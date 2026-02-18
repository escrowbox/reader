package com.example.walletconnect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    
    Button(
        onClick = {
            // Проверяем валидность формы
            if (!isFormValid) {
                // Показываем модалку с ошибкой
                onShowValidationError?.invoke()
                return@Button
            }
            
            // Для token box проверяем mint address
            if (isTokenBox && mintAddress.isNullOrBlank()) {
                onShowValidationError?.invoke()
                return@Button
            }
            
            // Если форма валидна и кнопка еще не была нажата
            if (!wasClicked) {
                wasClicked = true
                
                if (isTokenBox) {
                    Timber.d("🔘 Создание token контракта: id=${id.take(20)}..., deadline=$deadline days, amount=$amount, mint=${mintAddress?.take(20)}...")
                } else {
                    Timber.d("🔘 Создание SOL контракта: id=${id.take(20)}..., deadline=$deadline days, amount=$amount lamports")
                }
                
                // Сохраняем метаданные токена ДО создания pending контракта,
                // чтобы PendingContractCard мог правильно отобразить символ и decimals
                if (isTokenBox && !mintAddress.isNullOrBlank()) {
                    BoxMetadataStore.setIsToken(context, id, true)
                    BoxMetadataStore.setMint(context, id, mintAddress)
                    tokenDecimals?.let { BoxMetadataStore.setDecimals(context, id, it) }
                    tokenSymbol?.let { BoxMetadataStore.setSymbol(context, id, it) }
                    Timber.d("💾 Сохранены метаданные токена для pending: decimals=$tokenDecimals, symbol=$tokenSymbol, mint=${mintAddress.take(20)}...")
                }
                
                // Сначала добавляем pending контракт синхронно
                contract.addPendingContractSync(id, deadline, amount.toBigInteger())
                
                // Вызываем callback для сохранения параметров контракта (checkpoints или timer)
                onTransactionSent?.invoke()
                
                // Отправляем транзакцию через Solana
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
            }
        },
        enabled = !wasClicked, // Кнопка неактивна только после нажатия
        modifier = modifier
            .height(56.dp)
            .wrapContentWidth()
            .shadow(
                elevation = 8.dp,
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
        Text(
            text = "Create contract",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.2
            )
        )
    }
}
