package com.example.walletconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletconnect.SolanaManager
import com.example.walletconnect.ui.theme.NeumorphicBackground
import com.example.walletconnect.ui.theme.NeumorphicText
import com.example.walletconnect.ui.theme.NeumorphicTextSecondary
import com.example.walletconnect.ui.theme.TirtoWritterFontFamily
import com.example.walletconnect.utils.CheckpointIndexStore
import com.example.walletconnect.utils.CheckpointContractStore
import com.example.walletconnect.utils.TimerContractStore
import com.example.walletconnect.utils.FileManager
import com.example.walletconnect.utils.BoxMetadataStore
import com.example.walletconnect.utils.VaultManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream
import java.io.FileInputStream
import org.jsoup.Jsoup
import org.json.JSONObject
import timber.log.Timber

/**
 * Экран для отображения списка боксов из Solana блокчейна
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    onBack: () -> Unit,
    onReadBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // ОПТИМИЗАЦИЯ: Используем observeAsState напрямую - это уже оптимизировано Compose
    val createdEvents by manager.boxCreatedEvents.observeAsState(emptyList())
    val openedEvents by manager.boxOpenedEvents.observeAsState(emptyList())
    val pendingContracts by manager.pendingContracts.observeAsState(emptyList())
    val isConnected = manager.isConnected.observeAsState(false).value
    val errorMessage by manager.errorMessage.observeAsState("")
    val transactionStatus by manager.transactionStatus.observeAsState("")
    
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Показываем ошибки через Snackbar
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Long
            )
        }
    }
    
    // Создаем стабильный Set для быстрой проверки isOpened
    val openedEventIds = remember(openedEvents) {
        openedEvents.map { it.id }.toSet()
    }
    
    // КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Убираем предзагрузку полностью!
    // Проблема в том, что remember(createdEvents) вызывается каждый раз при любом изменении списка
    // Вместо этого полагаемся на remember(event.id) внутри каждого элемента - 
    // Compose умный и не будет пересоздавать элементы при скролле
    
    // Получаем текущее время и обновляем его каждую секунду для обратного отсчета
    var currentTimeSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    
    // Состояние загрузки для первичной загрузки событий
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Флаг для отслеживания первой загрузки
    var hasLoadedInitially by remember { mutableStateOf(false) }

    // Загружаем события из блокчейна ТОЛЬКО один раз при первом открытии экрана
    LaunchedEffect(isConnected) {
        if (!hasLoadedInitially && isConnected) {
            isLoading = true
            // Ждем реального ответа от сервера
            manager.fetchBoxCreatedEventsAsync()
            isLoading = false
            hasLoadedInitially = true
        } else if (!hasLoadedInitially && !isConnected) {
            hasLoadedInitially = true
        }
    }
    
    // Автоматически проверяем pending контракты при появлении экрана
    // Это нужно, если пользователь закрыл приложение с pending контрактом и вернулся позже
    LaunchedEffect(Unit) {
        if (pendingContracts.isNotEmpty() && isConnected) {
            delay(2000) // Даем время на загрузку базовых данных
            manager.fetchBoxCreatedEventsAsync()
        }
    }
    
    // Отслеживаем изменения в pending контрактах
    // Когда pending контракт исчезает (подтверждается), автоматически обновляем список событий
    val previousPendingCount = remember { mutableStateOf(pendingContracts.size) }
    LaunchedEffect(pendingContracts.size) {
        // Если список pending контрактов уменьшился (контракт подтвердился)
        if (pendingContracts.size < previousPendingCount.value && isConnected) {
            Timber.d("📊 Pending контракт подтвержден, обновляем список событий")
            // Небольшая задержка для уверенности что транзакция полностью обработана
            delay(1000)
            manager.fetchBoxCreatedEventsAsync()
        }
        previousPendingCount.value = pendingContracts.size
    }
    
    // Запускаем таймер для обновления времени каждую секунду (обратный отсчет deadline)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeSeconds = System.currentTimeMillis() / 1000
        }
    }

    // События уже отсортированы по slot (дате создания) в SolanaManager
    // Используем стабильную ссылку на список
    val sortedCreatedEvents = remember(createdEvents) {
        createdEvents
    }
    
    // Получаем openingBoxId один раз на уровне экрана
    val openingBoxId by manager.openingBoxId.observeAsState(null)
    
    // Состояние для выбранного таба
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    // Функция для определения статуса события
    fun getEventStatus(event: SolanaManager.BoxCreatedEvent, isOpened: Boolean, currentTime: Long): String {
        val savedStatus = BoxMetadataStore.getStatus(context, event.id)
        val isExpired = event.deadline.toLong() < currentTime && event.deadline.toLong() > 0
        
        return when {
            event.deadline.toLong() == 0L && event.amount == BigInteger.ZERO -> {
                when(savedStatus) {
                    BoxMetadataStore.BoxStatus.WIN -> "win"
                    BoxMetadataStore.BoxStatus.LOSE -> "lose"
                    else -> "win"
                }
            }
            savedStatus == BoxMetadataStore.BoxStatus.WIN -> "win"
            savedStatus == BoxMetadataStore.BoxStatus.LOSE -> "lose"
            isOpened -> "win"
            isExpired -> "lose"
            else -> "active"
        }
    }
    
    // Функция для проверки наличия ключа
    fun hasPrivateKey(eventId: String): Boolean {
        return VaultManager.getPrivateKey(context, eventId) != null
    }
    
    // Фильтруем события по выбранному табу
    // События без ключа показываются ТОЛЬКО в табе "no key"
    val filteredEvents = remember(sortedCreatedEvents, selectedTabIndex, currentTimeSeconds, openedEventIds) {
        val eventsWithStatus = sortedCreatedEvents.map { event ->
            val isOpened = openedEventIds.contains(event.id)
            val status = getEventStatus(event, isOpened, currentTimeSeconds)
            val hasKey = hasPrivateKey(event.id)
            Triple(event, status, hasKey)
        }
        
        when (selectedTabIndex) {
            0 -> eventsWithStatus.filter { it.second == "active" && it.third }.map { it.first } // active (только с ключом)
            1 -> eventsWithStatus.filter { it.second == "win" && it.third }.map { it.first } // win (только с ключом)
            2 -> eventsWithStatus.filter { it.second == "lose" && it.third }.map { it.first } // lose (только с ключом)
            3 -> eventsWithStatus.filter { !it.third }.map { it.first } // no key (все без ключа)
            else -> sortedCreatedEvents
        }
    }
    
    // Определяем, есть ли события без ключа для показа таба "no key"
    val hasNoKeyEvents = remember(sortedCreatedEvents) {
        sortedCreatedEvents.any { event ->
            VaultManager.getPrivateKey(context, event.id) == null
        }
    }
    
    // Формируем список табов
    val tabs = remember(hasNoKeyEvents) {
        val baseTabs = listOf("active", "win", "lose")
        if (hasNoKeyEvents) {
            baseTabs + "no key"
        } else {
            baseTabs
        }
    }
    
    // Сбрасываем selectedTabIndex если выбранный таб больше не существует
    LaunchedEffect(tabs.size) {
        if (selectedTabIndex >= tabs.size) {
            selectedTabIndex = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeumorphicBackground)
    ) {
        Scaffold(
            containerColor = NeumorphicBackground,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                            spotColor = Color.White.copy(alpha = 0.5f)
                        ),
                    color = NeumorphicBackground,
                    shadowElevation = 0.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                            }
                            
                            // Текст "contracts" посередине
                            Text(
                                text = "contracts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TirtoWritterFontFamily,
                                color = NeumorphicText,
                                modifier = Modifier.weight(1f)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                            
                            IconButton(onClick = { 
                                if (isConnected) {
                                    isLoading = true
                                    manager.fetchBoxCreatedEvents()
                                    scope.launch {
                                        delay(2000)
                                        isLoading = false
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                            }
                        }
                        
                        // Табы для фильтрации событий
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Text(
                                    text = title,
                                    modifier = Modifier
                                        .clickable { selectedTabIndex = index }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    fontFamily = TirtoWritterFontFamily,
                                    color = if (selectedTabIndex == index) NeumorphicText else NeumorphicTextSecondary
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
        // Показываем прелоадер во время первичной загрузки
        // Но если есть pending контракты в active табе — показываем LazyColumn, чтобы pending карточка была видна
        if (isLoading && isConnected && (selectedTabIndex != 0 || pendingContracts.isEmpty())) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeumorphicText)
            }
        } else if (hasLoadedInitially && filteredEvents.isEmpty() && (selectedTabIndex != 0 || pendingContracts.isEmpty())) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "No events found from the blockchain",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NeumorphicTextSecondary
                    )
                    Text(
                        text = "Create a contract to see events",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeumorphicTextSecondary.copy(alpha = 0.7f)
                    )
                    
                    // RPC ответ отключен для производительности
                    // if ("".isNotEmpty()) {
                    if (false) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "📡 RPC Response Info",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Парсим и показываем структурированную информацию
                                val rpcInfo = parseRpcResponseInfo("")
                                
                                if (rpcInfo != null) {
                                    EventRowReadable("Method", rpcInfo.method)
                                    EventRowReadable("Accounts Found", rpcInfo.accountsCount.toString())
                                    if (rpcInfo.error != null) {
                                        EventRowReadable("Error", rpcInfo.error)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Full JSON Response:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState()),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Показываем pending контракты в табе "active"
                if (selectedTabIndex == 0 && pendingContracts.isNotEmpty()) {
                    items(
                        items = pendingContracts,
                        key = { "pending_${it.id}" },
                        contentType = { "pending_contract" }
                    ) { pending ->
                        PendingContractCard(
                            pending = pending,
                            onReadBook = onReadBook
                        )
                    }
                }
                
                // Показываем события из блокчейна с полной информацией (отфильтрованные по табу)
                items(
                    items = filteredEvents,
                    key = { it.id },
                    contentType = { "box_event" }  // Указываем тип контента для оптимизации LazyColumn
                ) { event ->
                    // ОПТИМИЗАЦИЯ: Используем предвычисленный Set для O(1) проверки
                    val isOpened = openedEventIds.contains(event.id)
                    
                    EventItemCreated(
                        event = event,
                        manager = manager,
                        activityResultSender = activityResultSender,
                        isOpened = isOpened,
                        onReadBook = onReadBook,
                        openingBoxId = openingBoxId,
                        currentTimeSeconds = currentTimeSeconds
                    )
                }
                
                // RPC ответ отключен для производительности
                // if ("".isNotEmpty()) {
                if (false) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "📡 RPC Response Info",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Парсим и показываем структурированную информацию
                                val rpcInfo = parseRpcResponseInfo("")
                                
                                if (rpcInfo != null) {
                                    EventRowReadable("Method", rpcInfo.method)
                                    EventRowReadable("Accounts Found", rpcInfo.accountsCount.toString())
                                    if (rpcInfo.error != null) {
                                        EventRowReadable("Error", rpcInfo.error)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Full JSON Response:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .verticalScroll(rememberScrollState()),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * Элемент списка для события BoxCreated
 * ОПТИМИЗАЦИЯ: Все данные кешируются с помощью remember(event.id)
 * Compose не пересоздает элементы при скролле, поэтому remember работает эффективно
 */
@Composable
fun EventItemCreated(
    event: SolanaManager.BoxCreatedEvent,
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    isOpened: Boolean,
    onReadBook: (String) -> Unit,
    openingBoxId: String?,
    currentTimeSeconds: Long  // Статическое время для всего списка
) {
    val context = LocalContext.current
    
    // ОПТИМИЗАЦИЯ: Используем простую проверку вместо remember
    val isOpening = openingBoxId == event.id
    
    // КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Используем derivedStateOf для минимизации recompositions
    // Данные загружаются ТОЛЬКО ОДИН РАЗ при создании элемента (благодаря remember(event.id))
    // и НЕ перезагружаются при скролле
    data class CachedEventData(
        val hasBookFile: Boolean,
        val bookTitle: String,
        val checkpointIndices: List<Int>,
        val foundCheckpointIndices: Set<Int>,
        val checkpointLabel: String,
        val timerParams: TimerContractStore.TimerParams?,
        val remainingSeconds: Long,
        val hasPrivateKey: Boolean,
        val savedAmount: BigInteger?,
        val tokenDecimals: Int?,
        val tokenSymbol: String?
    )
    
    // Кешируем ВСЕ данные один раз при создании элемента
    val cachedData = remember(event.id) {
        val epubFile = FileManager.getEpubFile(context, event.id)
        val timerParams = TimerContractStore.getTimerParams(context, event.id)
        
        // ПРОВЕРКА: Есть ли закрытый ключ для этого события в VaultManager
        val hasPrivateKey = VaultManager.getPrivateKey(context, event.id) != null
        
        // Получаем сохраненную сумму депозита или сохраняем текущую, если её еще нет
        val savedAmount = BoxMetadataStore.getAmount(context, event.id)
        val amountToSave = if (savedAmount == null && event.amount != BigInteger.ZERO) {
            // Если суммы еще нет и текущая не 0, сохраняем её
            BoxMetadataStore.setAmount(context, event.id, event.amount)
            event.amount
        } else {
            savedAmount
        }
        
        // Получаем информацию о токене (decimals и symbol)
        val tokenDecimals = BoxMetadataStore.getDecimals(context, event.id)
        val tokenSymbol = BoxMetadataStore.getSymbol(context, event.id)
        
        // ЛОГИРОВАНИЕ: Проверяем, что получили из хранилища
        Timber.d("📊 EventItem для boxId=${event.id}: tokenDecimals=$tokenDecimals, tokenSymbol=$tokenSymbol")
        
        CachedEventData(
            hasBookFile = epubFile != null,
            bookTitle = epubFile?.let { extractBookTitleFromFile(it) } ?: "Box",
            checkpointIndices = CheckpointIndexStore.getIndices(context, event.id),
            foundCheckpointIndices = CheckpointIndexStore.getFoundIndices(context, event.id).toSet(),
            checkpointLabel = CheckpointIndexStore.getCheckpointLabel(context, event.id),
            timerParams = timerParams,
            remainingSeconds = timerParams?.let { 
                TimerContractStore.getRemainingSeconds(context, event.id) 
            } ?: 0L,
            hasPrivateKey = hasPrivateKey,
            savedAmount = amountToSave,
            tokenDecimals = tokenDecimals,
            tokenSymbol = tokenSymbol
        )
    }
    
    val hasBookFile = cachedData.hasBookFile
    val bookTitle = cachedData.bookTitle
    val checkpointIndices = cachedData.checkpointIndices
    val foundCheckpointIndices = cachedData.foundCheckpointIndices
    val checkpointLabel = cachedData.checkpointLabel
    val timerParams = cachedData.timerParams
    val remainingSeconds = cachedData.remainingSeconds

    // Состояние для показа диалога с полным текстом checkpoint
    var showCheckpointTextDialog by remember { mutableStateOf(false) }
    
    // Локальное состояние для предотвращения двойных кликов
    var isLocallyProcessing by remember { mutableStateOf(false) }
    
    // Сбрасываем локальное состояние если бокс открылся или операция завершилась
    LaunchedEffect(isOpened, openingBoxId) {
        if (isOpened) {
            // Бокс успешно открыт
            isLocallyProcessing = false
        } else if (isLocallyProcessing && openingBoxId != event.id) {
            // Операция завершилась (успешно или с ошибкой) для другого бокса или отменилась
            isLocallyProcessing = false
        }
    }
    
    // Проверяем, нужно ли показывать текст как кликабельный (если длиннее 20 символов)
    val isCheckpointTextLong = checkpointLabel.length > 20
    // Обрезаем текст до 20 символов с многоточием, если он длинный
    val displayCheckpointText = if (isCheckpointTextLong) {
        checkpointLabel.take(20) + "..."
    } else {
        checkpointLabel
    }

    // Получаем сохраненный статус бокса из метаданных
    val savedStatus = remember(event.id) {
        BoxMetadataStore.getStatus(context, event.id)
    }
    
    // Определяем статус с учетом текущего времени (обновляется каждую секунду)
    val isExpired = remember(event.deadline, currentTimeSeconds) {
        event.deadline.toLong() < currentTimeSeconds && event.deadline.toLong() > 0
    }
    
    // Определяем статус бокса с приоритетом на сохраненные данные
    val status = when {
        // Если бокс закрыт в блокчейне (deadline=0, amount=0)
        event.deadline.toLong() == 0L && event.amount == BigInteger.ZERO -> {
            // Используем сохраненный статус
            when(savedStatus) {
                BoxMetadataStore.BoxStatus.WIN -> "win"
                BoxMetadataStore.BoxStatus.LOSE -> "lose"
                else -> "win" // По умолчанию закрытый бокс = успешно открыт
            }
        }
        // Используем сохраненный статус если он есть и не ACTIVE
        savedStatus == BoxMetadataStore.BoxStatus.WIN -> "win"
        savedStatus == BoxMetadataStore.BoxStatus.LOSE -> "lose"
        // Проверяем открыт ли бокс через список открытых событий
        isOpened -> "win"
        // Проверяем просрочен ли бокс
        isExpired -> "lose"
        // Иначе бокс активен
        else -> "active"
    }

    val (cardColor, labelEmoji) = when (status) {
        "win" -> Color(0xFFE8F5E9) to "🏆"   // Зеленый
        "lose" -> Color(0xFFFFEBEE) to "💀"  // Красный
        else -> MaterialTheme.colorScheme.surface to "📦" // Стандарт
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                spotColor = Color.White.copy(alpha = 0.5f)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = NeumorphicBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when(status) {
                            "win" -> Color(0xFF2E7D32)
                            "lose" -> Color(0xFFC62828)
                            else -> NeumorphicText
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Статус на отдельной строке
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = when(status) {
                        "win" -> Color(0xFF2E7D32)
                        "lose" -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.secondary
                    }
                ) {
                    Text(
                        text = status.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                // Показываем предупреждение если ключ отсутствует
                if (!cachedData.hasPrivateKey) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = Color(0xFFFF9800).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Ключ отсутствует",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp, 
                color = when(status) {
                    "win" -> Color(0xFFC8E6C9)
                    "lose" -> Color(0xFFFFCDD2)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            )

            // Используем сохраненную сумму, если текущая равна 0 (бокс закрыт)
            val displayAmount = if (event.amount == BigInteger.ZERO && cachedData.savedAmount != null) {
                cachedData.savedAmount!!
            } else {
                event.amount
            }
            // Используем decimals и symbol токена, если они есть, иначе SOL
            val decimals = cachedData.tokenDecimals ?: 9  // По умолчанию 9 для SOL
            val symbol = cachedData.tokenSymbol ?: "SOL"  // По умолчанию SOL
            
            // ЛОГИРОВАНИЕ: Проверяем, какие значения используются для отображения
            Timber.d("💰 Отображение депозита для boxId=${event.id}: decimals=$decimals, symbol=$symbol, amount=$displayAmount")
            
            val formattedAmount = formatUnits(displayAmount, decimals)
            EventRow("Deposite", "$formattedAmount $symbol")

            // Показываем Deadline только для активных боксов (не для win/lose)
            if (status == "active") {
                // Форматируем оставшееся время до дедлайна с живым обновлением
                val remainingTime = remember(event.deadline, currentTimeSeconds) {
                    val remainingSecs = event.deadline.toLong() - currentTimeSeconds
                    if (remainingSecs <= 0) {
                        "EXPIRED"
                    } else {
                        formatRemainingTime(remainingSecs)
                    }
                }
                EventRow("Deadline", remainingTime)
            }

            // Строка с чекпоинтами (только для checkpoints контрактов, не для timer)
            if (timerParams == null) {
                EventRowWithCheckpoints("Checkpoints", checkpointIndices, foundCheckpointIndices)
                // Строка с checkpoint text - кликабельная, если текст длинный
                if (isCheckpointTextLong) {
                    EventRowClickable("Checkpoint text", displayCheckpointText) {
                        showCheckpointTextDialog = true
                    }
                } else {
                    EventRow("Checkpoint text", checkpointLabel)
                }
            }
            
            // Отображаем параметры timer контракта, если они есть
            if (timerParams != null) {
                // ОПТИМИЗАЦИЯ: Используем кешированное значение вместо вызова хранилища
                // Защита от NaN и отрицательных значений
                val safeSeconds = remainingSeconds.coerceAtLeast(0L)
                val hours = safeSeconds / 3600
                val minutes = (safeSeconds % 3600) / 60
                val secs = safeSeconds % 60
                val hoursFormatted = String.format("%02d:%02d:%02d", hours, minutes, secs)
                
                EventRow("Time", hoursFormatted)
                
                EventRow("Swipe Control", if (timerParams.swipeControl) "✓" else "✗")
                EventRow("Hand Control", if (timerParams.handControl) "✓" else "✗")
            }

            // Кнопка открытия бокса
            val allCheckpointsFound = checkpointIndices.isNotEmpty() && 
                checkpointIndices.size == 3 && 
                foundCheckpointIndices.size == checkpointIndices.size
            
            // Для checkpoints контрактов - все чекпоинты должны быть найдены
            // ОПТИМИЗАЦИЯ: Используем кешированное значение
            // Для timer контрактов - таймер должен обнулиться
            val remainingSecondsForTimer = timerParams?.let { remainingSeconds }
            val isTimerReady = timerParams != null && remainingSecondsForTimer == 0L
            
            val canOpenBox = if (timerParams != null) {
                // Для timer контрактов проверяем обнуление таймера
                isTimerReady
            } else {
                // Для checkpoints контрактов проверяем найденные чекпоинты
                allCheckpointsFound
            }
            
            // Комбинированное состояние загрузки: локальное ИЛИ глобальное
            val isTrulyProcessing = isLocallyProcessing || isOpening
            
            // Определяем, токеновый ли контракт
            val mintAddress = remember(event.id) {
                BoxMetadataStore.getMint(context, event.id)
            }
            val isTokenContract = mintAddress != null
            
            // Показываем кнопку только если бокс активен и все условия выполнены
            if (status == "active" && canOpenBox) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Если ключ есть - показываем обычную кнопку
                if (cachedData.hasPrivateKey) {
                    Button(
                        onClick = { 
                            if (!isLocallyProcessing) {
                                isLocallyProcessing = true
                                Timber.d("🔘 Return deposit нажата: boxId=${event.id}, isTokenContract=$isTokenContract, mintAddress=$mintAddress")
                                if (isTokenContract) {
                                    Timber.d("📤 Вызов openBoxToken для boxId=${event.id}")
                                    manager.openBoxToken(context, event.id, activityResultSender)
                                } else {
                                    Timber.d("📤 Вызов openBox для boxId=${event.id}")
                                    manager.openBox(context, event.id, activityResultSender)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                                spotColor = Color.White.copy(alpha = 0.5f)
                            ),
                        enabled = !isTrulyProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeumorphicBackground,
                            contentColor = NeumorphicText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTrulyProcessing) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NeumorphicText
                                )
                            }
                        } else {
                            Text("Return deposit")
                        }
                    }
                } else {
                    // Если ключа нет - показываем заблокированную кнопку
                    OutlinedButton(
                        onClick = { /* Ключ отсутствует, ничего не делаем */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.1f),
                                spotColor = Color.White.copy(alpha = 0.2f)
                            ),
                        enabled = false,
                        colors = ButtonDefaults.outlinedButtonColors(
                            disabledContentColor = NeumorphicTextSecondary,
                            disabledContainerColor = NeumorphicBackground
                        ),
                        border = BorderStroke(1.dp, NeumorphicTextSecondary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Return deposit (ключ отсутствует)",
                                color = NeumorphicTextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Кнопка чтения книги
            if (hasBookFile) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onReadBook(event.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                            spotColor = Color.White.copy(alpha = 0.4f)
                        ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeumorphicText
                    ),
                    border = BorderStroke(1.dp, NeumorphicTextSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Read")
                    }
                }
            }
        }
    }
    
    // Диалог для отображения полного текста checkpoint
    if (showCheckpointTextDialog) {
        Dialog(onDismissRequest = { showCheckpointTextDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight()
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.5f),
                        spotColor = Color.White.copy(alpha = 0.7f)
                    ),
                color = NeumorphicBackground,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Checkpoint text",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicText
                    )
                    Text(
                        text = checkpointLabel,
                        fontSize = 16.sp,
                        color = NeumorphicText
                    )
                    Button(
                        onClick = { showCheckpointTextDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                                spotColor = Color.White.copy(alpha = 0.6f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeumorphicBackground,
                            contentColor = NeumorphicText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Закрыть",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventRowReadable(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Black,
            modifier = Modifier.weight(2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun EventRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NeumorphicTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = NeumorphicText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EventRowClickable(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NeumorphicTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick),
            color = NeumorphicText
        )
    }
}

@Composable
fun EventRowWithCheckpoints(
    label: String,
    checkpointIndices: List<Int>,
    foundCheckpointIndices: Set<Int>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NeumorphicTextSecondary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            checkpointIndices.forEach { index ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (index in foundCheckpointIndices) {
                                Color(0xFF4CAF50) // Зеленый для найденных
                            } else {
                                Color.Gray.copy(alpha = 0.5f) // Серый для ненайденных
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

private fun formatUnits(value: BigInteger, decimals: Int): String {
    return try {
        // Защита от очень больших значений и NaN
        if (value.signum() == 0) {
            "0"
        } else {
            val bd = BigDecimal(value).movePointLeft(decimals)
            // Используем количество знаков после запятой соответствующее decimals токена
            // Не используем stripTrailingZeros() чтобы не потерять важные нули для малых значений
            val result = bd.setScale(decimals, RoundingMode.DOWN).toPlainString()
            // Проверка на NaN и Infinity
            if (result == "NaN" || result.contains("Infinity")) {
                "0"
            } else {
                result
            }
        }
    } catch (e: Exception) {
        "0"
    }
}

private fun formatDate(timestamp: BigInteger): String {
    return try {
        val date = Date(timestamp.toLong() * 1000L)
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(date)
    } catch (e: Exception) {
        timestamp.toString()
    }
}

/**
 * Форматирует оставшееся время в формат "дни:часы:минуты:секунды"
 */
private fun formatRemainingTime(seconds: Long): String {
    if (seconds <= 0) {
        return "EXPIRED"
    }
    
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return String.format("%d:%02d:%02d:%02d", days, hours, minutes, secs)
}

/**
 * Парсит информацию из RPC ответа для читаемого отображения
 */
private fun parseRpcResponseInfo(jsonString: String): RpcResponseInfo? {
    return try {
        val json = JSONObject(jsonString)
        val method = json.optString("method", "unknown")
        val result = json.optJSONObject("result")
        val error = json.optJSONObject("error")
        
        val accountsCount = if (result != null) {
            val value = result.optJSONArray("value")
            value?.length() ?: 0
        } else {
            0
        }
        
        val errorMessage = error?.optString("message")
        
        RpcResponseInfo(
            method = method,
            accountsCount = accountsCount,
            error = errorMessage
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Информация о RPC ответе
 */
private data class RpcResponseInfo(
    val method: String,
    val accountsCount: Int,
    val error: String?
)

/**
 * Извлекает название книги из EPUB файла
 */
private fun extractBookTitleFromFile(file: java.io.File): String {
    return try {
        FileInputStream(file).use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.contains("content.opf", ignoreCase = true) || 
                        entry.name.contains("metadata.opf", ignoreCase = true) ||
                        entry.name.endsWith(".opf", ignoreCase = true)) {
                        val content = zip.bufferedReader().readText()
                        val doc = Jsoup.parse(content)
                        val title = doc.select("dc|title, title").first()?.text()?.trim()
                        if (!title.isNullOrBlank()) {
                            return title
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        "EPUB файл"
    } catch (e: Exception) {
        "EPUB файл"
    }
}

/**
 * Карточка pending контракта с прелоадером
 * Отображает все те же данные, что и активная карточка
 */
@Composable
fun PendingContractCard(
    pending: SolanaManager.PendingContract,
    onReadBook: (String) -> Unit
) {
    val context = LocalContext.current
    
    // Кешируем все данные один раз при создании элемента (аналогично EventItemCreated)
    data class CachedPendingData(
        val hasBookFile: Boolean,
        val bookTitle: String,
        val tokenDecimals: Int,
        val tokenSymbol: String,
        val checkpointIndices: List<Int>,
        val foundCheckpointIndices: Set<Int>,
        val checkpointLabel: String,
        val timerParams: TimerContractStore.TimerParams?,
        val remainingSeconds: Long
    )
    
    val cachedData = remember(pending.id) {
        val epubFile = FileManager.getEpubFile(context, pending.id)
        val timerParams = TimerContractStore.getTimerParams(context, pending.id)
        
        CachedPendingData(
            hasBookFile = epubFile != null,
            bookTitle = epubFile?.let { extractBookTitleFromFile(it) } ?: "Box",
            tokenDecimals = BoxMetadataStore.getDecimals(context, pending.id) ?: 9,
            tokenSymbol = BoxMetadataStore.getSymbol(context, pending.id) ?: "SOL",
            checkpointIndices = CheckpointIndexStore.getIndices(context, pending.id),
            foundCheckpointIndices = CheckpointIndexStore.getFoundIndices(context, pending.id).toSet(),
            checkpointLabel = CheckpointIndexStore.getCheckpointLabel(context, pending.id),
            timerParams = timerParams,
            remainingSeconds = timerParams?.let {
                TimerContractStore.getRemainingSeconds(context, pending.id)
            } ?: 0L
        )
    }
    
    // Checkpoint text display logic
    val isCheckpointTextLong = cachedData.checkpointLabel.length > 20
    val displayCheckpointText = if (isCheckpointTextLong) {
        cachedData.checkpointLabel.take(20) + "..."
    } else {
        cachedData.checkpointLabel
    }
    var showCheckpointTextDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                spotColor = Color.White.copy(alpha = 0.5f)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeumorphicBackground
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cachedData.bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Статус на отдельной строке
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = "PENDING",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                
                // Спиннер рядом со статусом
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = NeumorphicTextSecondary
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            
            // Депозит
            val formattedAmount = formatUnits(pending.amount, cachedData.tokenDecimals)
            EventRow("Deposite", "$formattedAmount ${cachedData.tokenSymbol}")

            // Deadline
            if (pending.deadline.toLong() > 0) {
                val deadlineDays = pending.deadline.toLong()
                EventRow("Deadline", "~$deadlineDays days")
            }

            // Для checkpoint контрактов (timerParams == null)
            if (cachedData.timerParams == null) {
                if (cachedData.checkpointIndices.isNotEmpty()) {
                    EventRowWithCheckpoints("Checkpoints", cachedData.checkpointIndices, cachedData.foundCheckpointIndices)
                    if (isCheckpointTextLong) {
                        EventRowClickable("Checkpoint text", displayCheckpointText) {
                            showCheckpointTextDialog = true
                        }
                    } else {
                        EventRow("Checkpoint text", cachedData.checkpointLabel)
                    }
                }
            }
            
            // Для timer контрактов
            if (cachedData.timerParams != null) {
                val safeSeconds = cachedData.remainingSeconds.coerceAtLeast(0L)
                val hours = safeSeconds / 3600
                val minutes = (safeSeconds % 3600) / 60
                val secs = safeSeconds % 60
                val hoursFormatted = String.format("%02d:%02d:%02d", hours, minutes, secs)
                
                EventRow("Time", hoursFormatted)
                EventRow("Swipe Control", if (cachedData.timerParams.swipeControl) "✓" else "✗")
                EventRow("Hand Control", if (cachedData.timerParams.handControl) "✓" else "✗")
            }

            // Статус транзакции
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Waiting for transaction confirmation...",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeumorphicTextSecondary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
            
            if (pending.txHash != null) {
                Text(
                    text = "TX: ${pending.txHash}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            // Кнопка чтения книги
            if (cachedData.hasBookFile) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onReadBook(pending.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                            spotColor = Color.White.copy(alpha = 0.4f)
                        ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeumorphicText
                    ),
                    border = BorderStroke(1.dp, NeumorphicTextSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Read")
                    }
                }
            }
        }
    }
    
    // Диалог для отображения полного текста checkpoint
    if (showCheckpointTextDialog) {
        Dialog(onDismissRequest = { showCheckpointTextDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .wrapContentHeight()
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.5f),
                        spotColor = Color.White.copy(alpha = 0.7f)
                    ),
                color = NeumorphicBackground,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Checkpoint text",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicText
                    )
                    Text(
                        text = cachedData.checkpointLabel,
                        fontSize = 16.sp,
                        color = NeumorphicText
                    )
                    Button(
                        onClick = { showCheckpointTextDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                                spotColor = Color.White.copy(alpha = 0.6f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeumorphicBackground,
                            contentColor = NeumorphicText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Close",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

