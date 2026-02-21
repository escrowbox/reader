package com.example.walletconnect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// ─── Design tokens (mirrored from CreateContractScreen) ───────────────────────

private val EvBgTop      = Color(0xFFF6F9FE)
private val EvBgMid      = Color(0xFFD5DCE9)
private val EvBgBot      = Color(0xFFDEE6F2)
private val EvSurface    = Color(0xFFEDF1F8)
private val EvSurfaceLo  = Color(0xFFE8EDF5)
private val EvBorderHi   = Color(0xFFF4F7FC)
private val EvBorderLo   = Color(0xFFBDCADB)
private val EvNavy       = Color(0xFF2D3A4F)
private val EvSlate      = Color(0xFF4B6080)
private val EvTextHi     = Color(0xFF0F172A)
private val EvTextMid    = Color(0xFF374151)
private val EvTextLo     = Color(0xFF8896A8)
private val EvError      = Color(0xFFEF4444)
private val EvSuccess    = Color(0xFF16A34A)
private val EvAmber      = Color(0xFFD97706)

private val EvBgBrush       = Brush.verticalGradient(listOf(EvBgTop, EvBgMid, EvBgBot))
private val EvAccentBrush   = Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166)))
private val EvBorderBrush   = Brush.linearGradient(listOf(EvBorderHi, EvBorderLo))
private val EvShadowAmbient = Color(0x22000000)
private val EvShadowSpot    = Color(0x2E000000)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    onBack: () -> Unit,
    onReadBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val createdEvents     by manager.boxCreatedEvents.observeAsState(emptyList())
    val openedEvents      by manager.boxOpenedEvents.observeAsState(emptyList())
    val pendingContracts  by manager.pendingContracts.observeAsState(emptyList())
    val isConnected        = manager.isConnected.observeAsState(false).value
    val errorMessage      by manager.errorMessage.observeAsState("")
    val transactionStatus by manager.transactionStatus.observeAsState("")

    val context          = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotBlank()) {
            snackbarHostState.showSnackbar(message = errorMessage, duration = SnackbarDuration.Long)
        }
    }

    val openedEventIds = remember(openedEvents) { openedEvents.map { it.id }.toSet() }

    var currentTimeSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var isLoading          by remember { mutableStateOf(false) }
    val scope              = rememberCoroutineScope()
    var hasLoadedInitially by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (!hasLoadedInitially && isConnected) {
            isLoading = true
            manager.fetchBoxCreatedEventsAsync()
            isLoading = false
            hasLoadedInitially = true
        } else if (!hasLoadedInitially && !isConnected) {
            hasLoadedInitially = true
        }
    }

    LaunchedEffect(Unit) {
        if (pendingContracts.isNotEmpty() && isConnected) {
            delay(2000)
            manager.fetchBoxCreatedEventsAsync()
        }
    }

    val previousPendingCount = remember { mutableStateOf(pendingContracts.size) }
    LaunchedEffect(pendingContracts.size) {
        if (pendingContracts.size < previousPendingCount.value && isConnected) {
            Timber.d("📊 Pending контракт подтвержден, обновляем список событий")
            delay(1000)
            manager.fetchBoxCreatedEventsAsync()
        }
        previousPendingCount.value = pendingContracts.size
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeSeconds = System.currentTimeMillis() / 1000
        }
    }

    val sortedCreatedEvents = remember(createdEvents) { createdEvents }
    val openingBoxId        by manager.openingBoxId.observeAsState(null)
    var selectedTabIndex    by remember { mutableStateOf(0) }

    fun getEventStatus(event: SolanaManager.BoxCreatedEvent, isOpened: Boolean, currentTime: Long): String {
        val savedStatus = BoxMetadataStore.getStatus(context, event.id)
        val isExpired   = event.deadline.toLong() < currentTime && event.deadline.toLong() > 0
        return when {
            event.deadline.toLong() == 0L && event.amount == BigInteger.ZERO -> when (savedStatus) {
                BoxMetadataStore.BoxStatus.WIN  -> "win"
                BoxMetadataStore.BoxStatus.LOSE -> "lose"
                else -> "win"
            }
            savedStatus == BoxMetadataStore.BoxStatus.WIN  -> "win"
            savedStatus == BoxMetadataStore.BoxStatus.LOSE -> "lose"
            isOpened  -> "win"
            isExpired -> "lose"
            else      -> "active"
        }
    }

    fun hasPrivateKey(eventId: String) = VaultManager.getPrivateKey(context, eventId) != null

    val filteredEvents = remember(sortedCreatedEvents, selectedTabIndex, currentTimeSeconds, openedEventIds) {
        val eventsWithStatus = sortedCreatedEvents.map { event ->
            val isOpened = openedEventIds.contains(event.id)
            val status   = getEventStatus(event, isOpened, currentTimeSeconds)
            val hasKey   = hasPrivateKey(event.id)
            Triple(event, status, hasKey)
        }
        when (selectedTabIndex) {
            0    -> eventsWithStatus.filter { it.second == "active" && it.third }.map { it.first }
            1    -> eventsWithStatus.filter { it.second == "win"    && it.third }.map { it.first }
            2    -> eventsWithStatus.filter { it.second == "lose"   && it.third }.map { it.first }
            3    -> eventsWithStatus.filter { !it.third }.map { it.first }
            else -> sortedCreatedEvents
        }
    }

    val hasNoKeyEvents = remember(sortedCreatedEvents) {
        sortedCreatedEvents.any { VaultManager.getPrivateKey(context, it.id) == null }
    }

    val tabs = remember(hasNoKeyEvents) {
        val base = listOf("active", "win", "lose")
        if (hasNoKeyEvents) base + "no key" else base
    }

    LaunchedEffect(tabs.size) {
        if (selectedTabIndex >= tabs.size) selectedTabIndex = 0
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EvBgBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                            .background(EvNavy, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(data.visuals.message, color = Color.White, fontSize = 14.sp)
                    }
                }
            },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFF6F9FE), Color(0xFFEEF3FB), Color.Transparent))
                        )
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                ) {
                    // App bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                                .background(EvSurface, CircleShape)
                                .border(1.dp, EvBorderLo, CircleShape)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "back",
                                tint = EvTextHi,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "contracts",
                            fontFamily = TirtoWritterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = EvTextHi
                        )

                        // Refresh button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(6.dp, CircleShape, ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                                .background(EvSurface, CircleShape)
                                .border(1.dp, EvBorderLo, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    if (isConnected) {
                                        isLoading = true
                                        manager.fetchBoxCreatedEvents()
                                        scope.launch { delay(2000); isLoading = false }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "refresh",
                                tint = EvTextHi,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Tab pill row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = 0.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                            .background(EvSurface, RoundedCornerShape(14.dp))
                            .border(1.dp, EvBorderLo, RoundedCornerShape(14.dp))
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
                                            Modifier.background(EvAccentBrush, RoundedCornerShape(11.dp))
                                        else
                                            Modifier
                                    )
                                    .clickable { selectedTabIndex = index }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontFamily = TirtoWritterFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) Color.White else EvTextLo
                                )
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->

            if (isLoading && isConnected && (selectedTabIndex != 0 || pendingContracts.isEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EvNavy, strokeWidth = 2.5.dp)
                }
            } else if (hasLoadedInitially && filteredEvents.isEmpty() && (selectedTabIndex != 0 || pendingContracts.isEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("No contracts yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EvTextMid)
                        Text("Create a contract to see it here", fontSize = 13.sp, color = EvTextLo)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (selectedTabIndex == 0 && pendingContracts.isNotEmpty()) {
                        items(
                            items = pendingContracts,
                            key = { "pending_${it.id}" },
                            contentType = { "pending_contract" }
                        ) { pending ->
                            PendingContractCard(pending = pending, onReadBook = onReadBook)
                        }
                    }

                    items(
                        items = filteredEvents,
                        key = { it.id },
                        contentType = { "box_event" }
                    ) { event ->
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
                }
            }
        }
    }
}

// ─── Event card ───────────────────────────────────────────────────────────────

@Composable
fun EventItemCreated(
    event: SolanaManager.BoxCreatedEvent,
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    isOpened: Boolean,
    onReadBook: (String) -> Unit,
    openingBoxId: String?,
    currentTimeSeconds: Long
) {
    val context   = LocalContext.current
    val isOpening = openingBoxId == event.id

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
        val tokenSymbol: String?,
        val currentPage: Int,
        val totalPages: Int
    )

    val cachedData = remember(event.id) {
        val bookFile    = FileManager.getBookFile(context, event.id)
        val fileType    = BoxMetadataStore.getFileType(context, event.id)
        val timerParams = TimerContractStore.getTimerParams(context, event.id)
        val hasPrivateKey = VaultManager.getPrivateKey(context, event.id) != null
        val savedAmount = BoxMetadataStore.getAmount(context, event.id)
        val amountToSave = if (savedAmount == null && event.amount != BigInteger.ZERO) {
            BoxMetadataStore.setAmount(context, event.id, event.amount); event.amount
        } else savedAmount
        val tokenDecimals = BoxMetadataStore.getDecimals(context, event.id)
        val tokenSymbol   = BoxMetadataStore.getSymbol(context, event.id)
        Timber.d("📊 EventItem для boxId=${event.id}: tokenDecimals=$tokenDecimals, tokenSymbol=$tokenSymbol")
        CachedEventData(
            hasBookFile = bookFile != null,
            bookTitle   = BoxMetadataStore.getBookTitle(context, event.id)
                ?: bookFile?.let { extractBookTitleFromFile(it) }
                ?: "Box",
            checkpointIndices      = CheckpointIndexStore.getIndices(context, event.id),
            foundCheckpointIndices = CheckpointIndexStore.getFoundIndices(context, event.id).toSet(),
            checkpointLabel        = CheckpointIndexStore.getCheckpointLabel(context, event.id),
            timerParams            = timerParams,
            remainingSeconds       = timerParams?.let { TimerContractStore.getRemainingSeconds(context, event.id) } ?: 0L,
            hasPrivateKey  = hasPrivateKey,
            savedAmount    = amountToSave,
            tokenDecimals  = tokenDecimals,
            tokenSymbol    = tokenSymbol,
            currentPage    = CheckpointIndexStore.getCurrentPage(context, event.id),
            totalPages     = CheckpointIndexStore.getTotalPages(context, event.id)
        )
    }

    val hasBookFile            = cachedData.hasBookFile
    val bookTitle              = cachedData.bookTitle
    val checkpointIndices      = cachedData.checkpointIndices
    val foundCheckpointIndices = cachedData.foundCheckpointIndices
    val checkpointLabel        = cachedData.checkpointLabel
    val timerParams            = cachedData.timerParams
    val remainingSeconds       = cachedData.remainingSeconds

    var showCheckpointTextDialog by remember { mutableStateOf(false) }
    var isLocallyProcessing      by remember { mutableStateOf(false) }

    LaunchedEffect(isOpened, openingBoxId) {
        if (isOpened) isLocallyProcessing = false
        else if (isLocallyProcessing && openingBoxId != event.id) isLocallyProcessing = false
    }

    val isCheckpointTextLong  = checkpointLabel.length > 20
    val displayCheckpointText = if (isCheckpointTextLong) checkpointLabel.take(20) + "…" else checkpointLabel

    val savedStatus = remember(event.id) { BoxMetadataStore.getStatus(context, event.id) }
    val isExpired   = remember(event.deadline, currentTimeSeconds) {
        event.deadline.toLong() < currentTimeSeconds && event.deadline.toLong() > 0
    }

    val status = when {
        event.deadline.toLong() == 0L && event.amount == BigInteger.ZERO -> when (savedStatus) {
            BoxMetadataStore.BoxStatus.WIN  -> "win"
            BoxMetadataStore.BoxStatus.LOSE -> "lose"
            else -> "win"
        }
        savedStatus == BoxMetadataStore.BoxStatus.WIN  -> "win"
        savedStatus == BoxMetadataStore.BoxStatus.LOSE -> "lose"
        isOpened  -> "win"
        isExpired -> "lose"
        else      -> "active"
    }

    // left accent stripe color
    val stripeColor = when (status) {
        "win"  -> EvSuccess
        "lose" -> EvError
        else   -> EvSlate
    }

    // Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
            .background(EvSurface, RoundedCornerShape(20.dp))
            .border(1.dp, EvBorderBrush, RoundedCornerShape(20.dp))
    ) {
        // Left accent stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .align(Alignment.TopStart)
                .padding(top = 20.dp)
                .background(stripeColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: title + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = bookTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EvTextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Status pill
                Box(
                    modifier = Modifier
                        .background(stripeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, stripeColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when (status) { "win" -> "WIN"; "lose" -> "LOSE"; else -> "ACTIVE" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = stripeColor
                    )
                }

                if (!cachedData.hasPrivateKey) {
                    Box(
                        modifier = Modifier
                            .background(EvAmber.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, EvAmber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("⚠ no key", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EvAmber)
                    }
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, EvBorderLo, Color.Transparent)))
            )

            // Data rows
            val displayAmount = if (event.amount == BigInteger.ZERO && cachedData.savedAmount != null)
                cachedData.savedAmount!! else event.amount
            val decimals = cachedData.tokenDecimals ?: 9
            val symbol   = cachedData.tokenSymbol ?: "SOL"
            Timber.d("💰 Отображение депозита для boxId=${event.id}: decimals=$decimals, symbol=$symbol, amount=$displayAmount")

            EvRow("Deposit", "${formatUnits(displayAmount, decimals)} $symbol")

            if (status == "active") {
                val remainingTime = remember(event.deadline, currentTimeSeconds) {
                    val s = event.deadline.toLong() - currentTimeSeconds
                    if (s <= 0) "EXPIRED" else formatRemainingTime(s)
                }
                EvRow("Deadline", remainingTime)
            }

            if (timerParams == null) {
                EvRowCheckpoints("Checkpoints", checkpointIndices, foundCheckpointIndices)
                if (isCheckpointTextLong)
                    EvRowClickable("Checkpoint text", displayCheckpointText) { showCheckpointTextDialog = true }
                else
                    EvRow("Checkpoint text", checkpointLabel)
            }

            if (timerParams != null) {
                val safe    = remainingSeconds.coerceAtLeast(0L)
                val h = safe / 3600; val m = (safe % 3600) / 60; val s = safe % 60
                EvRow("Time", String.format("%02d:%02d:%02d", h, m, s))
                EvRow("Swipe Control", if (timerParams.swipeControl) "✓" else "✗")
                EvRow("Hand Control",  if (timerParams.handControl)  "✓" else "✗")
            }

            // Action buttons
            val allCheckpointsFound = checkpointIndices.size == 3 && foundCheckpointIndices.size == checkpointIndices.size
            val isTimerReady        = timerParams != null && remainingSeconds == 0L
            val canOpenBox          = if (timerParams != null) isTimerReady else allCheckpointsFound
            val isTrulyProcessing   = isLocallyProcessing || isOpening
            val mintAddress         = remember(event.id) { BoxMetadataStore.getMint(context, event.id) }
            val isTokenContract     = mintAddress != null

            if (status == "active" && canOpenBox) {
                if (cachedData.hasPrivateKey) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (!isTrulyProcessing) EvAccentBrush else Brush.linearGradient(listOf(EvSurfaceLo, EvSurfaceLo)), RoundedCornerShape(14.dp))
                            .border(1.dp, Brush.linearGradient(listOf(EvBorderHi, EvBorderLo)), RoundedCornerShape(14.dp))
                            .clickable(enabled = !isTrulyProcessing) {
                                if (!isLocallyProcessing) {
                                    isLocallyProcessing = true
                                    Timber.d("🔘 Return deposit нажата: boxId=${event.id}")
                                    if (isTokenContract) manager.openBoxToken(context, event.id, activityResultSender)
                                    else manager.openBox(context, event.id, activityResultSender)
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTrulyProcessing)
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EvSlate)
                        else
                            Text("Return deposit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EvSurfaceLo, RoundedCornerShape(14.dp))
                            .border(1.dp, EvBorderLo, RoundedCornerShape(14.dp))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Return deposit (no key)", color = EvTextLo, fontSize = 14.sp)
                    }
                }
            }

            if (hasBookFile) {
                if (cachedData.totalPages > 0) {
                    val readProgress = (cachedData.currentPage + 1).toFloat() / cachedData.totalPages.toFloat()
                    EvRow(
                        label = "Progress",
                        value = "${cachedData.currentPage + 1} / ${cachedData.totalPages} pages"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(EvSurfaceLo, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(readProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(EvSlate, EvNavy)),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                        .background(EvSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, EvBorderLo, RoundedCornerShape(14.dp))
                        .clickable { onReadBook(event.id) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Read", color = EvTextMid, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showCheckpointTextDialog) {
        EvDialog(
            title = "Checkpoint text",
            body  = checkpointLabel,
            onDismiss = { showCheckpointTextDialog = false }
        )
    }
}

// ─── Pending contract card ────────────────────────────────────────────────────

@Composable
fun PendingContractCard(
    pending: SolanaManager.PendingContract,
    onReadBook: (String) -> Unit
) {
    val context = LocalContext.current

    data class CachedPendingData(
        val hasBookFile: Boolean,
        val bookTitle: String,
        val tokenDecimals: Int,
        val tokenSymbol: String,
        val checkpointIndices: List<Int>,
        val foundCheckpointIndices: Set<Int>,
        val checkpointLabel: String,
        val timerParams: TimerContractStore.TimerParams?,
        val remainingSeconds: Long,
        val currentPage: Int,
        val totalPages: Int
    )

    val cachedData = remember(pending.id) {
        val bookFile    = FileManager.getBookFile(context, pending.id)
        val fileType    = BoxMetadataStore.getFileType(context, pending.id)
        val timerParams = TimerContractStore.getTimerParams(context, pending.id)
        CachedPendingData(
            hasBookFile   = bookFile != null,
            bookTitle     = BoxMetadataStore.getBookTitle(context, pending.id)
                ?: bookFile?.let { extractBookTitleFromFile(it) }
                ?: "Box",
            tokenDecimals = BoxMetadataStore.getDecimals(context, pending.id) ?: 9,
            tokenSymbol   = BoxMetadataStore.getSymbol(context, pending.id) ?: "SOL",
            checkpointIndices      = CheckpointIndexStore.getIndices(context, pending.id),
            foundCheckpointIndices = CheckpointIndexStore.getFoundIndices(context, pending.id).toSet(),
            checkpointLabel        = CheckpointIndexStore.getCheckpointLabel(context, pending.id),
            timerParams            = timerParams,
            remainingSeconds       = timerParams?.let { TimerContractStore.getRemainingSeconds(context, pending.id) } ?: 0L,
            currentPage            = CheckpointIndexStore.getCurrentPage(context, pending.id),
            totalPages             = CheckpointIndexStore.getTotalPages(context, pending.id)
        )
    }

    val isCheckpointTextLong  = cachedData.checkpointLabel.length > 20
    val displayCheckpointText = if (isCheckpointTextLong) cachedData.checkpointLabel.take(20) + "…" else cachedData.checkpointLabel
    var showCheckpointTextDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
            .background(EvSurface, RoundedCornerShape(20.dp))
            .border(1.dp, EvBorderBrush, RoundedCornerShape(20.dp))
    ) {
        // Amber pending stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .align(Alignment.TopStart)
                .padding(top = 20.dp)
                .background(EvAmber, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = cachedData.bookTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EvTextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // PENDING badge
                Box(
                    modifier = Modifier
                        .background(EvAmber.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, EvAmber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("PENDING", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = EvAmber)
                }
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = EvAmber)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, EvBorderLo, Color.Transparent)))
            )

            val formattedAmount = formatUnits(pending.amount, cachedData.tokenDecimals)
            EvRow("Deposit", "$formattedAmount ${cachedData.tokenSymbol}")

            if (pending.deadline.toLong() > 0)
                EvRow("Deadline", "~${pending.deadline} days")

            if (cachedData.timerParams == null && cachedData.checkpointIndices.isNotEmpty()) {
                EvRowCheckpoints("Checkpoints", cachedData.checkpointIndices, cachedData.foundCheckpointIndices)
                if (isCheckpointTextLong)
                    EvRowClickable("Checkpoint text", displayCheckpointText) { showCheckpointTextDialog = true }
                else
                    EvRow("Checkpoint text", cachedData.checkpointLabel)
            }

            if (cachedData.timerParams != null) {
                val safe = cachedData.remainingSeconds.coerceAtLeast(0L)
                val h = safe / 3600; val m = (safe % 3600) / 60; val s = safe % 60
                EvRow("Time", String.format("%02d:%02d:%02d", h, m, s))
                EvRow("Swipe Control", if (cachedData.timerParams.swipeControl) "✓" else "✗")
                EvRow("Hand Control",  if (cachedData.timerParams.handControl)  "✓" else "✗")
            }

            Text(
                text = "Waiting for transaction confirmation…",
                fontSize = 12.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = EvTextLo
            )

            if (pending.txHash != null) {
                Text(
                    text = "TX: ${pending.txHash}",
                    fontSize = 10.sp,
                    color = EvTextLo,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (cachedData.hasBookFile) {
                if (cachedData.totalPages > 0) {
                    val readProgress = (cachedData.currentPage + 1).toFloat() / cachedData.totalPages.toFloat()
                    EvRow(
                        label = "Progress",
                        value = "${cachedData.currentPage + 1} / ${cachedData.totalPages} pages"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(EvSurfaceLo, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(readProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(EvSlate, EvNavy)),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                        .background(EvSurface, RoundedCornerShape(14.dp))
                        .border(1.dp, EvBorderLo, RoundedCornerShape(14.dp))
                        .clickable { onReadBook(pending.id) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Read", color = EvTextMid, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showCheckpointTextDialog) {
        EvDialog(
            title = "Checkpoint text",
            body  = cachedData.checkpointLabel,
            onDismiss = { showCheckpointTextDialog = false }
        )
    }
}

// ─── Shared dialog ────────────────────────────────────────────────────────────

@Composable
private fun EvDialog(title: String, body: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .wrapContentHeight()
                .shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                .background(EvSurface, RoundedCornerShape(24.dp))
                .border(1.dp, EvBorderBrush, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EvTextHi)
                Text(body,  fontSize = 15.sp, color = EvTextMid)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(12.dp), ambientColor = EvShadowAmbient, spotColor = EvShadowSpot)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EvAccentBrush, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ─── Row helpers ──────────────────────────────────────────────────────────────

@Composable
fun EvRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = EvTextLo, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = EvTextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EvRowClickable(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = EvTextLo, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = EvNavy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EvRowCheckpoints(label: String, checkpointIndices: List<Int>, foundCheckpointIndices: Set<Int>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = EvTextLo, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            checkpointIndices.forEach { index ->
                val found = index in foundCheckpointIndices
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (found) EvSuccess else EvSurfaceLo,
                            CircleShape
                        )
                        .border(1.5.dp, if (found) EvSuccess else EvBorderLo, CircleShape)
                )
            }
        }
    }
}

// Keep legacy aliases so the old RPC debug code still compiles
@Composable
fun EventRow(label: String, value: String) = EvRow(label, value)
@Composable
fun EventRowClickable(label: String, value: String, onClick: () -> Unit) = EvRowClickable(label, value, onClick)
@Composable
fun EventRowWithCheckpoints(label: String, checkpointIndices: List<Int>, foundCheckpointIndices: Set<Int>) =
    EvRowCheckpoints(label, checkpointIndices, foundCheckpointIndices)

@Composable
fun EventRowReadable(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = EvTextLo, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = EvTextHi,
            modifier = Modifier.weight(2f), textAlign = TextAlign.End)
    }
}

// ─── Pure helpers (unchanged) ─────────────────────────────────────────────────

private fun formatUnits(value: BigInteger, decimals: Int): String {
    return try {
        if (value.signum() == 0) "0"
        else {
            val bd = BigDecimal(value).movePointLeft(decimals)
            val result = bd.setScale(decimals, RoundingMode.DOWN).toPlainString()
            if (result == "NaN" || result.contains("Infinity")) "0" else result
        }
    } catch (e: Exception) { "0" }
}

private fun formatDate(timestamp: BigInteger): String {
    return try {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp.toLong() * 1000L))
    } catch (e: Exception) { timestamp.toString() }
}

private fun formatRemainingTime(seconds: Long): String {
    if (seconds <= 0) return "EXPIRED"
    val days    = seconds / 86400
    val hours   = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs    = seconds % 60
    return String.format("%d:%02d:%02d:%02d", days, hours, minutes, secs)
}

private fun parseRpcResponseInfo(jsonString: String): RpcResponseInfo? {
    return try {
        val json          = JSONObject(jsonString)
        val method        = json.optString("method", "unknown")
        val result        = json.optJSONObject("result")
        val error         = json.optJSONObject("error")
        val accountsCount = result?.optJSONArray("value")?.length() ?: 0
        RpcResponseInfo(method = method, accountsCount = accountsCount, error = error?.optString("message"))
    } catch (e: Exception) { null }
}

private data class RpcResponseInfo(val method: String, val accountsCount: Int, val error: String?)

private fun extractBookTitleFromFile(file: java.io.File): String {
    if (file.extension.equals("pdf", ignoreCase = true)) {
        return "PDF файл"
    }
    return try {
        FileInputStream(file).use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.contains("content.opf", ignoreCase = true) ||
                        entry.name.contains("metadata.opf", ignoreCase = true) ||
                        entry.name.endsWith(".opf", ignoreCase = true)) {
                        val title = Jsoup.parse(zip.bufferedReader().readText())
                            .select("dc|title, title").first()?.text()?.trim()
                        if (!title.isNullOrBlank()) return title
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            }
        }
        "EPUB файл"
    } catch (e: Exception) { "EPUB файл" }
}
