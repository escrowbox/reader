package com.example.walletconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletconnect.SolanaManager
import com.example.walletconnect.ui.hooks.TxStatus
import com.example.walletconnect.ui.theme.NeumorphicBackground
import com.example.walletconnect.ui.theme.NeumorphicText
import com.example.walletconnect.ui.theme.NeumorphicTextSecondary
import com.example.walletconnect.ui.theme.TirtoWritterFontFamily
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

/**
 * Экран для отображения просроченных боксов и их sweep (забора средств authority)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SweepBoxesScreen(
    manager: SolanaManager,
    activityResultSender: ActivityResultSender,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expiredBoxes by manager.expiredBoxes.observeAsState(emptyList())
    val isLoading by manager.sweepLoading.observeAsState(false)
    val txStatus by manager.txStatusFlow.collectAsStateWithLifecycle()
    val errorMessage by manager.errorMessage.observeAsState("")
    val programStateExists by manager.programStateExists.observeAsState(null)

    var sweepingBoxPubkey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        manager.checkProgramStateExists()
        manager.fetchAllExpiredBoxes()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeumorphicBackground)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "back",
                                tint = NeumorphicText
                            )
                        }

                        Text(
                            text = "Sweep Boxes",
                            fontFamily = TirtoWritterFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeumorphicText,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )

                        IconButton(
                            onClick = { manager.fetchAllExpiredBoxes() },
                            enabled = !isLoading
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "refresh",
                                tint = if (isLoading) NeumorphicTextSecondary else NeumorphicText
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Transaction status bar
                val statusText = when (txStatus) {
                    TxStatus.IDLE -> null
                    TxStatus.SIGNING -> "Signing transaction..."
                    TxStatus.MINING -> "Confirming transaction..."
                    TxStatus.SUCCESS -> "Sweep successful!"
                    TxStatus.ERROR -> "Transaction failed"
                }

                if (statusText != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (txStatus) {
                                TxStatus.SUCCESS -> Color(0xFF1B5E20).copy(alpha = 0.15f)
                                TxStatus.ERROR -> Color(0xFFB71C1C).copy(alpha = 0.15f)
                                else -> NeumorphicBackground
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            if (txStatus == TxStatus.SIGNING || txStatus == TxStatus.MINING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = NeumorphicText
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = NeumorphicText
                            )
                        }
                    }
                }

                // Initialize card - shown when program state doesn't exist
                if (programStateExists == false) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE65100).copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                                spotColor = Color.White.copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Program not initialized",
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "You need to call Initialize first. This will set your connected wallet as the program authority for sweep operations.",
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 13.sp,
                                color = NeumorphicText,
                                lineHeight = 18.sp
                            )
                            Button(
                                onClick = {
                                    manager.sendInitializeWithStatus(activityResultSender)
                                },
                                enabled = txStatus == TxStatus.IDLE || txStatus == TxStatus.SUCCESS || txStatus == TxStatus.ERROR,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                                        spotColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE65100),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFDCDCDC),
                                    disabledContentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Initialize Program",
                                    fontFamily = TirtoWritterFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NeumorphicText)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading expired boxes...",
                                    fontFamily = TirtoWritterFontFamily,
                                    color = NeumorphicTextSecondary
                                )
                            }
                        }
                    }
                    expiredBoxes.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No expired boxes found",
                                fontFamily = TirtoWritterFontFamily,
                                fontSize = 16.sp,
                                color = NeumorphicTextSecondary
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "${expiredBoxes.size} expired box(es)",
                            fontFamily = TirtoWritterFontFamily,
                            fontSize = 14.sp,
                            color = NeumorphicTextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(expiredBoxes, key = { it.pubkey }) { box ->
                                ExpiredBoxCard(
                                    box = box,
                                    isSweeping = sweepingBoxPubkey == box.pubkey && (txStatus == TxStatus.SIGNING || txStatus == TxStatus.MINING),
                                    onSweep = {
                                        sweepingBoxPubkey = box.pubkey
                                        if (box.isToken && box.mint != null) {
                                            manager.sendSweepBoxTokenWithStatus(
                                                boxPubkey = box.pubkey,
                                                mintAddress = box.mint,
                                                sender = activityResultSender
                                            )
                                        } else {
                                            manager.sendSweepBoxWithStatus(
                                                boxPubkey = box.pubkey,
                                                sender = activityResultSender
                                            )
                                        }
                                    }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiredBoxCard(
    box: SolanaManager.ExpiredBox,
    isSweeping: Boolean,
    onSweep: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = NeumorphicBackground),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                spotColor = Color.White.copy(alpha = 0.6f)
            ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: type badge + PDA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (box.isToken) Color(0xFF1565C0).copy(alpha = 0.15f)
                            else Color(0xFF2E7D32).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (box.isToken) "TOKEN" else "SOL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (box.isToken) Color(0xFF1565C0) else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = Color(0xFFB71C1C).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "EXPIRED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = NeumorphicTextSecondary.copy(alpha = 0.2f))

            // Box PDA
            InfoRow(label = "Box PDA", value = shortenAddress(box.pubkey))

            // Sender
            InfoRow(label = "Sender", value = shortenAddress(box.sender))

            // ID
            InfoRow(label = "ID", value = shortenAddress(box.id))

            // Deadline
            InfoRow(
                label = "Deadline",
                value = dateFormat.format(Date(box.deadline * 1000))
            )

            // Amount
            val amountDisplay = if (box.isToken) {
                "${box.amount} raw units"
            } else {
                val sol = BigDecimal(box.amount).divide(
                    BigDecimal(1_000_000_000L), 6, RoundingMode.DOWN
                )
                "${sol.stripTrailingZeros().toPlainString()} SOL"
            }
            InfoRow(label = "Amount", value = amountDisplay)

            // Mint (for token boxes)
            if (box.isToken && box.mint != null) {
                InfoRow(label = "Mint", value = shortenAddress(box.mint))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sweep button
            Button(
                onClick = onSweep,
                enabled = !isSweeping,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                        spotColor = Color.White.copy(alpha = 0.6f)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFDCDCDC),
                    disabledContentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSweeping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sweeping...",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = if (box.isToken) "Sweep Token Box" else "Sweep SOL Box",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = NeumorphicTextSecondary,
            fontFamily = TirtoWritterFontFamily,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = NeumorphicText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun shortenAddress(address: String): String {
    return if (address.length > 12) {
        "${address.take(6)}...${address.takeLast(4)}"
    } else address
}
