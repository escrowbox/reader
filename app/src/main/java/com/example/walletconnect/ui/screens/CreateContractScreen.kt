package com.example.walletconnect.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.walletconnect.R
import com.example.walletconnect.ui.theme.TirtoWritterFontFamily
import com.example.walletconnect.ui.theme.NeumorphicBackground
import com.example.walletconnect.ui.theme.NeumorphicText
import com.example.walletconnect.ui.theme.NeumorphicTextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletconnect.SolanaManager
import com.example.walletconnect.ui.hooks.TxStatus
import com.example.walletconnect.ui.components.CreateBoxButton
import com.example.walletconnect.utils.VaultManager
import com.example.walletconnect.utils.FileManager
import com.example.walletconnect.utils.CheckpointIndexStore
import com.example.walletconnect.utils.CheckpointContractStore
import com.example.walletconnect.utils.TimerContractStore
import com.example.walletconnect.utils.EpubTextExtractor
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import android.net.Uri
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode
import org.jsoup.Jsoup

/**
 * Экран создания контракта с формой ввода
 */
/**
 * Экран создания контракта и привязки EPUB-книги к боксу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateContractScreen(
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    onBack: () -> Unit,
    onNavigateToContracts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var days by remember { mutableStateOf("") }
    var ethAmount by remember { mutableStateOf("") }
    var checkpointLabel by remember { mutableStateOf("Checkpoint found! Tap me!") }

    // Состояние для EPUB файла
    var selectedEpubName by remember { mutableStateOf<String?>(null) }
    var selectedEpubUri by remember { mutableStateOf<Uri?>(null) }
    
    // Состояния для выбора типа депозита (checkpoints tab)
    var isTokenDeposit by remember { mutableStateOf(false) }
    var mintAddress by remember { mutableStateOf("") }
    var tokenBalance by remember { mutableStateOf<SolanaManager.TokenInfo?>(null) }
    var isLoadingTokenBalance by remember { mutableStateOf(false) }
    var tokenSymbol by remember { mutableStateOf("TOKEN") }
    var selectedDepositType by remember { mutableStateOf("SOL") } // SOL, USDT, USDC, another
    
    // Загрузка баланса токена при изменении mint address (checkpoints tab)
    LaunchedEffect(mintAddress, isTokenDeposit) {
        if (isTokenDeposit && mintAddress.length >= 32) {
            isLoadingTokenBalance = true
            tokenBalance = null
            try {
                val ownerAddress = manager.getSelectedAddress()
                if (ownerAddress.isNotBlank()) {
                    tokenBalance = manager.getTokenBalance(ownerAddress, mintAddress)
                }
            } catch (e: Exception) {
                tokenBalance = null
            } finally {
                isLoadingTokenBalance = false
            }
        } else {
            tokenBalance = null
        }
    }
    
    // Состояния для таба timer
    var timerHours by remember { mutableStateOf("") }
    var timerDays by remember { mutableStateOf("") }
    var timerEthAmount by remember { mutableStateOf("") }
    var timerEpubName by remember { mutableStateOf<String?>(null) }
    var timerEpubUri by remember { mutableStateOf<Uri?>(null) }
    var timerGeneratedAddress by remember { mutableStateOf<String?>(null) }
    var timerGeneratedPrivateKey by remember { mutableStateOf<String?>(null) }
    var swipeControl by remember { mutableStateOf(true) }
    var handControl by remember { mutableStateOf(true) }
    var faceControl by remember { mutableStateOf(false) }
    
    // Состояния для выбора типа депозита (timer tab)
    var timerIsTokenDeposit by remember { mutableStateOf(false) }
    var timerMintAddress by remember { mutableStateOf("") }
    var timerTokenBalance by remember { mutableStateOf<SolanaManager.TokenInfo?>(null) }
    var isLoadingTimerTokenBalance by remember { mutableStateOf(false) }
    var timerSelectedDepositType by remember { mutableStateOf("SOL") } // SOL, USDT, USDC, another
    
    // Загрузка баланса токена при изменении mint address (timer tab)
    LaunchedEffect(timerMintAddress, timerIsTokenDeposit) {
        if (timerIsTokenDeposit && timerMintAddress.length >= 32) {
            isLoadingTimerTokenBalance = true
            timerTokenBalance = null
            try {
                val ownerAddress = manager.getSelectedAddress()
                if (ownerAddress.isNotBlank()) {
                    timerTokenBalance = manager.getTokenBalance(ownerAddress, timerMintAddress)
                }
            } catch (e: Exception) {
                timerTokenBalance = null
            } finally {
                isLoadingTimerTokenBalance = false
            }
        } else {
            timerTokenBalance = null
        }
    }
    
    // Состояния для модалок с описанием
    var showSwipeControlDialog by remember { mutableStateOf(false) }
    var showHandControlDialog by remember { mutableStateOf(false) }
    
    // Состояния для модалок валидации
    var showCheckpointsValidationDialog by remember { mutableStateOf(false) }
    var showTimerValidationDialog by remember { mutableStateOf(false) }
    
    // Функция для извлечения названия книги из EPUB
    fun extractBookTitle(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                java.util.zip.ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.contains("content.opf", ignoreCase = true) || 
                            entry.name.contains("metadata.opf", ignoreCase = true) ||
                            entry.name.endsWith(".opf", ignoreCase = true)) {
                            val content = zip.bufferedReader().readText()
                            val doc = org.jsoup.Jsoup.parse(content)
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

    // Состояние для отображения результата генерации
    var generatedAddress by remember { mutableStateOf<String?>(null) }
    var generatedPrivateKey by remember { mutableStateOf<String?>(null) }

    // Launcher для выбора файла (checkpoints)
    val epubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedEpubUri = it
            // Извлекаем название книги из EPUB
            selectedEpubName = extractBookTitle(it)
            
            // Автоматически генерируем ключи при загрузке EPUB
            scope.launch {
                val result = VaultManager.generateAndSaveKeyPair(context)
                generatedAddress = result.first
                generatedPrivateKey = result.second

                // Сохраняем файл во внутреннюю память приложения
                if (result.first != "Error") {
                    val boxId = result.first
                    FileManager.saveEpubFile(context, it, boxId)

                    // Извлекаем текст книги и генерируем 3 индекса чекпоинтов
                    val fullText = EpubTextExtractor.extractFullText(context, it)
                    val indices = EpubTextExtractor.pickCheckpointIndices(fullText)
                    CheckpointIndexStore.saveIndices(context, boxId, indices)
                    
                    // Сохраняем текст чекпоинта
                    CheckpointIndexStore.saveCheckpointLabel(context, boxId, " ${checkpointLabel.trim()} ")
                }
            }
        }
    }
    
    // Launcher для выбора файла (timer)
    val timerEpubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            timerEpubUri = it
            // Извлекаем название книги из EPUB
            timerEpubName = extractBookTitle(it)
            
            // Автоматически генерируем ключи при загрузке EPUB
            scope.launch {
                val result = VaultManager.generateAndSaveKeyPair(context)
                timerGeneratedAddress = result.first
                timerGeneratedPrivateKey = result.second

                // Сохраняем файл во внутреннюю память приложения
                if (result.first != "Error") {
                    val boxId = result.first
                    FileManager.saveEpubFile(context, it, boxId)
                }
            }
        }
    }

    val txStatus by manager.txStatusFlow.collectAsStateWithLifecycle()
    val transactionStatus = when(txStatus) {
        TxStatus.IDLE -> ""
        TxStatus.SIGNING -> "Signing transaction..."
        TxStatus.MINING -> "Confirming transaction..."
        TxStatus.SUCCESS -> "Success!"
        TxStatus.ERROR -> "Transaction failed"
    }
    
    // Получаем баланс кошелька через LiveData
    val ethBalance = manager.nativeEthBalance.observeAsState("").value
    
    // Парсим баланс в lamports для валидации
    fun parseBalanceToLamports(balanceStr: String): Long? {
        if (balanceStr.isBlank()) return null
        return try {
            val solValue = balanceStr.replace(" SOL", "").trim()
            if (solValue.isBlank()) return null
            val bd = java.math.BigDecimal(solValue)
            bd.multiply(java.math.BigDecimal(1_000_000_000)).toLong()
        } catch (e: Exception) {
            null
        }
    }
    
    val balanceLamports = parseBalanceToLamports(ethBalance)

    // Валидация дней
    val daysInt = days.toIntOrNull()
    val isDaysError = days.isNotEmpty() && (daysInt == null || daysInt <= 0 || daysInt > 36500)
    val daysErrorText = when {
        daysInt == null && days.isNotEmpty() -> "Введите целое число"
        daysInt != null && daysInt <= 0 -> "Должно быть больше 0"
        daysInt != null && daysInt > 366 -> "Не более 365 дней"
        else -> null
    }

    val ethAmountDouble = try {
        if (ethAmount.isBlank()) null else ethAmount.toDouble()
    } catch (e: Exception) {
        null
    }
    
    // Для токенов используем decimals из tokenBalance, для SOL - 9
    val tokenDecimals = tokenBalance?.decimals ?: 9
    val ethAmountRaw = if (isTokenDeposit && tokenBalance != null) {
        // Для токенов: умножаем на 10^decimals
        ethAmountDouble?.let { (it * Math.pow(10.0, tokenDecimals.toDouble())).toLong() } ?: 0L
    } else {
        // Для SOL: lamports (9 decimals)
        ethAmountDouble?.let { (it * 1_000_000_000).toLong() } ?: 0L
    }
    // Для обратной совместимости сохраняем ethAmountLamports
    val ethAmountLamports = ethAmountRaw
    
    // Проверка баланса: для токенов сравниваем с tokenBalance, для SOL с balanceLamports
    // Не проверяем баланс пока идет загрузка
    val exceedsBalance = if (isTokenDeposit) {
        !isLoadingTokenBalance && tokenBalance != null && ethAmountRaw > tokenBalance!!.balance
    } else {
        balanceLamports != null && ethAmountRaw > balanceLamports
    }
    val isEthAmountError = ethAmount.isNotEmpty() && (ethAmountDouble == null || ethAmountDouble <= 0.0 || exceedsBalance)
    val ethAmountErrorText = when {
        ethAmountDouble == null && ethAmount.isNotEmpty() -> "Enter the number"
        ethAmountDouble != null && ethAmountDouble <= 0.0 -> "Must be greater than 0"
        exceedsBalance -> "Exceeds balance"
        else -> null
    }

    // Валидация mint address для токенов
    val isMintAddressValid = !isTokenDeposit || mintAddress.length >= 32
    val mintAddressError = if (isTokenDeposit && mintAddress.isNotEmpty() && mintAddress.length < 32) {
        "Invalid mint address"
    } else null
    
    // Для токенов требуем загруженный баланс (чтобы знать decimals)
    val isTokenBalanceReady = !isTokenDeposit || (!isLoadingTokenBalance && tokenBalance != null)
    
    val isFormValid = days.isNotEmpty() && ethAmount.isNotEmpty() && !isDaysError && !isEthAmountError && isMintAddressValid && isTokenBalanceReady

    // Валидация для timer таба
    val timerHoursInt = timerHours.toIntOrNull()
    val isTimerHoursError = timerHours.isNotEmpty() && (timerHoursInt == null || timerHoursInt < 0 )
    val timerHoursErrorText = when {
        timerHoursInt == null && timerHours.isNotEmpty() -> "Enter an integer"
        timerHoursInt != null && timerHoursInt < 0 -> "Can't be negative"
        else -> null
    }

    val timerDaysInt = timerDays.toIntOrNull()
    val isTimerDaysError = timerDays.isNotEmpty() && (timerDaysInt == null || timerDaysInt <= 0 || timerDaysInt > 36500)
    val timerDaysErrorText = when {
        timerDaysInt == null && timerDays.isNotEmpty() -> "Enter an integer"
        timerDaysInt != null && timerDaysInt <= 0 -> "Must be greater than 0"
        timerDaysInt != null && timerDaysInt > 366 -> "No more than 365"
        else -> null
    }

    val timerEthAmountDouble = try {
        if (timerEthAmount.isBlank()) null else timerEthAmount.toDouble()
    } catch (e: Exception) {
        null
    }
    
    // Для токенов используем decimals из timerTokenBalance, для SOL - 9
    val timerTokenDecimals = timerTokenBalance?.decimals ?: 9
    val timerEthAmountRaw = if (timerIsTokenDeposit && timerTokenBalance != null) {
        // Для токенов: умножаем на 10^decimals
        timerEthAmountDouble?.let { (it * Math.pow(10.0, timerTokenDecimals.toDouble())).toLong() } ?: 0L
    } else {
        // Для SOL: lamports (9 decimals)
        timerEthAmountDouble?.let { (it * 1_000_000_000).toLong() } ?: 0L
    }
    // Для обратной совместимости сохраняем timerEthAmountLamports
    val timerEthAmountLamports = timerEthAmountRaw
    
    // Проверка баланса: для токенов сравниваем с timerTokenBalance, для SOL с balanceLamports
    // Не проверяем баланс пока идет загрузка
    val timerExceedsBalance = if (timerIsTokenDeposit) {
        !isLoadingTimerTokenBalance && timerTokenBalance != null && timerEthAmountRaw > timerTokenBalance!!.balance
    } else {
        balanceLamports != null && timerEthAmountRaw > balanceLamports
    }
    val isTimerEthAmountError = timerEthAmount.isNotEmpty() && (timerEthAmountDouble == null || timerEthAmountDouble <= 0.0 || timerExceedsBalance)
    val timerEthAmountErrorText = when {
        timerEthAmountDouble == null && timerEthAmount.isNotEmpty() -> "Enter the number"
        timerEthAmountDouble != null && timerEthAmountDouble <= 0.0 -> "Must be greater than 0"
        timerExceedsBalance -> "Exceeds balance"
        else -> null
    }

    val totalTimerDaysInt = timerDaysInt ?: 0

    // Валидация mint address для токенов (timer tab)
    val timerMintAddressValid = !timerIsTokenDeposit || timerMintAddress.length >= 32
    val timerMintAddressError = if (timerIsTokenDeposit && timerMintAddress.isNotEmpty() && timerMintAddress.length < 32) {
        "Invalid mint address"
    } else null
    
    // Для токенов требуем загруженный баланс (чтобы знать decimals)
    val isTimerTokenBalanceReady = !timerIsTokenDeposit || (!isLoadingTimerTokenBalance && timerTokenBalance != null)
    
    val isTimerFormValid = timerDays.isNotEmpty() && timerEthAmount.isNotEmpty() && 
                          !isTimerDaysError && !isTimerEthAmountError && 
                          timerGeneratedAddress != null && timerMintAddressValid && isTimerTokenBalanceReady

    var selectedTabIndex by remember { mutableStateOf(1) }
    val tabs = listOf("checkpoints", "timer")

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = NeumorphicBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                Icon(Icons.Default.ArrowBack, contentDescription = "back")
                            }
                            
                            val isConnected = manager.isConnected.observeAsState(false).value
                            
                            // Загружаем баланс при открытии экрана, если кошелек подключен
                            LaunchedEffect(isConnected) {
                                if (isConnected) {
                                    manager.refreshBalances()
                                }
                            }
                            
                            Text(
                                text = ethBalance.ifBlank { "0.0000 SOL" },
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 16.sp,
                                color = NeumorphicText,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        
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
            }
        ) { innerPadding ->
            when (selectedTabIndex) {
                0 -> { // checkpoints
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 0.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Кнопка загрузки EPUB в стиле OutlinedTextField
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val displayText = if (selectedEpubName != null) {
                                if (selectedEpubName!!.length > 50) {
                                    selectedEpubName!!.take(47) + "..."
                                } else {
                                    selectedEpubName!!
                                }
                            } else ""
                            
                            OutlinedTextField(
                                value = displayText,
                                onValueChange = { },
                                readOnly = true,
                                enabled = false,
                                label = if (selectedEpubName == null) { { Text("EPUB file") } } else null,
                                placeholder = { Text("Загрузить EPUB file") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = NeumorphicText,
                                    disabledBorderColor = NeumorphicTextSecondary,
                                    disabledLabelColor = NeumorphicTextSecondary,
                                    disabledPlaceholderColor = NeumorphicTextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                        spotColor = Color.White.copy(alpha = 0.4f)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .matchParentSize()
                                    .clickable { epubLauncher.launch("application/epub+zip") }
                            )
                        }

                        OutlinedTextField(
                            value = days,
                            onValueChange = { 
                                if (it.isEmpty() || (it.all { char -> char.isDigit() })) {
                                    days = it 
                                }
                            },
                            label = { Text("deadline") },
                            placeholder = { Text("days until the deadline") },
                            isError = isDaysError,
                            supportingText = { if (daysErrorText != null) Text(daysErrorText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeumorphicTextSecondary,
                                unfocusedBorderColor = NeumorphicTextSecondary,
                                focusedLabelColor = NeumorphicTextSecondary,
                                unfocusedLabelColor = NeumorphicTextSecondary,
                                focusedTextColor = NeumorphicText,
                                unfocusedTextColor = NeumorphicText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                    spotColor = Color.White.copy(alpha = 0.4f)
                                )
                        )

                        // Выбор типа депозита: SOL, USDT, USDC или another
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Deposit type:",
                                color = NeumorphicText,
                                fontFamily = TirtoWritterFontFamily
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    onClick = { 
                                        selectedDepositType = "SOL"
                                        isTokenDeposit = false
                                        mintAddress = ""
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("SOL")
                                        }
                                    },
                                    selected = selectedDepositType == "SOL",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    onClick = { 
                                        selectedDepositType = "USDT"
                                        isTokenDeposit = true
                                        mintAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("USDT")
                                        }
                                    },
                                    selected = selectedDepositType == "USDT",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    onClick = { 
                                        selectedDepositType = "USDC"
                                        isTokenDeposit = true
                                        mintAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("USDC")
                                        }
                                    },
                                    selected = selectedDepositType == "USDC",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    onClick = { 
                                        selectedDepositType = "another"
                                        isTokenDeposit = true
                                        mintAddress = ""
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "SPL",
                                                maxLines = 1
                                            )
                                        }
                                    },
                                    selected = selectedDepositType == "another",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        // Поле для mint address (только для "another")
                        if (selectedDepositType == "another") {
                            OutlinedTextField(
                                value = mintAddress,
                                onValueChange = { mintAddress = it },
                                label = { Text("mint address") },
                                placeholder = { Text("Token mint address") },
                                isError = mintAddressError != null,
                                supportingText = { if (mintAddressError != null) Text(mintAddressError) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeumorphicTextSecondary,
                                    unfocusedBorderColor = NeumorphicTextSecondary,
                                    focusedLabelColor = NeumorphicTextSecondary,
                                    unfocusedLabelColor = NeumorphicTextSecondary,
                                    focusedTextColor = NeumorphicText,
                                    unfocusedTextColor = NeumorphicText
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                        spotColor = Color.White.copy(alpha = 0.4f)
                                    )
                            )
                        }
                        
                        // Отображение баланса токена
                        if (isTokenDeposit) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Token balance: ",
                                    color = NeumorphicTextSecondary,
                                    fontSize = 14.sp
                                )
                                if (isLoadingTokenBalance) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = NeumorphicTextSecondary
                                    )
                                } else {
                                    Text(
                                        text = if (tokenBalance != null) {
                                            String.format("%.${tokenBalance!!.decimals.coerceAtMost(6)}f", tokenBalance!!.uiAmount)
                                        } else if (mintAddress.length >= 32) {
                                            "0"
                                        } else {
                                            "—"
                                        },
                                        color = NeumorphicText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = ethAmount,
                            onValueChange = { 
                                // Разрешаем пустую строку, цифры и одну точку
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    ethAmount = it 
                                }
                            },
                            label = { Text(if (isTokenDeposit) "amount" else "deposite") },
                            placeholder = { Text(if (isTokenDeposit) "Token amount" else "") },
                            isError = isEthAmountError,
                            supportingText = { if (ethAmountErrorText != null) Text(ethAmountErrorText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeumorphicTextSecondary,
                                unfocusedBorderColor = NeumorphicTextSecondary,
                                focusedLabelColor = NeumorphicTextSecondary,
                                unfocusedLabelColor = NeumorphicTextSecondary,
                                focusedTextColor = NeumorphicText,
                                unfocusedTextColor = NeumorphicText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                    spotColor = Color.White.copy(alpha = 0.4f)
                                )
                        )

                        OutlinedTextField(
                            value = checkpointLabel,
                            onValueChange = { checkpointLabel = it },
                            label = { Text("checkpoint text") },
                            placeholder = { Text("") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeumorphicTextSecondary,
                                unfocusedBorderColor = NeumorphicTextSecondary,
                                focusedLabelColor = NeumorphicTextSecondary,
                                unfocusedLabelColor = NeumorphicTextSecondary,
                                focusedTextColor = NeumorphicText,
                                unfocusedTextColor = NeumorphicText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                    spotColor = Color.White.copy(alpha = 0.4f)
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (transactionStatus.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NeumorphicBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                                        spotColor = Color.White.copy(alpha = 0.5f)
                                    ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = NeumorphicText
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = transactionStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeumorphicText
                                    )
                                }
                            }
                        }

                        CreateBoxButton(
                            contract = manager,
                            activityResultSender = activityResultSender,
                            id = generatedAddress ?: "",
                            deadline = days.toIntOrNull() ?: 0,
                            amount = ethAmountLamports,
                            modifier = Modifier,
                            isFormValid = isFormValid && generatedAddress != null,
                            isTokenBox = isTokenDeposit,
                            mintAddress = if (isTokenDeposit) mintAddress else null,
                            tokenDecimals = if (isTokenDeposit) tokenBalance?.decimals else null,
                            tokenSymbol = if (isTokenDeposit) {
                                when (selectedDepositType) {
                                    "USDT" -> "USDT"
                                    "USDC" -> "USDC"
                                    else -> selectedDepositType.uppercase()
                                }
                            } else null,
                            onShowValidationError = { showCheckpointsValidationDialog = true },
                            onTransactionSent = {
                                // Сохраняем параметры checkpoints контракта
                                generatedAddress?.let { boxId ->
                                    val daysValue = days.toIntOrNull() ?: 0
                                    CheckpointContractStore.saveCheckpointParams(
                                        context = context,
                                        boxId = boxId,
                                        days = daysValue,
                                        amount = ethAmountLamports.toBigInteger()
                                    )
                                }
                                onNavigateToContracts()
                            }
                        )

                        // Блок отображения результата

                    }
                }
                1 -> { // timer
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 0.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val displayText = if (timerEpubName != null) {
                                if (timerEpubName!!.length > 50) {
                                    timerEpubName!!.take(47) + "..."
                                } else {
                                    timerEpubName!!
                                }
                            } else ""
                            
                            OutlinedTextField(
                                value = displayText,
                                onValueChange = { },
                                readOnly = true,
                                enabled = false,
                                label = if (timerEpubName == null) { { Text("EPUB файл") } } else null,
                                placeholder = { Text("EPUB file") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = NeumorphicText,
                                    disabledBorderColor = NeumorphicTextSecondary,
                                    disabledLabelColor = NeumorphicTextSecondary,
                                    disabledPlaceholderColor = NeumorphicTextSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                        spotColor = Color.White.copy(alpha = 0.4f)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .matchParentSize()
                                    .clickable { timerEpubLauncher.launch("application/epub+zip") }
                            )
                        }

                        // Количество часов
                        OutlinedTextField(
                            value = timerHours,
                            onValueChange = { 
                                if (it.isEmpty() || (it.all { char -> char.isDigit() })) {
                                    timerHours = it 
                                }
                            },
                            label = { Text("hours") },
                            placeholder = { Text("hours of reading") },
                            isError = isTimerHoursError,
                            supportingText = { if (timerHoursErrorText != null) Text(timerHoursErrorText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeumorphicTextSecondary,
                                unfocusedBorderColor = NeumorphicTextSecondary,
                                focusedLabelColor = NeumorphicTextSecondary,
                                unfocusedLabelColor = NeumorphicTextSecondary,
                                focusedTextColor = NeumorphicText,
                                unfocusedTextColor = NeumorphicText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                    spotColor = Color.White.copy(alpha = 0.4f)
                                )
                        )

                        // Количество дней
                        OutlinedTextField(
                            value = timerDays,
                            onValueChange = { 
                                if (it.isEmpty() || (it.all { char -> char.isDigit() })) {
                                    timerDays = it 
                                }
                            },
                            label = { Text("deadline") },
                            placeholder = { Text("days until the deadline") },
                            isError = isTimerDaysError,
                            supportingText = { if (timerDaysErrorText != null) Text(timerDaysErrorText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeumorphicTextSecondary,
                                unfocusedBorderColor = NeumorphicTextSecondary,
                                focusedLabelColor = NeumorphicTextSecondary,
                                unfocusedLabelColor = NeumorphicTextSecondary,
                                focusedTextColor = NeumorphicText,
                                unfocusedTextColor = NeumorphicText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                    spotColor = Color.White.copy(alpha = 0.4f)
                                )
                        )

                        // Выбор типа депозита: SOL, USDT, USDC или another (timer tab)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Deposit type:",
                                color = NeumorphicText,
                                fontFamily = TirtoWritterFontFamily
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    onClick = { 
                                        timerSelectedDepositType = "SOL"
                                        timerIsTokenDeposit = false
                                        timerMintAddress = ""
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("SOL")
                                        }
                                    },
                                    selected = timerSelectedDepositType == "SOL",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    onClick = { 
                                        timerSelectedDepositType = "USDT"
                                        timerIsTokenDeposit = true
                                        timerMintAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("USDT")
                                        }
                                    },
                                    selected = timerSelectedDepositType == "USDT",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    onClick = { 
                                        timerSelectedDepositType = "USDC"
                                        timerIsTokenDeposit = true
                                        timerMintAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("USDC")
                                        }
                                    },
                                    selected = timerSelectedDepositType == "USDC",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    onClick = { 
                                        timerSelectedDepositType = "another"
                                        timerIsTokenDeposit = true
                                        timerMintAddress = ""
                                    },
                                    label = { 
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "SPL",
                                                maxLines = 1
                                            )
                                        }
                                    },
                                    selected = timerSelectedDepositType == "another",
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color.Black,
                                        selectedLabelColor = Color.White,
                                        containerColor = NeumorphicBackground,
                                        labelColor = NeumorphicText
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        // Поле для mint address (только для "another")
                        if (timerSelectedDepositType == "another") {
                            OutlinedTextField(
                                value = timerMintAddress,
                                onValueChange = { timerMintAddress = it },
                                label = { Text("mint address") },
                                placeholder = { Text("Token mint address") },
                                isError = timerMintAddressError != null,
                                supportingText = { if (timerMintAddressError != null) Text(timerMintAddressError) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeumorphicTextSecondary,
                                    unfocusedBorderColor = NeumorphicTextSecondary,
                                    focusedLabelColor = NeumorphicTextSecondary,
                                    unfocusedLabelColor = NeumorphicTextSecondary,
                                    focusedTextColor = NeumorphicText,
                                    unfocusedTextColor = NeumorphicText
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = RoundedCornerShape(8.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                        spotColor = Color.White.copy(alpha = 0.4f)
                                    )
                            )
                        }
                        
                        // Отображение баланса токена
                        if (timerIsTokenDeposit) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Token balance: ",
                                    color = NeumorphicTextSecondary,
                                    fontSize = 14.sp
                                )
                                if (isLoadingTimerTokenBalance) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = NeumorphicTextSecondary
                                    )
                                } else {
                                    Text(
                                        text = if (timerTokenBalance != null) {
                                            String.format("%.${timerTokenBalance!!.decimals.coerceAtMost(6)}f", timerTokenBalance!!.uiAmount)
                                        } else if (timerMintAddress.length >= 32) {
                                            "0"
                                        } else {
                                            "—"
                                        },
                                        color = NeumorphicText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Ставка
                        OutlinedTextField(
                            value = timerEthAmount,
                            onValueChange = { 
                                // Разрешаем пустую строку, цифры и одну точку
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    timerEthAmount = it 
                                }
                            },
                            label = { Text(if (timerIsTokenDeposit) "amount" else "deposite") },
                            placeholder = { Text(if (timerIsTokenDeposit) "Token amount" else "") },
                            isError = isTimerEthAmountError,
                            supportingText = { if (timerEthAmountErrorText != null) Text(timerEthAmountErrorText) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeumorphicTextSecondary,
                                unfocusedBorderColor = NeumorphicTextSecondary,
                                focusedLabelColor = NeumorphicTextSecondary,
                                unfocusedLabelColor = NeumorphicTextSecondary,
                                focusedTextColor = NeumorphicText,
                                unfocusedTextColor = NeumorphicText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.2f),
                                    spotColor = Color.White.copy(alpha = 0.4f)
                                )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Checkboxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = swipeControl,
                                    onCheckedChange = { swipeControl = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeumorphicText,
                                        uncheckedColor = NeumorphicTextSecondary
                                    )
                                )
                                Text(
                                    text = "swipe control",
                                    color = NeumorphicText,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.question),
                                    contentDescription = "Help",
                                    modifier = Modifier
                                        .clickable { showSwipeControlDialog = true }
                                        .size(20.dp)
                                        .padding(start = 8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = handControl,
                                    onCheckedChange = { handControl = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeumorphicText,
                                        uncheckedColor = NeumorphicTextSecondary
                                    )
                                )
                                Text(
                                    text = "hand control",
                                    color = NeumorphicText,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.question),
                                    contentDescription = "Help",
                                    modifier = Modifier
                                        .clickable { showHandControlDialog = true }
                                        .size(20.dp)
                                        .padding(start = 8.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = faceControl,
                                    onCheckedChange = { faceControl = it },
                                    enabled = false,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeumorphicTextSecondary,
                                        uncheckedColor = NeumorphicTextSecondary,
                                        disabledCheckedColor = NeumorphicTextSecondary,
                                        disabledUncheckedColor = NeumorphicTextSecondary
                                    )
                                )
                                Text(
                                    text = "face control",
                                    color = NeumorphicTextSecondary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (transactionStatus.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NeumorphicBackground),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                                        spotColor = Color.White.copy(alpha = 0.5f)
                                    ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = NeumorphicText
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = transactionStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeumorphicText
                                    )
                                }
                            }
                        }

                        CreateBoxButton(
                            contract = manager,
                            activityResultSender = activityResultSender,
                            id = timerGeneratedAddress ?: "",
                            deadline = totalTimerDaysInt,
                            amount = timerEthAmountLamports,
                            modifier = Modifier,
                            isFormValid = isTimerFormValid,
                            isTokenBox = timerIsTokenDeposit,
                            mintAddress = if (timerIsTokenDeposit) timerMintAddress else null,
                            tokenDecimals = if (timerIsTokenDeposit) timerTokenBalance?.decimals else null,
                            tokenSymbol = if (timerIsTokenDeposit) {
                                when (timerSelectedDepositType) {
                                    "USDT" -> "USDT"
                                    "USDC" -> "USDC"
                                    else -> timerSelectedDepositType.uppercase()
                                }
                            } else null,
                            onShowValidationError = { showTimerValidationDialog = true },
                            onTransactionSent = {
                                // Сохраняем параметры timer контракта
                                timerGeneratedAddress?.let { boxId ->
                                    TimerContractStore.saveTimerParams(
                                        context = context,
                                        boxId = boxId,
                                        hours = timerHoursInt ?: 0,
                                        days = timerDaysInt ?: 0,
                                        amount = timerEthAmountLamports.toBigInteger(),
                                        swipeControl = swipeControl,
                                        handControl = handControl,
                                        faceControl = faceControl
                                    )
                                }
                                onNavigateToContracts()
                            }
                        )

                                    Spacer(modifier = Modifier.width(30.dp))

                    }
                }
            }
        }
        
        // Диалог для swipe control
        if (showSwipeControlDialog) {
            Dialog(onDismissRequest = { showSwipeControlDialog = false }) {
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
                            text = "Swipe Control",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeumorphicText
                        )
                        Text(
                            text = "If you don't turn the pages for 5 minutes, the timer stops.",
                            fontSize = 16.sp,
                            color = NeumorphicText
                        )
                        Button(
                            onClick = { showSwipeControlDialog = false },
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
        
        // Диалог для hand control
        if (showHandControlDialog) {
            Dialog(onDismissRequest = { showHandControlDialog = false }) {
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
                            text = "Hand Control",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeumorphicText
                        )
                        Text(
                            text = "If you don't hold your phone in your hand for 5 minutes, the timer stops.",
                            fontSize = 16.sp,
                            color = NeumorphicText
                        )
                        Button(
                            onClick = { showHandControlDialog = false },
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
        
        // Диалог валидации для checkpoints таба
        if (showCheckpointsValidationDialog) {
            Dialog(onDismissRequest = { showCheckpointsValidationDialog = false }) {
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
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Please fill in all fields",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeumorphicText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showCheckpointsValidationDialog = false },
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
                                text = "OK",
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
        
        // Диалог валидации для timer таба
        if (showTimerValidationDialog) {
            Dialog(onDismissRequest = { showTimerValidationDialog = false }) {
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
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Please fill in all fields",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeumorphicText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showTimerValidationDialog = false },
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
                                text = "OK",
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
