package com.example.walletconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletconnect.SolanaManager
import com.example.walletconnect.ui.hooks.TxStatus
import com.example.walletconnect.ui.theme.TirtoWritterFontFamily
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

// ─── Design tokens ────────────────────────────────────────────────────────────

private val SwBgTop      = Color(0xFFDDE3EE)
private val SwBgMid      = Color(0xFFD5DCE9)
private val SwBgBot      = Color(0xFFCBD4E3)
private val SwSurface    = Color(0xFFEDF1F8)
private val SwSurfaceLo  = Color(0xFFE8EDF5)
private val SwBorderHi   = Color(0xFFF4F7FC)
private val SwBorderLo   = Color(0xFFBDCADB)
private val SwNavy       = Color(0xFF2D3A4F)
private val SwSlate      = Color(0xFF4B6080)
private val SwTextHi     = Color(0xFF0F172A)
private val SwTextMid    = Color(0xFF374151)
private val SwTextLo     = Color(0xFF8896A8)
private val SwError      = Color(0xFFDC2626)
private val SwSuccess    = Color(0xFF16A34A)
private val SwAmber      = Color(0xFFD97706)
private val SwBlue       = Color(0xFF1D6FBB)

private val SwBgBrush       = Brush.verticalGradient(listOf(SwBgTop, SwBgMid, SwBgBot))
private val SwAccentBrush   = Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166)))
private val SwBorderBrush   = Brush.linearGradient(listOf(SwBorderHi, SwBorderLo))
private val SwShadowAmbient = Color(0x22000000)
private val SwShadowSpot    = Color(0x2E000000)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SweepBoxesScreen(
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expiredBoxes      by manager.expiredBoxes.observeAsState(emptyList())
    val isLoading         by manager.sweepLoading.observeAsState(false)
    val txStatus          by manager.txStatusFlow.collectAsStateWithLifecycle()
    val errorMessage      by manager.errorMessage.observeAsState("")
    val programStateExists by manager.programStateExists.observeAsState(null)

    var sweepingBoxPubkey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        manager.checkProgramStateExists()
        manager.fetchAllExpiredBoxes()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SwBgBrush)
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
                                listOf(Color(0xFFDDE3EE), Color(0xF2D5DCE9), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                ) {
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
                                .shadow(6.dp, CircleShape, ambientColor = SwShadowAmbient, spotColor = SwShadowSpot)
                                .background(SwSurface, CircleShape)
                                .border(1.dp, SwBorderLo, CircleShape)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "back",
                                tint = SwTextHi,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "Sweep Boxes",
                            fontFamily = TirtoWritterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = SwTextHi
                        )

                        // Refresh button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(if (!isLoading) 6.dp else 2.dp, CircleShape, ambientColor = SwShadowAmbient, spotColor = SwShadowSpot)
                                .background(SwSurface, CircleShape)
                                .border(1.dp, SwBorderLo, CircleShape)
                                .clip(CircleShape)
                                .clickable(enabled = !isLoading) { manager.fetchAllExpiredBoxes() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = SwSlate
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "refresh",
                                    tint = SwTextHi,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                // ── Transaction status ────────────────────────────────────────
                val isSuccess = txStatus == TxStatus.SUCCESS
                val isError   = txStatus == TxStatus.ERROR
                val statusText = when (txStatus) {
                    TxStatus.IDLE    -> null
                    TxStatus.SIGNING -> "Signing transaction…"
                    TxStatus.MINING  -> "Confirming transaction…"
                    TxStatus.SUCCESS -> "Sweep successful!"
                    TxStatus.ERROR   -> "Transaction failed"
                }

                if (statusText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = SwShadowAmbient, spotColor = SwShadowSpot)
                            .background(
                                when {
                                    isSuccess -> SwSuccess.copy(alpha = 0.1f)
                                    isError   -> SwError.copy(alpha = 0.1f)
                                    else      -> SwSurface
                                },
                                RoundedCornerShape(14.dp)
                            )
                            .border(
                                1.dp,
                                when {
                                    isSuccess -> SwSuccess.copy(alpha = 0.35f)
                                    isError   -> SwError.copy(alpha = 0.35f)
                                    else      -> SwBorderLo
                                },
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when {
                                isSuccess -> Text("✓", color = SwSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                isError   -> Text("✕", color = SwError,   fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                else -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SwSlate)
                            }
                            Text(
                                text = statusText,
                                fontSize = 13.sp,
                                color = when { isSuccess -> SwSuccess; isError -> SwError; else -> SwTextHi }
                            )
                        }
                    }
                }

                // ── Init card ─────────────────────────────────────────────────
                if (programStateExists == false) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = SwShadowAmbient, spotColor = SwShadowSpot)
                            .background(SwAmber.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .border(
                                1.dp,
                                Brush.linearGradient(listOf(SwAmber.copy(alpha = 0.4f), SwAmber.copy(alpha = 0.15f))),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        // Left stripe
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(52.dp)
                                .align(Alignment.TopStart)
                                .padding(top = 20.dp)
                                .background(SwAmber, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(SwAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .border(1.dp, SwAmber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("NOT INITIALIZED", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = SwAmber)
                                }
                            }
                            Text(
                                text = "Program not initialized",
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SwAmber
                            )
                            Text(
                                text = "You need to call Initialize first. This will set your connected wallet as the program authority for sweep operations.",
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 13.sp,
                                color = SwTextMid,
                                lineHeight = 19.sp
                            )
                            val canInit = txStatus == TxStatus.IDLE || txStatus == TxStatus.SUCCESS || txStatus == TxStatus.ERROR
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = SwShadowAmbient, spotColor = SwShadowSpot)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (canInit)
                                            Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFFD97706)))
                                        else
                                            Brush.linearGradient(listOf(SwSurfaceLo, SwSurfaceLo)),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.dp,
                                        Brush.linearGradient(listOf(SwBorderHi, SwBorderLo)),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable(enabled = canInit) {
                                        manager.sendInitializeWithStatus(activityResultSender)
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Initialize Program",
                                    fontFamily = TirtoWritterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (canInit) Color.White else SwTextLo
                                )
                            }
                        }
                    }
                }

                // ── Main content ──────────────────────────────────────────────
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = SwNavy, strokeWidth = 2.5.dp)
                                Text(
                                    text = "Loading expired boxes…",
                                    fontFamily = TirtoWritterFontFamily,
                                    fontSize = 14.sp,
                                    color = SwTextLo
                                )
                            }
                        }
                    }

                    expiredBoxes.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("No expired boxes found", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SwTextMid)
                                Text("All boxes are within their deadlines", fontSize = 13.sp, color = SwTextLo)
                            }
                        }
                    }

                    else -> {
                        // Count badge
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(SwError.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .border(1.dp, SwError.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${expiredBoxes.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SwError
                                )
                            }
                            Text(
                                text = "expired box${if (expiredBoxes.size != 1) "es" else ""}",
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 13.sp,
                                color = SwTextLo
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(expiredBoxes, key = { it.pubkey }) { box ->
                                ExpiredBoxCard(
                                    box = box,
                                    isSweeping = sweepingBoxPubkey == box.pubkey &&
                                            (txStatus == TxStatus.SIGNING || txStatus == TxStatus.MINING),
                                    onSweep = {
                                        sweepingBoxPubkey = box.pubkey
                                        if (box.isToken && box.mint != null) {
                                            manager.sendSweepBoxTokenWithStatus(
                                                boxPubkey  = box.pubkey,
                                                mintAddress = box.mint,
                                                sender     = activityResultSender
                                            )
                                        } else {
                                            manager.sendSweepBoxWithStatus(
                                                boxPubkey = box.pubkey,
                                                sender    = activityResultSender
                                            )
                                        }
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ─── Expired box card ─────────────────────────────────────────────────────────

@Composable
private fun ExpiredBoxCard(
    box: SolanaManager.ExpiredBox,
    isSweeping: Boolean,
    onSweep: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = SwShadowAmbient, spotColor = SwShadowSpot)
            .background(SwSurface, RoundedCornerShape(20.dp))
            .border(1.dp, SwBorderBrush, RoundedCornerShape(20.dp))
    ) {
        // Red expired stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp)
                .align(Alignment.TopStart)
                .padding(top = 20.dp)
                .background(SwError, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SOL / TOKEN badge
                Box(
                    modifier = Modifier
                        .background(
                            if (box.isToken) SwBlue.copy(alpha = 0.12f) else SwSuccess.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            if (box.isToken) SwBlue.copy(alpha = 0.35f) else SwSuccess.copy(alpha = 0.35f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (box.isToken) "TOKEN" else "SOL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = if (box.isToken) SwBlue else SwSuccess
                    )
                }

                // EXPIRED badge
                Box(
                    modifier = Modifier
                        .background(SwError.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, SwError.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "EXPIRED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SwError
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, SwBorderLo, Color.Transparent)))
            )

            // Info rows
            SwInfoRow("Box PDA",  shortenAddress(box.pubkey))
            SwInfoRow("Sender",   shortenAddress(box.sender))
            SwInfoRow("ID",       shortenAddress(box.id))
            SwInfoRow("Deadline", dateFormat.format(Date(box.deadline * 1000)))

            val amountDisplay = if (box.isToken) {
                "${box.amount} raw units"
            } else {
                val sol = BigDecimal(box.amount).divide(BigDecimal(1_000_000_000L), 6, RoundingMode.DOWN)
                "${sol.stripTrailingZeros().toPlainString()} SOL"
            }
            SwInfoRow("Amount", amountDisplay)

            if (box.isToken && box.mint != null) {
                SwInfoRow("Mint", shortenAddress(box.mint))
            }

            // Sweep button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        if (!isSweeping) 6.dp else 2.dp,
                        RoundedCornerShape(14.dp),
                        ambientColor = SwShadowAmbient,
                        spotColor    = SwShadowSpot
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (!isSweeping) SwAccentBrush
                        else Brush.linearGradient(listOf(SwSurfaceLo, SwSurfaceLo)),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(SwBorderHi, SwBorderLo)),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(enabled = !isSweeping, onClick = onSweep)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSweeping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SwSlate)
                        Text("Sweeping…", color = SwTextLo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                } else {
                    Text(
                        text = if (box.isToken) "Sweep Token Box" else "Sweep SOL Box",
                        fontFamily = TirtoWritterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ─── Info row ─────────────────────────────────────────────────────────────────

@Composable
private fun SwInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = SwTextLo,
            letterSpacing = 0.3.sp,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = SwTextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// Keep legacy alias
@Composable
private fun InfoRow(label: String, value: String) = SwInfoRow(label, value)

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun shortenAddress(address: String): String =
    if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address
