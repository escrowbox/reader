package com.example.walletconnect.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.math.BigInteger

/**
 * CheckpointContractStore – зашифрованное хранилище для параметров checkpoints контрактов.
 * Сохраняет days, amount по идентификатору бокса (boxId).
 */
object CheckpointContractStore {
    private const val PREFS_NAME = "secure_checkpoint_contract"
    private const val DAYS_SUFFIX = "_days"
    private const val AMOUNT_SUFFIX = "_amount"

    // КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Кешируем SharedPreferences
    // Без кеша каждое обращение создает новый MasterKey → обращение к Android Keystore → лаги!
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getSecurePrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createSecurePrefs(context).also { cachedPrefs = it }
        }
    }

    private fun createSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Ошибка инициализации CheckpointContractStore, fallback на незашифрованное хранилище")
            context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Сохраняет параметры checkpoints контракта для конкретного boxId.
     * @param amount Количество ETH в wei
     */
    fun saveCheckpointParams(
        context: Context,
        boxId: String,
        days: Int,
        amount: BigInteger
    ) {
        try {
            val keyBase = boxId.lowercase()
            getSecurePrefs(context)
                .edit()
                .putInt(keyBase + DAYS_SUFFIX, days)
                .putString(keyBase + AMOUNT_SUFFIX, amount.toString())
                .apply()
            // Timber.d("Параметры checkpoints для бокса $boxId сохранены: days=$days, amount=$amount")
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения параметров checkpoints для бокса $boxId")
        }
    }

    /**
     * Возвращает параметры checkpoints контракта для boxId или null, если их нет.
     * @property amount Количество ETH в wei
     */
    data class CheckpointParams(
        val days: Int,
        val amount: BigInteger
    )

    fun getCheckpointParams(context: Context, boxId: String): CheckpointParams? {
        return try {
            val keyBase = boxId.lowercase()
            val prefs = getSecurePrefs(context)
            
            // Проверяем, есть ли хотя бы один параметр
            if (!prefs.contains(keyBase + DAYS_SUFFIX)) {
                return null
            }
            
            val days = prefs.getInt(keyBase + DAYS_SUFFIX, 0)
            val amountStr = prefs.getString(keyBase + AMOUNT_SUFFIX, "0") ?: "0"
            val amount = amountStr.toBigIntegerOrNull() ?: BigInteger.ZERO
            
            CheckpointParams(days = days, amount = amount)
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения параметров checkpoints для бокса $boxId")
            null
        }
    }

    /**
     * Проверяет, является ли контракт checkpoints контрактом.
     */
    fun isCheckpointContract(context: Context, boxId: String): Boolean {
        return getCheckpointParams(context, boxId) != null
    }
}


