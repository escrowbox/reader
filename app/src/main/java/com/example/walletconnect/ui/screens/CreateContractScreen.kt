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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
import com.example.walletconnect.utils.BoxMetadataStore
import com.example.walletconnect.utils.EpubTextExtractor
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import android.net.Uri
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.math.BigDecimal
import java.math.RoundingMode
import org.jsoup.Jsoup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape

// ─── Design tokens ────────────────────────────────────────────────────────────

private val PremBgTop = Color(0xFFF6F9FE)
private val PremBgMid = Color(0xFFEEF3FB)
private val PremBgBot = Color(0xFFE6EDF8)


// Card surface — noticeably lighter than bg, but still gray-toned (not white)
private val PremSurface   = Color(0xFFEDF1F8)
private val PremSurfaceLo = Color(0xFFF6F9FE)  // slightly dimmer variant
private val PremBorderHi  = Color(0xFFEEF3FB)  // soft near-white highlight
private val PremBorderLo  = Color(0xFFBDCADB)  // visible gray-blue border
private val PremPurple    = Color(0xFF2D3A4F)   // deep navy-slate
private val PremCyan      = Color(0xFF4B6080)   // medium slate-blue
private val PremTextHi    = Color(0xFF0F172A)   // near black
private val PremTextMid   = Color(0xFF374151)   // medium dark gray
private val PremTextLo    = Color(0xFF8896A8)   // muted gray
// Keep legacy alias used in legacy code
private val PremGlass     = PremSurface
private val PremGlassDim  = PremSurfaceLo
private val PremError     = Color(0xFFEF4444)
private val PremSuccess   = Color(0xFF10B981)

// Active tab / selected chip gradient – inky navy
private val PremAccentBrush  = Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166)))
// Card border: soft light top-left + visible bottom-right
private val PremBorderBrush  = Brush.linearGradient(listOf(Color(0xFFF4F7FC), Color(0xFFBDCADB)))
// Background
private val PremBgBrush      = Brush.verticalGradient(listOf(PremBgTop, PremBgMid, PremBgBot))
private val PremDividerBrush = Brush.horizontalGradient(
    listOf(Color.Transparent, Color(0xFFBDCADB), Color.Transparent)
)
// Reusable shadow colors — slightly stronger on darker bg
private val ShadowAmbient = Color(0x22000000)
private val ShadowSpot    = Color(0x2E000000)

// ─── Shared micro-composables ─────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = ShadowAmbient,
                spotColor = ShadowSpot
            )
            .background(PremSurface, RoundedCornerShape(20.dp))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFFF4F7FC), Color(0xFFBDCADB))),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.sp,
        color = PremPurple,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun GlassDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PremDividerBrush)
    )
}

@Composable
private fun RowScope.DepositChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected)
                    Modifier
                        .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                        .background(PremAccentBrush, RoundedCornerShape(10.dp))
                else
                    Modifier
                        .background(PremSurfaceLo, RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFBDCADB), RoundedCornerShape(10.dp))
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else PremTextMid
        )
    }
}

@Composable
private fun premFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Color(0xFF2D3A4F),
    unfocusedBorderColor = Color(0xFFBDCADB),
    focusedLabelColor    = Color(0xFF2D3A4F),
    unfocusedLabelColor  = PremTextLo,
    focusedTextColor     = PremTextHi,
    unfocusedTextColor   = PremTextHi,
    cursorColor          = Color(0xFF2D3A4F),
    focusedPlaceholderColor   = PremTextLo,
    unfocusedPlaceholderColor = PremTextLo,
    focusedContainerColor     = PremSurface,
    unfocusedContainerColor   = PremSurfaceLo,
    errorBorderColor     = PremError,
    errorLabelColor      = PremError,
    errorTextColor       = PremTextHi,
    errorContainerColor  = Color(0xFFEDE0E0),
    errorCursorColor     = PremError,
    errorSupportingTextColor = PremError
)

@Composable
private fun PremSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor    = Color.White,
    checkedTrackColor    = Color(0xFF3D4D63),
    checkedBorderColor   = Color.Transparent,
    uncheckedThumbColor  = Color(0xFFBDC9DC),
    uncheckedTrackColor  = Color(0x40C4CEDC),
    uncheckedBorderColor = Color(0x80BDC9DC)
)

@Composable
private fun ValidationDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .wrapContentHeight()
                .shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                .background(PremSurface, RoundedCornerShape(24.dp))
                .border(1.dp, PremBorderBrush, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Please fill in all fields",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PremTextHi,
                    textAlign = TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PremAccentBrush, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

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

    var selectedEpubName by remember { mutableStateOf<String?>(null) }
    var selectedEpubUri  by remember { mutableStateOf<Uri?>(null) }

    var isTokenDeposit       by remember { mutableStateOf(false) }
    var mintAddress          by remember { mutableStateOf("") }
    var tokenBalance         by remember { mutableStateOf<SolanaManager.TokenInfo?>(null) }
    var isLoadingTokenBalance by remember { mutableStateOf(false) }
    var tokenSymbol          by remember { mutableStateOf("TOKEN") }
    var selectedDepositType  by remember { mutableStateOf("SOL") }

    LaunchedEffect(mintAddress, isTokenDeposit) {
        if (isTokenDeposit && mintAddress.length >= 32) {
            isLoadingTokenBalance = true
            tokenBalance = null
            try {
                val ownerAddress = manager.getSelectedAddress()
                if (ownerAddress.isNotBlank())
                    tokenBalance = manager.getTokenBalance(ownerAddress, mintAddress)
            } catch (e: Exception) {
                tokenBalance = null
            } finally {
                isLoadingTokenBalance = false
            }
        } else {
            tokenBalance = null
        }
    }

    var timerHours              by remember { mutableStateOf("") }
    var timerDays               by remember { mutableStateOf("") }
    var timerEthAmount          by remember { mutableStateOf("") }
    var timerEpubName           by remember { mutableStateOf<String?>(null) }
    var timerEpubUri            by remember { mutableStateOf<Uri?>(null) }
    var timerGeneratedAddress   by remember { mutableStateOf<String?>(null) }
    var timerGeneratedPrivateKey by remember { mutableStateOf<String?>(null) }
    var swipeControl by remember { mutableStateOf(true) }
    var handControl  by remember { mutableStateOf(true) }
    var faceControl  by remember { mutableStateOf(false) }

    var timerIsTokenDeposit       by remember { mutableStateOf(false) }
    var timerMintAddress          by remember { mutableStateOf("") }
    var timerTokenBalance         by remember { mutableStateOf<SolanaManager.TokenInfo?>(null) }
    var isLoadingTimerTokenBalance by remember { mutableStateOf(false) }
    var timerSelectedDepositType  by remember { mutableStateOf("SOL") }

    LaunchedEffect(timerMintAddress, timerIsTokenDeposit) {
        if (timerIsTokenDeposit && timerMintAddress.length >= 32) {
            isLoadingTimerTokenBalance = true
            timerTokenBalance = null
            try {
                val ownerAddress = manager.getSelectedAddress()
                if (ownerAddress.isNotBlank())
                    timerTokenBalance = manager.getTokenBalance(ownerAddress, timerMintAddress)
            } catch (e: Exception) {
                timerTokenBalance = null
            } finally {
                isLoadingTimerTokenBalance = false
            }
        } else {
            timerTokenBalance = null
        }
    }

    var showCheckpointsValidationDialog by remember { mutableStateOf(false) }
    var showTimerValidationDialog       by remember { mutableStateOf(false) }

    fun extractBookTitle(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                java.util.zip.ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.contains("content.opf", ignoreCase = true) ||
                            entry.name.contains("metadata.opf", ignoreCase = true) ||
                            entry.name.endsWith(".opf", ignoreCase = true)
                        ) {
                            val content = zip.bufferedReader().readText()
                            val doc = org.jsoup.Jsoup.parse(content)
                            val title = doc.select("dc|title, title").first()?.text()?.trim()
                            if (!title.isNullOrBlank()) return title
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            "EPUB file"
        } catch (e: Exception) {
            "EPUB file"
        }
    }

    fun extractPdfTitle(ctx: android.content.Context, uri: Uri): String {
        return try {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            var name: String? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = it.getString(idx)
                }
            }
            name?.removeSuffix(".pdf")?.trim()?.ifBlank { null } ?: "PDF file"
        } catch (e: Exception) {
            "PDF file"
        }
    }

    var generatedAddress    by remember { mutableStateOf<String?>(null) }
    var generatedPrivateKey by remember { mutableStateOf<String?>(null) }

    val epubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedEpubUri  = it
            selectedEpubName = extractBookTitle(it)
            scope.launch {
                val result = VaultManager.generateAndSaveKeyPair(context)
                generatedAddress    = result.first
                generatedPrivateKey = result.second
                if (result.first != "Error") {
                    val boxId = result.first
                    FileManager.saveEpubFile(context, it, boxId)
                    selectedEpubName?.let { name ->
                        BoxMetadataStore.setBookTitle(context, boxId, name)
                    }
                    val fullText = EpubTextExtractor.extractFullText(context, it)
                    val indices  = EpubTextExtractor.pickCheckpointIndices(fullText)
                    CheckpointIndexStore.saveIndices(context, boxId, indices)
                    CheckpointIndexStore.saveCheckpointLabel(context, boxId, " ${checkpointLabel.trim()} ")
                }
            }
        }
    }

    val timerEpubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            timerEpubUri = it
            val mimeType = context.contentResolver.getType(it)
            val isPdf = mimeType == "application/pdf"
            timerEpubName = if (isPdf) {
                extractPdfTitle(context, it)
            } else {
                extractBookTitle(it)
            }
            scope.launch {
                val result = VaultManager.generateAndSaveKeyPair(context)
                timerGeneratedAddress    = result.first
                timerGeneratedPrivateKey = result.second
                if (result.first != "Error") {
                    val boxId = result.first
                    if (isPdf) {
                        FileManager.savePdfFile(context, it, boxId)
                        BoxMetadataStore.setFileType(context, boxId, "pdf")
                    } else {
                        FileManager.saveEpubFile(context, it, boxId)
                        BoxMetadataStore.setFileType(context, boxId, "epub")
                    }
                    timerEpubName?.let { name ->
                        BoxMetadataStore.setBookTitle(context, boxId, name)
                    }
                }
            }
        }
    }

    val txStatus by manager.txStatusFlow.collectAsStateWithLifecycle()
    val transactionStatus = when (txStatus) {
        TxStatus.IDLE    -> ""
        TxStatus.SIGNING -> "Signing transaction..."
        TxStatus.MINING  -> "Confirming transaction..."
        TxStatus.SUCCESS -> "Success!"
        TxStatus.ERROR   -> "Transaction failed"
    }
    val isTxSuccess = txStatus == TxStatus.SUCCESS
    val isTxError   = txStatus == TxStatus.ERROR

    val ethBalance = manager.nativeEthBalance.observeAsState("").value

    fun parseBalanceToLamports(balanceStr: String): Long? {
        if (balanceStr.isBlank()) return null
        return try {
            val sol = balanceStr.replace(" SOL", "").trim()
            if (sol.isBlank()) return null
            java.math.BigDecimal(sol).multiply(java.math.BigDecimal(1_000_000_000)).toLong()
        } catch (e: Exception) { null }
    }

    val balanceLamports = parseBalanceToLamports(ethBalance)

    val daysInt        = days.toIntOrNull()
    val isDaysError    = days.isNotEmpty() && (daysInt == null || daysInt <= 0 || daysInt > 36500)
    val daysErrorText  = when {
        daysInt == null && days.isNotEmpty()  -> "Enter an integer"
        daysInt != null && daysInt <= 0       -> "Must be greater than 0"
        daysInt != null && daysInt > 366      -> "No more than 365 days"
        else -> null
    }

    val ethAmountDouble = try {
        if (ethAmount.isBlank()) null else ethAmount.toDouble()
    } catch (e: Exception) { null }

    val tokenDecimals   = tokenBalance?.decimals ?: 9
    val ethAmountRaw    = if (isTokenDeposit && tokenBalance != null)
        ethAmountDouble?.let { (it * Math.pow(10.0, tokenDecimals.toDouble())).toLong() } ?: 0L
    else
        ethAmountDouble?.let { (it * 1_000_000_000).toLong() } ?: 0L
    val ethAmountLamports = ethAmountRaw

    val exceedsBalance = if (isTokenDeposit)
        !isLoadingTokenBalance && tokenBalance != null && ethAmountRaw > tokenBalance!!.balance
    else
        balanceLamports != null && ethAmountRaw > balanceLamports

    val isEthAmountError  = ethAmount.isNotEmpty() && (ethAmountDouble == null || ethAmountDouble <= 0.0 || exceedsBalance)
    val ethAmountErrorText = when {
        ethAmountDouble == null && ethAmount.isNotEmpty() -> "Enter the number"
        ethAmountDouble != null && ethAmountDouble <= 0.0 -> "Must be greater than 0"
        exceedsBalance -> "Exceeds balance"
        else -> null
    }

    val isMintAddressValid = !isTokenDeposit || mintAddress.length >= 32
    val mintAddressError   = if (isTokenDeposit && mintAddress.isNotEmpty() && mintAddress.length < 32)
        "Invalid mint address" else null

    val isTokenBalanceReady = !isTokenDeposit || (!isLoadingTokenBalance && tokenBalance != null)
    val isFormValid = days.isNotEmpty() && ethAmount.isNotEmpty() &&
            !isDaysError && !isEthAmountError && isMintAddressValid && isTokenBalanceReady

    val timerHoursInt       = timerHours.toIntOrNull()
    val isTimerHoursError   = timerHours.isNotEmpty() && (timerHoursInt == null || timerHoursInt < 0)
    val timerHoursErrorText = when {
        timerHoursInt == null && timerHours.isNotEmpty() -> "Enter an integer"
        timerHoursInt != null && timerHoursInt < 0       -> "Can't be negative"
        else -> null
    }

    val timerDaysInt       = timerDays.toIntOrNull()
    val isTimerDaysError   = timerDays.isNotEmpty() && (timerDaysInt == null || timerDaysInt <= 0 || timerDaysInt > 36500)
    val timerDaysErrorText = when {
        timerDaysInt == null && timerDays.isNotEmpty()  -> "Enter an integer"
        timerDaysInt != null && timerDaysInt <= 0       -> "Must be greater than 0"
        timerDaysInt != null && timerDaysInt > 366      -> "No more than 365"
        else -> null
    }

    val timerEthAmountDouble = try {
        if (timerEthAmount.isBlank()) null else timerEthAmount.toDouble()
    } catch (e: Exception) { null }

    val timerTokenDecimals  = timerTokenBalance?.decimals ?: 9
    val timerEthAmountRaw   = if (timerIsTokenDeposit && timerTokenBalance != null)
        timerEthAmountDouble?.let { (it * Math.pow(10.0, timerTokenDecimals.toDouble())).toLong() } ?: 0L
    else
        timerEthAmountDouble?.let { (it * 1_000_000_000).toLong() } ?: 0L
    val timerEthAmountLamports = timerEthAmountRaw

    val timerExceedsBalance = if (timerIsTokenDeposit)
        !isLoadingTimerTokenBalance && timerTokenBalance != null && timerEthAmountRaw > timerTokenBalance!!.balance
    else
        balanceLamports != null && timerEthAmountRaw > balanceLamports

    val isTimerEthAmountError  = timerEthAmount.isNotEmpty() && (timerEthAmountDouble == null || timerEthAmountDouble <= 0.0 || timerExceedsBalance)
    val timerEthAmountErrorText = when {
        timerEthAmountDouble == null && timerEthAmount.isNotEmpty() -> "Enter the number"
        timerEthAmountDouble != null && timerEthAmountDouble <= 0.0 -> "Must be greater than 0"
        timerExceedsBalance -> "Exceeds balance"
        else -> null
    }

    val totalTimerDaysInt  = timerDaysInt ?: 0

    val timerMintAddressValid = !timerIsTokenDeposit || timerMintAddress.length >= 32
    val timerMintAddressError = if (timerIsTokenDeposit && timerMintAddress.isNotEmpty() && timerMintAddress.length < 32)
        "Invalid mint address" else null

    val isTimerTokenBalanceReady = !timerIsTokenDeposit || (!isLoadingTimerTokenBalance && timerTokenBalance != null)
    val isTimerFormValid = timerDays.isNotEmpty() && timerEthAmount.isNotEmpty() &&
            !isTimerDaysError && !isTimerEthAmountError &&
            timerGeneratedAddress != null && timerMintAddressValid && isTimerTokenBalanceReady

    var selectedTabIndex by remember { mutableStateOf(1) }
    val tabs = listOf("checkpoints", "timer")

    // ── UI ──────────────────────────────────────────────────────────────────────

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremBgBrush)
            .drawBehind {
                // soft blue-gray wash – top-left
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x25B0C4DC), Color.Transparent),
                        radius = 360.dp.toPx()
                    ),
                    radius = 360.dp.toPx(),
                    center = Offset(-60f, 200.dp.toPx())
                )
                // warm white wash – bottom-right
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x30D8E4F0), Color.Transparent),
                        radius = 300.dp.toPx()
                    ),
                    radius = 300.dp.toPx(),
                    center = Offset(size.width + 60f, size.height - 160.dp.toPx())
                )
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFF6F9FE), Color(0xFFEEF3FB), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                ) {
                    // ── App bar ──────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back button — raised circle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                                .background(PremSurface, CircleShape)
                                .border(1.dp, Color(0xFFBDCADB), CircleShape)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "back",
                                tint = PremTextHi,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val isConnected = manager.isConnected.observeAsState(false).value
                        LaunchedEffect(isConnected) {
                            if (isConnected) manager.refreshBalances()
                        }

                        // Balance pill
                        Box(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                                .background(PremSurface, RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0xFFBDCADB), RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = ethBalance.ifBlank { "0.0000 SOL" },
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PremCyan
                            )
                        }
                    }

                    // ── Tab row ──────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = 0.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                            .background(PremSurface, RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFBDCADB), RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .then(
                                        if (selectedTabIndex == index)
                                            Modifier.background(PremAccentBrush, RoundedCornerShape(11.dp))
                                        else
                                            Modifier
                                    )
                                    .clickable { selectedTabIndex = index }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontFamily = TirtoWritterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) Color.White else PremTextMid
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->

            when (selectedTabIndex) {

                // ════════════════════════════════════════════════════════════════
                // TAB 0  –  CHECKPOINTS
                // ════════════════════════════════════════════════════════════════
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        // ── Book ─────────────────────────────────────────────
                        SectionLabel("Book")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color(0xFF1E2D3D).copy(alpha = 0.28f),
                                    spotColor = Color(0xFF06B6D4).copy(alpha = 0.25f)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166))),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    Brush.linearGradient(listOf(Color(0x60FFFFFF), Color(0x20FFFFFF))),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { epubLauncher.launch("application/epub+zip") },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (selectedEpubName != null) "📖" else "📂",
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = if (selectedEpubName != null) {
                                        if (selectedEpubName!!.length > 40) selectedEpubName!!.take(37) + "…"
                                        else selectedEpubName!!
                                    } else "Choose EPUB file",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ── Deadline ─────────────────────────────────────────
                        SectionLabel("Deadline")
                        GlassCard {
                                                            Text(
                                    text = "How many days will it take you to find all the checkpoints?",
                                    fontSize = 11.sp,
                                    color = PremTextLo,
                                    modifier = Modifier.padding(bottom = 11.dp)
                                )
                            OutlinedTextField(
                                value = days,
                                onValueChange = {
                                    if (it.isEmpty() || it.all { c -> c.isDigit() }) days = it
                                },
                                label = { Text("deadline") },
                                placeholder = { Text("days until the deadline") },
                                isError = isDaysError,
                                supportingText = { if (daysErrorText != null) Text(daysErrorText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = premFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ── Deposit ──────────────────────────────────────────
                        SectionLabel("Deposit")
                        GlassCard {
                            // Deposit type selector
                            Text(
                                text = "Select the token you want to deposit or add your own SPL token",
                                fontSize = 11.sp,
                                color = PremTextLo,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DepositChip("SOL",  selectedDepositType == "SOL")  { selectedDepositType = "SOL";  isTokenDeposit = false; mintAddress = "" }
                                DepositChip("USDT", selectedDepositType == "USDT") { selectedDepositType = "USDT"; isTokenDeposit = true;  mintAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" }
                                DepositChip("USDC", selectedDepositType == "USDC") { selectedDepositType = "USDC"; isTokenDeposit = true;  mintAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" }
                                DepositChip("SPL",  selectedDepositType == "another") { selectedDepositType = "another"; isTokenDeposit = true; mintAddress = "" }
                            }

                            if (selectedDepositType == "another") {
                                OutlinedTextField(
                                    value = mintAddress,
                                    onValueChange = { mintAddress = it },
                                    label = { Text("mint address") },
                                    placeholder = { Text("Token mint address") },
                                    isError = mintAddressError != null,
                                    supportingText = { if (mintAddressError != null) Text(mintAddressError) },
                                    singleLine = true,
                                    colors = premFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (isTokenDeposit) {
                                GlassDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Token balance", color = PremTextMid, fontSize = 12.sp)
                                    if (isLoadingTokenBalance) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 1.5.dp,
                                            color = PremCyan
                                        )
                                    } else {
                                        Text(
                                            text = if (tokenBalance != null)
                                                String.format("%.${tokenBalance!!.decimals.coerceAtMost(6)}f", tokenBalance!!.uiAmount)
                                            else if (mintAddress.length >= 32) "0" else "—",
                                            color = PremCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            GlassDivider()

                            OutlinedTextField(
                                value = ethAmount,
                                onValueChange = {
                                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) ethAmount = it
                                },
                                label = { Text(if (isTokenDeposit) "amount" else "deposit") },
                                placeholder = { Text(if (isTokenDeposit) "Token amount" else "SOL amount") },
                                isError = isEthAmountError,
                                supportingText = { if (ethAmountErrorText != null) Text(ethAmountErrorText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = premFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ── Checkpoint text ───────────────────────────────────
                        SectionLabel("Checkpoint trigger")
                        GlassCard {
                            Text(
                                text = "This text will appear at random places throughout the book to confirm that you are reading carefully.",
                                fontSize = 11.sp,
                                color = PremTextLo,
                                lineHeight = 16.sp
                            )
                            OutlinedTextField(
                                value = checkpointLabel,
                                onValueChange = { checkpointLabel = it },
                                label = { Text("checkpoint text") },
                                colors = premFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                        }

                        // ── Transaction status ────────────────────────────────
                        if (transactionStatus.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isTxSuccess) PremSuccess.copy(alpha = 0.12f)
                                        else if (isTxError) PremError.copy(alpha = 0.12f)
                                        else PremGlass,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isTxSuccess -> PremSuccess.copy(alpha = 0.4f)
                                            isTxError   -> PremError.copy(alpha = 0.4f)
                                            else        -> Color(0x28FFFFFF)
                                        },
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!isTxSuccess && !isTxError) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = PremCyan
                                        )
                                    } else {
                                        Text(if (isTxSuccess) "✓" else "✕", color = if (isTxSuccess) PremSuccess else PremError, fontSize = 16.sp)
                                    }
                                    Text(
                                        text = transactionStatus,
                                        color = when {
                                            isTxSuccess -> PremSuccess
                                            isTxError   -> PremError
                                            else        -> PremTextHi
                                        },
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // ── Create button ─────────────────────────────────────
                        CreateBoxButton(
                            contract = manager,
                            activityResultSender = activityResultSender,
                            id = generatedAddress ?: "",
                            deadline = days.toIntOrNull() ?: 0,
                            amount = ethAmountLamports,
                            modifier = Modifier.fillMaxWidth(),
                            isFormValid = isFormValid && generatedAddress != null,
                            isTokenBox = isTokenDeposit,
                            mintAddress = if (isTokenDeposit) mintAddress else null,
                            tokenDecimals = if (isTokenDeposit) tokenBalance?.decimals else null,
                            tokenSymbol = if (isTokenDeposit) {
                                when (selectedDepositType) {
                                    "USDT" -> "USDT"; "USDC" -> "USDC"
                                    else   -> selectedDepositType.uppercase()
                                }
                            } else null,
                            onShowValidationError = { showCheckpointsValidationDialog = true },
                            onTransactionSent = {
                                generatedAddress?.let { boxId ->
                                    CheckpointContractStore.saveCheckpointParams(
                                        context = context,
                                        boxId = boxId,
                                        days = days.toIntOrNull() ?: 0,
                                        amount = ethAmountLamports.toBigInteger()
                                    )
                                }
                                onNavigateToContracts()
                            }
                        )
                    }
                }

                // ════════════════════════════════════════════════════════════════
                // TAB 1  –  TIMER
                // ════════════════════════════════════════════════════════════════
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        // ── Book ─────────────────────────────────────────────
                        SectionLabel("Book")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color(0xFF1E2D3D).copy(alpha = 0.28f),
                                    spotColor = Color(0xFF06B6D4).copy(alpha = 0.25f)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166))),
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    1.dp,
                                    Brush.linearGradient(listOf(Color(0x60FFFFFF), Color(0x20FFFFFF))),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { timerEpubLauncher.launch(arrayOf("application/epub+zip", "application/pdf")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (timerEpubName != null) "📖" else "📂",
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = if (timerEpubName != null) {
                                        if (timerEpubName!!.length > 40) timerEpubName!!.take(37) + "…"
                                        else timerEpubName!!
                                    } else "Choose EPUB / PDF file",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // ── Reading goal ──────────────────────────────────────
                        SectionLabel("Reading goal")
                        GlassCard {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Determine the number of hours to read and the number of days to complete.",
                                    fontSize = 11.sp,
                                    color = PremTextLo,
                                    modifier = Modifier.padding(bottom = 11.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                OutlinedTextField(
                                    value = timerHours,
                                    onValueChange = {
                                        if (it.isEmpty() || it.all { c -> c.isDigit() }) timerHours = it
                                    },
                                    label = { Text("hours") },
                                    placeholder = { Text("reading hours") },
                                    isError = isTimerHoursError,
                                    supportingText = { if (timerHoursErrorText != null) Text(timerHoursErrorText) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = premFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = timerDays,
                                    onValueChange = {
                                        if (it.isEmpty() || it.all { c -> c.isDigit() }) timerDays = it
                                    },
                                    label = { Text("deadline") },
                                    placeholder = { Text("days") },
                                    isError = isTimerDaysError,
                                    supportingText = { if (timerDaysErrorText != null) Text(timerDaysErrorText) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = premFieldColors(),
                                    modifier = Modifier.weight(1f)
                                )
                                }
                            }
                        }

                        // ── Deposit ───────────────────────────────────────────
                        SectionLabel("Deposit")
                        GlassCard {
                            Text(
                                text = "Select the token you want to deposit, or add your own SPL token.",
                                fontSize = 11.sp,
                                color = PremTextLo,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DepositChip("SOL",  timerSelectedDepositType == "SOL")  { timerSelectedDepositType = "SOL";  timerIsTokenDeposit = false; timerMintAddress = "" }
                                DepositChip("USDT", timerSelectedDepositType == "USDT") { timerSelectedDepositType = "USDT"; timerIsTokenDeposit = true;  timerMintAddress = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" }
                                DepositChip("USDC", timerSelectedDepositType == "USDC") { timerSelectedDepositType = "USDC"; timerIsTokenDeposit = true;  timerMintAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" }
                                DepositChip("SPL",  timerSelectedDepositType == "another") { timerSelectedDepositType = "another"; timerIsTokenDeposit = true; timerMintAddress = "" }
                            }

                            if (timerSelectedDepositType == "another") {
                                OutlinedTextField(
                                    value = timerMintAddress,
                                    onValueChange = { timerMintAddress = it },
                                    label = { Text("mint address") },
                                    placeholder = { Text("Token mint address") },
                                    isError = timerMintAddressError != null,
                                    supportingText = { if (timerMintAddressError != null) Text(timerMintAddressError) },
                                    singleLine = true,
                                    colors = premFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (timerIsTokenDeposit) {
                                GlassDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Token balance", color = PremTextMid, fontSize = 12.sp)
                                    if (isLoadingTimerTokenBalance) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 1.5.dp,
                                            color = PremCyan
                                        )
                                    } else {
                                        Text(
                                            text = if (timerTokenBalance != null)
                                                String.format("%.${timerTokenBalance!!.decimals.coerceAtMost(6)}f", timerTokenBalance!!.uiAmount)
                                            else if (timerMintAddress.length >= 32) "0" else "—",
                                            color = PremCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            GlassDivider()

                            OutlinedTextField(
                                value = timerEthAmount,
                                onValueChange = {
                                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) timerEthAmount = it
                                },
                                label = { Text(if (timerIsTokenDeposit) "amount" else "deposit") },
                                placeholder = { Text(if (timerIsTokenDeposit) "Token amount" else "SOL amount") },
                                isError = isTimerEthAmountError,
                                supportingText = { if (timerEthAmountErrorText != null) Text(timerEthAmountErrorText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = premFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ── Proof of reading ──────────────────────────────────
                        SectionLabel("Proof of reading")
                        GlassCard {
                            // Flip control
                                                        Text(
                                text = "If you disable these options, it will be enough to keep the screen active for the timer to work.",
                                fontSize = 11.sp,
                                color = PremTextLo,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { swipeControl = !swipeControl }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(if (swipeControl) 4.dp else 1.dp, RoundedCornerShape(10.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                                        .background(
                                            if (swipeControl) Color(0xFF2D3A4F).copy(alpha = 0.12f) else PremSurfaceLo,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (swipeControl) Color(0xFF2D3A4F).copy(alpha = 0.4f) else Color(0xFFC8D4E4),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) { Text("👆", fontSize = 16.sp) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("flip control", color = PremTextHi, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(
                                        "Timer pauses if no page flip for 5 min.",
                                        color = PremTextLo, fontSize = 10.sp, lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = swipeControl,
                                    onCheckedChange = { swipeControl = it },
                                    colors = PremSwitchColors()
                                )
                            }

                            GlassDivider()

                            // Hand control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { handControl = !handControl }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(if (handControl) 4.dp else 1.dp, RoundedCornerShape(10.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                                        .background(
                                            if (handControl) Color(0xFF2D3A4F).copy(alpha = 0.12f) else PremSurfaceLo,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (handControl) Color(0xFF2D3A4F).copy(alpha = 0.4f) else Color(0xFFC8D4E4),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) { Text("🤚", fontSize = 16.sp) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("hand control", color = PremTextHi, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(
                                        "The timer pauses if the device is stationary for more than 5 minutes.",
                                        color = PremTextLo, fontSize = 10.sp, lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = handControl,
                                    onCheckedChange = { handControl = it },
                                    colors = PremSwitchColors()
                                )
                            }

                            GlassDivider()

                            // Face control (disabled)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(PremGlassDim, RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Text("👁", fontSize = 16.sp) }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("face control", color = PremTextLo, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Box(
                                            modifier = Modifier
                                                .background(PremTextLo.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text("soon", color = PremTextLo, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        }
                                    }

                                }
                                Switch(
                                    checked = faceControl,
                                    onCheckedChange = { faceControl = it },
                                    enabled = false,
                                    colors = SwitchDefaults.colors(
                                        disabledCheckedThumbColor   = PremTextLo,
                                        disabledCheckedTrackColor   = Color(0x14FFFFFF),
                                        disabledUncheckedThumbColor = PremTextLo.copy(alpha = 0.4f),
                                        disabledUncheckedTrackColor = Color(0x0AFFFFFF),
                                        disabledUncheckedBorderColor = Color(0x15FFFFFF)
                                    )
                                )
                            }
                        }

                        // ── Transaction status ────────────────────────────────
                        if (transactionStatus.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isTxSuccess) PremSuccess.copy(alpha = 0.12f)
                                        else if (isTxError) PremError.copy(alpha = 0.12f)
                                        else PremGlass,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isTxSuccess -> PremSuccess.copy(alpha = 0.4f)
                                            isTxError   -> PremError.copy(alpha = 0.4f)
                                            else        -> Color(0x28FFFFFF)
                                        },
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!isTxSuccess && !isTxError) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = PremCyan
                                        )
                                    } else {
                                        Text(if (isTxSuccess) "✓" else "✕", color = if (isTxSuccess) PremSuccess else PremError, fontSize = 16.sp)
                                    }
                                    Text(
                                        text = transactionStatus,
                                        color = when {
                                            isTxSuccess -> PremSuccess
                                            isTxError   -> PremError
                                            else        -> PremTextHi
                                        },
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        // ── Create button ─────────────────────────────────────
                        CreateBoxButton(
                            contract = manager,
                            activityResultSender = activityResultSender,
                            id = timerGeneratedAddress ?: "",
                            deadline = totalTimerDaysInt,
                            amount = timerEthAmountLamports,
                            modifier = Modifier.fillMaxWidth(),
                            isFormValid = isTimerFormValid,
                            isTokenBox = timerIsTokenDeposit,
                            mintAddress = if (timerIsTokenDeposit) timerMintAddress else null,
                            tokenDecimals = if (timerIsTokenDeposit) timerTokenBalance?.decimals else null,
                            tokenSymbol = if (timerIsTokenDeposit) {
                                when (timerSelectedDepositType) {
                                    "USDT" -> "USDT"; "USDC" -> "USDC"
                                    else   -> timerSelectedDepositType.uppercase()
                                }
                            } else null,
                            onShowValidationError = { showTimerValidationDialog = true },
                            onTransactionSent = {
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

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Validation dialogs ───────────────────────────────────────────────
        if (showCheckpointsValidationDialog)
            ValidationDialog(onDismiss = { showCheckpointsValidationDialog = false })

        if (showTimerValidationDialog)
            ValidationDialog(onDismiss = { showTimerValidationDialog = false })
    }
}
