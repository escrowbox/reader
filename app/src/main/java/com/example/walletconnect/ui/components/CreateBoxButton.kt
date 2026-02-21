package com.example.walletconnect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletconnect.SolanaManager
import com.example.walletconnect.ui.theme.NeumorphicBackground
import com.example.walletconnect.ui.theme.NeumorphicText
import com.example.walletconnect.utils.BoxMetadataStore
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.math.BigInteger
import timber.log.Timber

/**
 * Кнопка создания бокса с управлением состоянием транзакции
 * После нажатия становится неактивной до выхода с экрана
 * 
 * Работает с Solana программой escrow через Mobile Wallet Adapter
 * Поддерживает как SOL, так и SPL токены
 */
@Composable
fun CreateBoxButton(
    contract: SolanaManager,
    activityResultSender: ActivityResultSender,
    id: String,
    deadline: Int,
    amount: Long,
    modifier: Modifier = Modifier,
    isFormValid: Boolean = true,
    isTokenBox: Boolean = false,
    mintAddress: String? = null,
    tokenDecimals: Int? = null,
    tokenSymbol: String? = null,
    onShowValidationError: (() -> Unit)? = null,
    onTransactionSent: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Локальное состояние - была ли нажата кнопка
    var wasClicked by remember { mutableStateOf(false) }
    
    val accentGradient = Brush.linearGradient(listOf(Color(0xFF1E2D3D), Color(0xFF3D5166)))
    val disabledGradient = Brush.linearGradient(listOf(Color(0xFFCDD5E2), Color(0xFFCDD5E2)))
    val glowColor = if (!wasClicked) Color(0xFF1E2D3D).copy(alpha = 0.28f) else Color.Transparent

    Box(
        modifier = modifier
            .height(56.dp)
            .shadow(
                elevation = if (!wasClicked) 16.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = glowColor,
                spotColor = Color(0xFF06B6D4).copy(alpha = 0.25f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (!wasClicked) accentGradient else disabledGradient,
                RoundedCornerShape(16.dp)
            )
            .then(
                if (!wasClicked)
                    Modifier.border(
                        1.dp,
                        Brush.linearGradient(listOf(Color(0x60FFFFFF), Color(0x20FFFFFF))),
                        RoundedCornerShape(16.dp)
                    )
                else
                    Modifier.border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
            )
            .clickable(enabled = !wasClicked) {
                if (!isFormValid) {
                    onShowValidationError?.invoke()
                    return@clickable
                }
                if (isTokenBox && mintAddress.isNullOrBlank()) {
                    onShowValidationError?.invoke()
                    return@clickable
                }
                wasClicked = true

                if (isTokenBox) {
                    Timber.d("🔘 Создание token контракта: id=${id.take(20)}..., deadline=$deadline days, amount=$amount, mint=${mintAddress?.take(20)}...")
                } else {
                    Timber.d("🔘 Создание SOL контракта: id=${id.take(20)}..., deadline=$deadline days, amount=$amount lamports")
                }

                if (isTokenBox && !mintAddress.isNullOrBlank()) {
                    BoxMetadataStore.setIsToken(context, id, true)
                    BoxMetadataStore.setMint(context, id, mintAddress)
                    tokenDecimals?.let { BoxMetadataStore.setDecimals(context, id, it) }
                    tokenSymbol?.let { BoxMetadataStore.setSymbol(context, id, it) }
                    Timber.d("💾 Сохранены метаданные токена для pending: decimals=$tokenDecimals, symbol=$tokenSymbol, mint=${mintAddress.take(20)}...")
                }

                contract.addPendingContractSync(id, deadline, amount.toBigInteger())
                onTransactionSent?.invoke()

                if (isTokenBox && !mintAddress.isNullOrBlank()) {
                    contract.sendCreateBoxTokenWithStatus(
                        id = id,
                        deadlineDays = deadline,
                        amount = amount.toBigInteger(),
                        mintAddress = mintAddress,
                        sender = activityResultSender,
                        decimals = tokenDecimals,
                        symbol = tokenSymbol
                    )
                } else {
                    contract.sendCreateBoxWithStatus(
                        id = id,
                        deadlineDays = deadline,
                        amount = amount.toBigInteger(),
                        sender = activityResultSender
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (wasClicked) "Sent…" else "Create contract",
            color = if (!wasClicked) Color.White else Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp
        )
    }
}
