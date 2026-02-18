package com.example.walletconnect.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.math.BigInteger

/**
 * TimerContractStore – зашифрованное хранилище для параметров timer контрактов.
 * Сохраняет hours, days, amount (ETH в wei), swipeControl, handControl, faceControl по идентификатору бокса (boxId).
 */
object TimerContractStore {
    private const val PREFS_NAME = "secure_timer_contract"
    private const val HOURS_SUFFIX = "_hours"
    private const val DAYS_SUFFIX = "_days"
    private const val AMOUNT_SUFFIX = "_amount"
    private const val SWIPE_CONTROL_SUFFIX = "_swipe"
    private const val HAND_CONTROL_SUFFIX = "_hand"
    private const val FACE_CONTROL_SUFFIX = "_face"
    private const val REMAINING_SECONDS_SUFFIX = "_remaining_seconds"

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
            Timber.e(e, "Ошибка инициализации TimerContractStore, fallback на незашифрованное хранилище")
            context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Сохраняет параметры timer контракта для конкретного boxId.
     * @param amount Количество ETH в wei
     */
    fun saveTimerParams(
        context: Context,
        boxId: String,
        hours: Int,
        days: Int,
        amount: BigInteger,
        swipeControl: Boolean,
        handControl: Boolean,
        faceControl: Boolean
    ) {
        try {
            val keyBase = boxId.lowercase()
            val prefs = getSecurePrefs(context)
            
            // Если remainingSeconds еще не установлен, инициализируем его
            val isFirstSave = !prefs.contains(keyBase + REMAINING_SECONDS_SUFFIX)
            val initialSeconds = hours * 3600L
            
            prefs.edit()
                .putInt(keyBase + HOURS_SUFFIX, hours)
                .putInt(keyBase + DAYS_SUFFIX, days)
                .putString(keyBase + AMOUNT_SUFFIX, amount.toString())
                .putBoolean(keyBase + SWIPE_CONTROL_SUFFIX, swipeControl)
                .putBoolean(keyBase + HAND_CONTROL_SUFFIX, handControl)
                .putBoolean(keyBase + FACE_CONTROL_SUFFIX, faceControl)
                .apply()
            
            // При первом сохранении инициализируем оставшиеся секунды
            if (isFirstSave) {
                saveRemainingSeconds(context, boxId, initialSeconds)
            }
            
            // Timber.d("Параметры timer для бокса $boxId сохранены: hours=$hours, days=$days, amount=$amount, swipe=$swipeControl, hand=$handControl, face=$faceControl")
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения параметров timer для бокса $boxId")
        }
    }

    /**
     * Возвращает параметры timer контракта для boxId или null, если их нет.
     * @property amount Количество ETH в wei
     */
    data class TimerParams(
        val hours: Int,
        val days: Int,
        val amount: BigInteger,
        val swipeControl: Boolean,
        val handControl: Boolean,
        val faceControl: Boolean
    )

    fun getTimerParams(context: Context, boxId: String): TimerParams? {
        return try {
            val keyBase = boxId.lowercase()
            val prefs = getSecurePrefs(context)
            
            // Проверяем, есть ли хотя бы один параметр (признак timer контракта)
            if (!prefs.contains(keyBase + HOURS_SUFFIX)) {
                return null
            }
            
            val hours = prefs.getInt(keyBase + HOURS_SUFFIX, 0)
            val days = prefs.getInt(keyBase + DAYS_SUFFIX, 0)
            val amountStr = prefs.getString(keyBase + AMOUNT_SUFFIX, "0") ?: "0"
            val amount = amountStr.toBigIntegerOrNull() ?: BigInteger.ZERO
            val swipeControl = prefs.getBoolean(keyBase + SWIPE_CONTROL_SUFFIX, false)
            val handControl = prefs.getBoolean(keyBase + HAND_CONTROL_SUFFIX, false)
            val faceControl = prefs.getBoolean(keyBase + FACE_CONTROL_SUFFIX, false)
            
            TimerParams(
                hours = hours,
                days = days,
                amount = amount,
                swipeControl = swipeControl,
                handControl = handControl,
                faceControl = faceControl
            )
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения параметров timer для бокса $boxId")
            null
        }
    }

    /**
     * Проверяет, является ли контракт timer контрактом.
     */
    fun isTimerContract(context: Context, boxId: String): Boolean {
        return getTimerParams(context, boxId) != null
    }

    /**
     * Сохраняет оставшиеся секунды для timer контракта.
     */
    fun saveRemainingSeconds(context: Context, boxId: String, remainingSeconds: Long) {
        try {
            val keyBase = boxId.lowercase()
            getSecurePrefs(context)
                .edit()
                .putLong(keyBase + REMAINING_SECONDS_SUFFIX, remainingSeconds.coerceAtLeast(0))
                .apply()
            // Логирование отключено для производительности (вызывается каждую секунду)
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения оставшихся секунд для бокса $boxId")
        }
    }

    /**
     * Возвращает оставшиеся секунды для timer контракта.
     * Если не установлено, возвращает начальное значение из hours * 3600.
     */
    fun getRemainingSeconds(context: Context, boxId: String): Long {
        return try {
            val keyBase = boxId.lowercase()
            val prefs = getSecurePrefs(context)
            
            if (prefs.contains(keyBase + REMAINING_SECONDS_SUFFIX)) {
                prefs.getLong(keyBase + REMAINING_SECONDS_SUFFIX, 0)
            } else {
                // Если не установлено, вычисляем из hours
                val params = getTimerParams(context, boxId)
                val initialSeconds = (params?.hours ?: 0) * 3600L
                if (initialSeconds > 0) {
                    saveRemainingSeconds(context, boxId, initialSeconds)
                }
                initialSeconds
            }
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения оставшихся секунд для бокса $boxId")
            0L
        }
    }
}

