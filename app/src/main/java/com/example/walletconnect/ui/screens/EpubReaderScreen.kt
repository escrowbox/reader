package com.example.walletconnect.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walletconnect.epub.EpubReaderViewModel
import com.example.walletconnect.epub.PaginationResult
import com.example.walletconnect.sensors.MotionDetector
import com.example.walletconnect.utils.TimerContractStore
import androidx.compose.runtime.collectAsState
import java.io.File
import kotlin.math.roundToInt

val ReaderTextStyle = TextStyle(
    fontSize = 18.sp,
    lineHeight = 28.sp,
    color = Color.Unspecified  // Позволяет стилям из AnnotatedString иметь приоритет
)

/**
 * Получает состояние движения из MotionDetector
 */
@Composable
private fun getMotionState(motionDetector: MotionDetector?): MotionDetector.MotionState? {
    return motionDetector?.let {
        val state by it.motionState.collectAsState()
        state
    }
}

/**
 * Основной экран чтения EPUB-файла.
 */
@Composable
fun EpubReaderScreen(
    epubFile: File,
    boxId: String,
    onBack: () -> Unit,
    viewModel: EpubReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.uiState
    val remainingSeconds = viewModel.remainingSeconds
    val textMeasurer = rememberTextMeasurer()

    var pageWidth by remember { mutableStateOf(0f) }
    var pageHeight by remember { mutableStateOf(0f) }
    var isPageSizeMeasured by remember { mutableStateOf(false) }

    // Проверяем, есть ли hand control в контракте
    val timerParams = remember(boxId) {
        TimerContractStore.getTimerParams(context, boxId)
    }
    val hasHandControl = timerParams?.handControl == true

    // Создаем и управляем MotionDetector, если включен hand control
    val motionDetector = remember(hasHandControl) {
        if (hasHandControl) MotionDetector(context) else null
    }
    
    // Получаем состояние движения, если детектор активен
    val motionState = getMotionState(motionDetector)

    // Управление жизненным циклом MotionDetector
    DisposableEffect(hasHandControl, motionDetector) {
        if (hasHandControl && motionDetector != null) {
            motionDetector.start()
        }
        onDispose {
            motionDetector?.stop()
        }
    }
    
    // Отслеживание жизненного цикла экрана для паузы таймера
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    viewModel.setScreenPaused(true)
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setScreenPaused(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Обновляем состояние движения в ViewModel для управления таймером
    LaunchedEffect(motionState) {
        viewModel.setMotionState(motionState)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context, boxId)
    }
    
    // Останавливаем таймер при закрытии экрана
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTimer(context)
        }
    }

    LaunchedEffect(isPageSizeMeasured) {
        if (isPageSizeMeasured && pageWidth > 0 && pageHeight > 0) {
            viewModel.loadEpubFile(
                context = context,
                uri = Uri.fromFile(epubFile),
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                textMeasurer = textMeasurer,
                textStyle = ReaderTextStyle
            )
        }
    }

    BackHandler {
        // Сохраняем текущую страницу перед выходом
        viewModel.goToHome(context)
        viewModel.stopTimer(context)
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF8E1))
    ) {
        if (!isPageSizeMeasured || uiState.isLoading) {
            LoadingScreen(onPageSizeChanged = { width, height ->
                pageWidth = width
                pageHeight = height
                isPageSizeMeasured = true
            })
        } else if (uiState.error != null) {
            ErrorScreen(error = uiState.error!!, onBack = onBack)
        } else if (uiState.paginationResult != null) {
            PageContent(
                paginationResult = uiState.paginationResult!!,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                onNextPage = { 
                    viewModel.onSwipeDetected()
                    viewModel.nextPage(context) 
                },
                onPreviousPage = { 
                    viewModel.onSwipeDetected()
                    viewModel.previousPage(context) 
                },
                onGoToPage = { viewModel.goToPage(context, it) },
                onCheckpointClick = { checkpointIndex ->
                    viewModel.onCheckpointFound(context, checkpointIndex, textMeasurer, ReaderTextStyle)
                },
                onBack = onBack,
                remainingSeconds = remainingSeconds,
                motionState = if (hasHandControl) motionState else null
            )
        }
    }
}

/**
 * Экран загрузки/измерения области для первой пагинации.
 * Измеряет размер с учётом padding и других элементов, как в PageContent.
 */
@Composable
fun LoadingScreen(onPageSizeChanged: (Float, Float) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)  // так же как в PageContent
                .onGloballyPositioned { coords ->
                    onPageSizeChanged(coords.size.width.toFloat(), coords.size.height.toFloat())
                },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        
        // Placeholder для progress bar (чтобы размер совпадал с PageContent)
Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)) {
    Spacer(modifier = Modifier.height(14.dp)) // Высота текста + прогресс-бара
        }
    }
}

/**
 * Экран отображения ошибки чтения файла.
 */
@Composable
fun ErrorScreen(error: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = error, color = Color.Red, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) {
                Text("Назад")
            }
        }
    }
}

/**
 * Извлекает подстроку из AnnotatedString с сохранением аннотаций и подсвечивает чекпоинты красным цветом
 */
private fun highlightCheckpoints(
    fullText: AnnotatedString,
    startIndex: Int,
    endIndex: Int
): AnnotatedString {
    return buildAnnotatedString {
        // Получаем все аннотации чекпоинтов в диапазоне страницы
        val annotations = fullText.getStringAnnotations(
            tag = "checkpoint",
            start = startIndex,
            end = endIndex
        )
        
        // Если нет чекпоинтов, просто возвращаем подстроку
        if (annotations.isEmpty()) {
            append(fullText.subSequence(startIndex, endIndex))
            return@buildAnnotatedString
        }
        
        // Сортируем аннотации по позиции
        val sortedAnnotations = annotations.sortedBy { it.start }
        
        var currentIndex = startIndex
        
        sortedAnnotations.forEach { annotation ->
            // Добавляем текст до чекпоинта (копируем все стили)
            if (annotation.start > currentIndex) {
                append(fullText.subSequence(currentIndex, annotation.start))
            }
            
            // Добавляем чекпоинт с красным цветом (перезаписываем цвет)
            withStyle(style = SpanStyle(color = Color.Black)) {
                // Извлекаем только текст чекпоинта без стилей
                val checkpointText = fullText.subSequence(annotation.start, annotation.end).text
                append(checkpointText)
            }
            
            // Сохраняем аннотацию для обработки кликов
            val annotationStart = length - (annotation.end - annotation.start)
            addStringAnnotation(
                tag = "checkpoint",
                annotation = annotation.item,
                start = annotationStart,
                end = length
            )
            
            currentIndex = annotation.end
        }
        
        // Добавляем оставшийся текст после последнего чекпоинта
        if (currentIndex < endIndex) {
            append(fullText.subSequence(currentIndex, endIndex))
        }
    }
}

/**
 * Контент страницы книги: текст, жесты перелистывания и навигационный слайдер.
 * Также обрабатывает клики по чекпоинтам (аннотация tag = "checkpoint").
 */
@Composable
fun PageContent(
    paginationResult: PaginationResult,
    currentPage: Int,
    totalPages: Int,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onCheckpointClick: (Int) -> Unit,
    onBack: () -> Unit,
    remainingSeconds: Long? = null,
    motionState: MotionDetector.MotionState? = null
) {
    var showSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }
    var dragHandled by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage) {
        sliderValue = currentPage.toFloat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showSlider = !showSlider })
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)  // уменьшен bottom
                    .pointerInput(Unit) {
                        var dragStart = Offset.Zero
                        detectHorizontalDragGestures(
                            onDragStart = { dragStart = it },
                            onDragEnd = { dragHandled = false }
                        ) { change, _ ->
                            if (!dragHandled) {
                                val totalDrag = change.position.x - dragStart.x
                                if (totalDrag > 100) {
                                    onPreviousPage()
                                    dragHandled = true
                                } else if (totalDrag < -100) {
                                    onNextPage()
                                    dragHandled = true
                                }
                            }
                            change.consume()
                        }
                    }
            ) {
                if (currentPage < paginationResult.pages.size) {
                    val pageSlice = paginationResult.pages[currentPage]
                    
                    // Подсвечиваем чекпоинты красным цветом
                    val annotatedText = remember(pageSlice.startIndex, pageSlice.endIndex, paginationResult.fullText) {
                        highlightCheckpoints(
                            fullText = paginationResult.fullText,
                            startIndex = pageSlice.startIndex,
                            endIndex = pageSlice.endIndex
                        )
                    }
                    
                    ClickableText(
                        text = annotatedText,
                        style = ReaderTextStyle,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { offset ->
                            val annotations = annotatedText.getStringAnnotations(
                                tag = "checkpoint",
                                start = offset,
                                end = offset
                            )
                            if (annotations.isNotEmpty()) {
                                // Клик по чекпоинту - помечаем его как найденный
                                val checkpointIndex = annotations.first().item.toIntOrNull()
                                if (checkpointIndex != null) {
                                    onCheckpointClick(checkpointIndex)
                                }
                            } else {
                                // Клик по обычному тексту - показываем/скрываем слайдер
                                showSlider = !showSlider
                            }
                        }
                    )
                }
            }

            // Progress bar
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)) {
                Text(
                    text = "${currentPage + 1}/$totalPages",
                    modifier = Modifier.align(Alignment.End),
                    style = TextStyle(fontSize = 10.sp, color = Color.Gray)
                )
                LinearProgressIndicator(
                    progress = if (totalPages > 0) (currentPage + 1).toFloat() / totalPages else 0f,
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.Gray,
                    trackColor = Color.LightGray.copy(alpha = 0.3f)
                )
            }
        }

        // Top bar
        AnimatedVisibility(
            visible = showSlider,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            showSlider = false
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                        Text(text = "Назад")
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Индикатор состояния движения (hand control)
                        motionState?.let { state ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = when (state) {
                                    MotionDetector.MotionState.STATIONARY -> Color(0xFF4CAF50)
                                    MotionDetector.MotionState.MOVING -> Color(0xFFFF9800)
                                }
                            ) {
                                Text(
                                    text = when (state) {
                                        MotionDetector.MotionState.STATIONARY -> "STATIONARY"
                                        MotionDetector.MotionState.MOVING -> "MOVING"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        // Таймер для timer контрактов
                        remainingSeconds?.let { seconds ->
                            if (seconds >= 0) {
                                val hours = seconds / 3600
                                val minutes = (seconds % 3600) / 60
                                val secs = seconds % 60
                                
                                Text(
                                    text = String.format("%02d:%02d:%02d", hours, minutes, secs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (seconds <= 0) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom slider
        AnimatedVisibility(
            visible = showSlider,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${sliderValue.roundToInt() + 1} / $totalPages",
                        modifier = Modifier.align(Alignment.End),
                        style = TextStyle(fontSize = 10.sp, color = Color.Gray)
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            onGoToPage(sliderValue.roundToInt())
                            showSlider = false
                        },
                        valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat()
                    )
                }
            }
        }
    }
}

