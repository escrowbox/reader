package com.example.walletconnect.epub

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletconnect.utils.CheckpointIndexStore
import com.example.walletconnect.utils.TimerContractStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ViewModel для управления состоянием читалки EPUB.
 * Отвечает за загрузку файла, подготовку текста, вставку чекпоинтов и пагинацию.
 */
class EpubReaderViewModel : ViewModel() {

    var uiState by mutableStateOf(EpubReaderUiState())
        private set

    private val textProcessor = TextProcessor()
    private val paginationEngine = ComposePaginationEngine()
    private lateinit var epubParser: EpubParser
    private var imageMap: Map<String, ByteArray> = emptyMap()

    private var cachedElements: List<TextProcessor.TextElement>? = null
    private var lastPageWidth: Float = 0f
    private var lastPageHeight: Float = 0f

    // Индексы чекпоинтов в общем тексте книги
    private var checkpointIndices: List<Int> = emptyList()
    private var foundCheckpointIndices: MutableSet<Int> = mutableSetOf()
    private var currentBoxId: String = ""
    private var checkpointLabel: String = " [I find checkpoint] "
    
    // Защита от множественных сохранений
    private var lastSavedPage: Int = -1
    
    // Таймер для timer контрактов
    private var timerJob: Job? = null
    var remainingSeconds by mutableStateOf<Long?>(null)
        private set
    
    // Для swipe control
    private var hasSwipeControl = false
    private var lastSwipeTime = System.currentTimeMillis()
    private var isTimerPaused = false
    
    // Для hand control
    private var hasHandControl = false
    private var isHandControlPaused = false
    
    // Для паузы при неактивности экрана
    private var isScreenPaused = false

    /**
     * Инициализирует парсер EPUB и загружает индексы чекпоинтов для бокса (если есть).
     */
    fun initialize(context: Context, boxId: String = "") {
        epubParser = EpubParser(context)
        currentBoxId = boxId
        if (boxId.isNotEmpty()) {
            checkpointIndices = CheckpointIndexStore.getIndices(context, boxId)
            foundCheckpointIndices = CheckpointIndexStore.getFoundIndices(context, boxId).toMutableSet()
            checkpointLabel = CheckpointIndexStore.getCheckpointLabel(context, boxId)
            
            // Инициализируем таймер для timer контрактов
            val timerParams = TimerContractStore.getTimerParams(context, boxId)
            if (timerParams != null) {
                remainingSeconds = TimerContractStore.getRemainingSeconds(context, boxId)
                hasSwipeControl = timerParams.swipeControl
                hasHandControl = timerParams.handControl
                lastSwipeTime = System.currentTimeMillis()
                isTimerPaused = false
                isHandControlPaused = false
                isScreenPaused = false
                startTimer(context, boxId)
            }
        }
    }
    
    /**
     * Запускает таймер обратного отсчета для timer контракта.
     */
    private fun startTimer(context: Context, boxId: String) {
        // Останавливаем предыдущий таймер, если он есть
        timerJob?.cancel()
        
        timerJob = viewModelScope.launch {
            while (remainingSeconds != null && remainingSeconds!! > 0) {
                delay(1000) // 1 секунда
                
                // Проверяем swipe control - если включен и прошло больше 300 секунд с последнего свайпа, паузируем
                if (hasSwipeControl) {
                    val timeSinceLastSwipe = (System.currentTimeMillis() - lastSwipeTime) / 1000
                    if (timeSinceLastSwipe > 300) {
                        isTimerPaused = true
                        // Продолжаем цикл, но не уменьшаем секунды
                        continue
                    } else {
                        isTimerPaused = false
                    }
                }
                
                // Уменьшаем секунды только если таймер не на паузе (ни от swipe, ни от hand control, ни от неактивности экрана)
                if (!isTimerPaused && !isHandControlPaused && !isScreenPaused && remainingSeconds != null && remainingSeconds!! > 0) {
                    val newSeconds = remainingSeconds!! - 1
                    remainingSeconds = newSeconds
                    TimerContractStore.saveRemainingSeconds(context, boxId, newSeconds)
                }
            }
            // Когда время истекло
            if (remainingSeconds != null && remainingSeconds!! == 0L) {
                TimerContractStore.saveRemainingSeconds(context, boxId, 0L)
            }
        }
    }
    
    /**
     * Уведомляет о свайпе страницы. Используется для swipe control.
     */
    fun onSwipeDetected() {
        if (hasSwipeControl) {
            lastSwipeTime = System.currentTimeMillis()
            isTimerPaused = false
        }
    }
    
    /**
     * Устанавливает состояние движения для hand control.
     * Если STATIONARY - паузим таймер, если MOVING - возобновляем.
     */
    fun setMotionState(motionState: com.example.walletconnect.sensors.MotionDetector.MotionState?) {
        if (hasHandControl) {
            isHandControlPaused = motionState == com.example.walletconnect.sensors.MotionDetector.MotionState.STATIONARY
        }
    }
    
    /**
     * Устанавливает состояние активности экрана.
     * Если экран неактивен (пауза/стоп) - паузим таймер.
     */
    fun setScreenPaused(paused: Boolean) {
        isScreenPaused = paused
    }
    
    /**
     * Останавливает таймер и сохраняет оставшееся время.
     */
    fun stopTimer(context: Context) {
        timerJob?.cancel()
        timerJob = null
        
        // Сохраняем оставшиеся секунды при остановке
        if (currentBoxId.isNotEmpty() && remainingSeconds != null) {
            TimerContractStore.saveRemainingSeconds(context, currentBoxId, remainingSeconds!!)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    /**
     * Загружает файл EPUB, преобразует его в элементы текста и выполняет первую пагинацию.
     */
    fun loadEpubFile(
        context: Context,
        uri: Uri,
        pageWidth: Float,
        pageHeight: Float,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)

            try {
                val parseResult = withContext(Dispatchers.IO) {
                    epubParser.parseEpubFile(uri)
                }

                imageMap = parseResult.images
                val elements = textProcessor.processFormattedElements(parseResult.elements)

                cachedElements = elements
                lastPageWidth = pageWidth
                lastPageHeight = pageHeight

                val paginationResult = paginationEngine.paginate(
                    elements = elements,
                    textMeasurer = textMeasurer,
                    textStyle = textStyle,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    checkpointIndices = checkpointIndices,
                    foundCheckpointIndices = foundCheckpointIndices.toSet(),
                    checkpointLabel = checkpointLabel
                )

                // Загружаем сохраненную позицию или начинаем с первой страницы
                val savedPage = if (currentBoxId.isNotEmpty()) {
                    val savedCharIndex = CheckpointIndexStore.getCharIndex(context, currentBoxId)
                    val page = if (savedCharIndex >= 0) {
                        // Находим страницу по индексу символа
                        val foundPage = paginationResult.pages.indexOfFirst { pageSlice ->
                            savedCharIndex >= pageSlice.startIndex && savedCharIndex < pageSlice.endIndex
                        }
                        if (foundPage >= 0) foundPage else 0
                    } else {
                        0
                    }
                    // Timber.d("📚 Загрузка книги для бокса $currentBoxId:")
                    // Timber.d("   - Сохранённый индекс символа: $savedCharIndex")
                    // Timber.d("   - Найденная страница: $page")
                    // Timber.d("   - Всего страниц: ${paginationResult.pages.size}")
                    lastSavedPage = page
                    // Timber.d("   - lastSavedPage установлен: $lastSavedPage")
                    page
                } else {
                    // Timber.d("📚 Загрузка книги без boxId, начинаем с первой страницы")
                    lastSavedPage = 0
                    0
                }

                uiState = uiState.copy(
                    isLoading = false,
                    paginationResult = paginationResult,
                    currentPage = savedPage,
                    totalPages = paginationResult.pages.size,
                    images = imageMap
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Ошибка загрузки файла: ${e.message}"
                )
            }
        }
    }

    fun repaginateIfNeeded(
        pageWidth: Float,
        pageHeight: Float,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        val elements = cachedElements ?: return
        
        val widthChanged = kotlin.math.abs(pageWidth - lastPageWidth) > 1f
        val heightChanged = kotlin.math.abs(pageHeight - lastPageHeight) > 1f
        
        if (!widthChanged && !heightChanged) return
        
        // Timber.d("🔄 Репагинация: размер изменился с ${lastPageWidth}x${lastPageHeight} на ${pageWidth}x${pageHeight}")
        // Timber.d("   Текущая страница ДО репагинации: ${uiState.currentPage}")
        
        lastPageWidth = pageWidth
        lastPageHeight = pageHeight
        
        val currentResult = uiState.paginationResult
        val currentCharIndex = if (currentResult != null && uiState.currentPage < currentResult.pages.size) {
            currentResult.pages[uiState.currentPage].startIndex
        } else 0
        
        // Timber.d("   Текущий индекс символа: $currentCharIndex")
        
        val paginationResult = paginationEngine.paginate(
            elements = elements,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            checkpointIndices = checkpointIndices,
            foundCheckpointIndices = foundCheckpointIndices.toSet(),
            checkpointLabel = checkpointLabel
        )
        
        val newCurrentPage = paginationResult.pages.indexOfFirst { page ->
            currentCharIndex >= page.startIndex && currentCharIndex < page.endIndex
        }.coerceAtLeast(0)
        
        // Timber.d("   Новая страница ПОСЛЕ репагинации: $newCurrentPage из ${paginationResult.pages.size}")
        // Timber.d("   ⚠️ НЕ сохраняем страницу при репагинации!")
        
        uiState = uiState.copy(
            paginationResult = paginationResult,
            currentPage = newCurrentPage,
            totalPages = paginationResult.pages.size
        )
        
        // НЕ сохраняем страницу при репагинации, так как это техническая операция
        // НЕ обновляем lastSavedPage!
    }

    private fun saveCurrentPageIfChanged(context: Context, newPage: Int, source: String) {
        // Timber.d("🔵 saveCurrentPageIfChanged вызвана: newPage=$newPage, lastSavedPage=$lastSavedPage, source=$source, boxId=$currentBoxId")
        if (currentBoxId.isNotEmpty() && newPage != lastSavedPage) {
            // Сохраняем индекс символа вместо номера страницы
            val paginationResult = uiState.paginationResult
            if (paginationResult != null && newPage < paginationResult.pages.size) {
                val charIndex = paginationResult.pages[newPage].startIndex
                // Timber.d("💾 Сохраняем позицию для бокса $currentBoxId ($source):")
                // Timber.d("   - Страница: $newPage (предыдущая: $lastSavedPage)")
                // Timber.d("   - Индекс символа: $charIndex")
                CheckpointIndexStore.saveCharIndex(context, currentBoxId, charIndex)
                lastSavedPage = newPage
                // Timber.d("✅ lastSavedPage обновлён на: $lastSavedPage")
            } else {
                Timber.w("⚠️ Не можем сохранить: paginationResult=$paginationResult, newPage=$newPage")
            }
        }
        // Остальные логи отключены для производительности
    }

    fun nextPage(context: Context) {
        // Timber.d("➡️ nextPage вызвана: текущая=${uiState.currentPage}, всего=${uiState.totalPages}")
        if (uiState.currentPage < uiState.totalPages - 1) {
            val newPage = uiState.currentPage + 1
            // Timber.d("   Переходим на страницу: $newPage")
            uiState = uiState.copy(currentPage = newPage)
            saveCurrentPageIfChanged(context, newPage, "nextPage")
        }
        // Логи отключены для производительности
    }

    fun previousPage(context: Context) {
        // Timber.d("⬅️ previousPage вызвана: текущая=${uiState.currentPage}, всего=${uiState.totalPages}")
        if (uiState.currentPage > 0) {
            val newPage = uiState.currentPage - 1
            // Timber.d("   Переходим на страницу: $newPage")
            uiState = uiState.copy(currentPage = newPage)
            saveCurrentPageIfChanged(context, newPage, "previousPage")
        }
        // Логи отключены для производительности
    }

    fun goToPage(context: Context, pageIndex: Int) {
        // Timber.d("🎯 goToPage вызвана: pageIndex=$pageIndex, текущая=${uiState.currentPage}, всего=${uiState.totalPages}")
        val validIndex = pageIndex.coerceIn(0, (uiState.totalPages - 1).coerceAtLeast(0))
        // Timber.d("   Валидный индекс: $validIndex")
        uiState = uiState.copy(currentPage = validIndex)
        saveCurrentPageIfChanged(context, validIndex, "goToPage")
    }

    /**
     * Обрабатывает клик на чекпоинт: помечает его как найденный и обновляет UI.
     */
    fun onCheckpointFound(
        context: Context,
        checkpointIndex: Int,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle
    ) {
        if (currentBoxId.isEmpty() || checkpointIndex !in checkpointIndices) return
        if (checkpointIndex in foundCheckpointIndices) return // Уже найден

        // Timber.d("Найден чекпоинт с индексом $checkpointIndex для бокса $currentBoxId")

        // Помечаем как найденный
        foundCheckpointIndices.add(checkpointIndex)
        CheckpointIndexStore.markIndexAsFound(context, currentBoxId, checkpointIndex)

        // Пересобираем текст с обновленным состоянием чекпоинтов
        val elements = cachedElements ?: return

        val currentResult = uiState.paginationResult
        val currentCharIndex = if (currentResult != null && uiState.currentPage < currentResult.pages.size) {
            currentResult.pages[uiState.currentPage].startIndex
        } else 0

        val paginationResult = paginationEngine.paginate(
            elements = elements,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            pageWidth = lastPageWidth,
            pageHeight = lastPageHeight,
            checkpointIndices = checkpointIndices,
            foundCheckpointIndices = foundCheckpointIndices.toSet(),
            checkpointLabel = checkpointLabel
        )

        val newCurrentPage = paginationResult.pages.indexOfFirst { page ->
            currentCharIndex >= page.startIndex && currentCharIndex < page.endIndex
        }.coerceAtLeast(0)

        uiState = uiState.copy(
            paginationResult = paginationResult,
            currentPage = newCurrentPage,
            totalPages = paginationResult.pages.size
        )
        
        // НЕ сохраняем страницу при клике на чекпоинт, так как это техническая операция
    }

    fun goToHome(context: Context) {
        // Сохраняем текущую страницу перед выходом
        saveCurrentPageIfChanged(context, uiState.currentPage, "goToHome")
        cachedElements = null
        imageMap = emptyMap()
        foundCheckpointIndices.clear()
        currentBoxId = ""
        lastSavedPage = -1
        uiState = EpubReaderUiState()
    }
}

data class EpubReaderUiState(
    val isLoading: Boolean = false,
    val paginationResult: PaginationResult? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val error: String? = null,
    val images: Map<String, ByteArray> = emptyMap()
)

