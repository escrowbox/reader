package com.example.walletconnect.ui.hooks

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.walletconnect.SolanaManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch
import java.math.BigInteger

/**
 * Данные, возвращаемые хуком useTxButton (аналог React)
 */
data class TxButtonState(
    val title: String,
    val send: (String, Int, BigInteger, ActivityResultSender) -> Unit,
    val disabled: Boolean
)

/**
 * Хук для управления состоянием транзакции (аналог useTxButton из React)
 * 
 * @param contractManager Менеджер для отправки транзакций
 * @param onTransactionSent Callback, вызываемый после отправки транзакции
 * @return Состояние кнопки: title, send функция, disabled флаг
 */
@Composable
fun useTxButton(
    contractManager: SolanaManager,
    onTransactionSent: (() -> Unit)? = null
): TxButtonState {
    // Подписываемся на изменения статуса из менеджера (аналог useState)
    val status by contractManager.txStatusFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    
    // Отслеживаем предыдущий статус для определения момента отправки
    var previousStatus by remember { mutableStateOf(TxStatus.IDLE) }
    
    LaunchedEffect(status) {
        // Когда статус меняется с SIGNING на MINING, транзакция отправлена
        if (previousStatus == TxStatus.SIGNING && status == TxStatus.MINING) {
            onTransactionSent?.invoke()
        }
        previousStatus = status
    }
    
    // Функция отправки транзакции
    val send: (String, Int, BigInteger, ActivityResultSender) -> Unit = { id, deadline, amount, sender ->
        scope.launch {
            try {
                // Отправляем транзакцию через менеджер
                contractManager.sendCreateBoxWithStatus(
                    id = id,
                    deadlineDays = deadline,
                    amount = amount,
                    sender = sender
                )
            } catch (e: Exception) {
                // Ошибка обрабатывается в менеджере
            }
        }
    }
    
    return TxButtonState(
        title = TX_STATUS_LABELS[status] ?: "Create box",
        send = send,
        disabled = status != TxStatus.IDLE
    )
}

