package com.example.walletconnect.ui.hooks

/**
 * Состояния транзакции, аналогично React STATES
 */
enum class TxStatus {
    IDLE,
    SIGNING,
    MINING,
    SUCCESS,
    ERROR
}

/**
 * Текстовые метки для каждого состояния
 */
val TX_STATUS_LABELS = mapOf(
    TxStatus.IDLE to "Create box",
    TxStatus.SIGNING to "Approve in wallet…",
    TxStatus.MINING to "Mining…",
    TxStatus.SUCCESS to "Created ✅",
    TxStatus.ERROR to "Failed ❌"
)






