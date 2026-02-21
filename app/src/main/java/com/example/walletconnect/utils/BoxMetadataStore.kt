package com.example.walletconnect.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.math.BigInteger

/**
 * Хранилище метаданных боксов: порядок создания и статус
 */
object BoxMetadataStore {
    private const val PREFS_NAME = "box_metadata"
    private const val KEY_ORDER = "box_order"
    private const val KEY_STATUSES = "box_statuses"
    private const val KEY_AMOUNTS = "box_amounts"
    private const val KEY_MINTS = "box_mints"       // mint address for token boxes
    private const val KEY_IS_TOKEN = "box_is_token" // flag for token vs SOL boxes
    private const val KEY_DECIMALS = "box_decimals" // decimals for token boxes
    private const val KEY_SYMBOLS = "box_symbols"   // symbol for token boxes
    private const val KEY_FILE_TYPES = "box_file_types" // "epub" or "pdf"
    private const val KEY_BOOK_TITLES = "box_book_titles"
    
    /**
     * Метаданные бокса
     */
    data class BoxMetadata(
        val id: String,
        val createdAt: Long,  // timestamp создания для сортировки
        val status: BoxStatus // сохраненный статус
    )
    
    enum class BoxStatus {
        ACTIVE,   // активный
        WIN,      // успешно открыт
        LOSE      // просрочен
    }
    
    /**
     * Добавляет новый бокс в порядок (если еще не добавлен)
     */
    fun addBox(context: Context, boxId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val orderJson = prefs.getString(KEY_ORDER, "[]") ?: "[]"
            val orderArray = JSONArray(orderJson)
            
            // Проверяем, нет ли уже этого бокса
            for (i in 0 until orderArray.length()) {
                val item = orderArray.getJSONObject(i)
                if (item.getString("id") == boxId) {
                    Timber.d("📦 Бокс $boxId уже есть в порядке")
                    return // Уже есть
                }
            }
            
            // Добавляем новый бокс
            val newItem = JSONObject().apply {
                put("id", boxId)
                put("createdAt", System.currentTimeMillis())
            }
            orderArray.put(newItem)
            
            prefs.edit().putString(KEY_ORDER, orderArray.toString()).apply()
            Timber.d("📦 Добавлен бокс в порядок: $boxId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка добавления бокса в порядок")
        }
    }
    
    /**
     * Получает порядок боксов (от новых к старым)
     */
    fun getBoxOrder(context: Context): List<BoxMetadata> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val orderJson = prefs.getString(KEY_ORDER, "[]") ?: "[]"
            val orderArray = JSONArray(orderJson)
            
            val statuses = getStatuses(context)
            
            val result = mutableListOf<BoxMetadata>()
            for (i in 0 until orderArray.length()) {
                val item = orderArray.getJSONObject(i)
                val id = item.getString("id")
                result.add(BoxMetadata(
                    id = id,
                    createdAt = item.getLong("createdAt"),
                    status = statuses[id] ?: BoxStatus.ACTIVE
                ))
            }
            
            result.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения порядка боксов")
            emptyList()
        }
    }
    
    /**
     * Получает timestamp создания бокса для сортировки
     */
    fun getCreatedAt(context: Context, boxId: String): Long? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val orderJson = prefs.getString(KEY_ORDER, "[]") ?: "[]"
            val orderArray = JSONArray(orderJson)
            
            for (i in 0 until orderArray.length()) {
                val item = orderArray.getJSONObject(i)
                if (item.getString("id") == boxId) {
                    return item.getLong("createdAt")
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения createdAt для бокса $boxId")
            null
        }
    }
    
    /**
     * Устанавливает статус бокса
     */
    fun setStatus(context: Context, boxId: String, status: BoxStatus) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val statusesJson = prefs.getString(KEY_STATUSES, "{}") ?: "{}"
            val statusesObj = JSONObject(statusesJson)
            
            statusesObj.put(boxId, status.name)
            prefs.edit().putString(KEY_STATUSES, statusesObj.toString()).apply()
            Timber.d("📦 Установлен статус $status для бокса $boxId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка установки статуса для бокса $boxId")
        }
    }
    
    /**
     * Получает статус бокса
     */
    fun getStatus(context: Context, boxId: String): BoxStatus? {
        val statuses = getStatuses(context)
        return statuses[boxId]
    }
    
    /**
     * Получает все статусы
     */
    private fun getStatuses(context: Context): Map<String, BoxStatus> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val statusesJson = prefs.getString(KEY_STATUSES, "{}") ?: "{}"
            val statusesObj = JSONObject(statusesJson)
            
            val result = mutableMapOf<String, BoxStatus>()
            statusesObj.keys().forEach { key ->
                val statusName = statusesObj.getString(key)
                try {
                    result[key] = BoxStatus.valueOf(statusName)
                } catch (e: Exception) {
                    // Игнорируем некорректные статусы
                    Timber.w("⚠️ Некорректный статус для бокса $key: $statusName")
                }
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения статусов")
            emptyMap()
        }
    }
    
    /**
     * Устанавливает сохраненную сумму депозита для бокса
     */
    fun setAmount(context: Context, boxId: String, amount: BigInteger) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val amountsJson = prefs.getString(KEY_AMOUNTS, "{}") ?: "{}"
            val amountsObj = JSONObject(amountsJson)
            
            amountsObj.put(boxId, amount.toString())
            prefs.edit().putString(KEY_AMOUNTS, amountsObj.toString()).apply()
            Timber.d("📦 Установлена сумма депозита для бокса $boxId: $amount")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка установки суммы для бокса $boxId")
        }
    }
    
    /**
     * Получает сохраненную сумму депозита для бокса
     */
    fun getAmount(context: Context, boxId: String): BigInteger? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val amountsJson = prefs.getString(KEY_AMOUNTS, "{}") ?: "{}"
            val amountsObj = JSONObject(amountsJson)
            
            if (amountsObj.has(boxId)) {
                val amountStr = amountsObj.getString(boxId)
                BigInteger(amountStr)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения суммы для бокса $boxId")
            null
        }
    }
    
    /**
     * Устанавливает mint address для token бокса
     */
    fun setMint(context: Context, boxId: String, mintAddress: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val mintsJson = prefs.getString(KEY_MINTS, "{}") ?: "{}"
            val mintsObj = JSONObject(mintsJson)
            
            mintsObj.put(boxId, mintAddress)
            prefs.edit().putString(KEY_MINTS, mintsObj.toString()).apply()
            Timber.d("📦 Установлен mint для бокса $boxId: $mintAddress")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка установки mint для бокса $boxId")
        }
    }
    
    /**
     * Получает mint address для token бокса
     */
    fun getMint(context: Context, boxId: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val mintsJson = prefs.getString(KEY_MINTS, "{}") ?: "{}"
            val mintsObj = JSONObject(mintsJson)
            
            val result = if (mintsObj.has(boxId)) {
                mintsObj.getString(boxId)
            } else {
                null
            }
            
            Timber.d("📦 Получение mint для бокса $boxId: $result")
            result
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения mint для бокса $boxId")
            null
        }
    }
    
    /**
     * Устанавливает флаг token бокса (true = SPL token, false = SOL)
     */
    fun setIsToken(context: Context, boxId: String, isToken: Boolean) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isTokenJson = prefs.getString(KEY_IS_TOKEN, "{}") ?: "{}"
            val isTokenObj = JSONObject(isTokenJson)
            
            isTokenObj.put(boxId, isToken)
            prefs.edit().putString(KEY_IS_TOKEN, isTokenObj.toString()).apply()
            Timber.d("📦 Установлен isToken=$isToken для бокса $boxId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка установки isToken для бокса $boxId")
        }
    }
    
    /**
     * Проверяет, является ли бокс token боксом
     */
    fun isTokenBox(context: Context, boxId: String): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isTokenJson = prefs.getString(KEY_IS_TOKEN, "{}") ?: "{}"
            val isTokenObj = JSONObject(isTokenJson)
            
            if (isTokenObj.has(boxId)) {
                isTokenObj.getBoolean(boxId)
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения isToken для бокса $boxId")
            false
        }
    }
    
    /**
     * Устанавливает decimals для token бокса
     */
    fun setDecimals(context: Context, boxId: String, decimals: Int) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val decimalsJson = prefs.getString(KEY_DECIMALS, "{}") ?: "{}"
            val decimalsObj = JSONObject(decimalsJson)
            
            decimalsObj.put(boxId, decimals)
            val success = prefs.edit().putString(KEY_DECIMALS, decimalsObj.toString()).commit()
            Timber.d("📦 Установлены decimals для бокса $boxId: $decimals (success=$success)")
            
            // Проверяем, что сохранилось
            val saved = getDecimals(context, boxId)
            Timber.d("📦 Проверка сохранения decimals: boxId=$boxId, saved=$saved")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка установки decimals для бокса $boxId")
        }
    }
    
    /**
     * Получает decimals для token бокса
     */
    fun getDecimals(context: Context, boxId: String): Int? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val decimalsJson = prefs.getString(KEY_DECIMALS, "{}") ?: "{}"
            val decimalsObj = JSONObject(decimalsJson)
            
            val result = if (decimalsObj.has(boxId)) {
                decimalsObj.getInt(boxId)
            } else {
                null
            }
            
            Timber.d("📦 Получение decimals для бокса $boxId: $result (всего записей: ${decimalsObj.length()})")
            result
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения decimals для бокса $boxId")
            null
        }
    }
    
    /**
     * Устанавливает symbol для token бокса
     */
    fun setSymbol(context: Context, boxId: String, symbol: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val symbolsJson = prefs.getString(KEY_SYMBOLS, "{}") ?: "{}"
            val symbolsObj = JSONObject(symbolsJson)
            
            symbolsObj.put(boxId, symbol)
            val success = prefs.edit().putString(KEY_SYMBOLS, symbolsObj.toString()).commit()
            Timber.d("📦 Установлен symbol для бокса $boxId: $symbol (success=$success)")
            
            // Проверяем, что сохранилось
            val saved = getSymbol(context, boxId)
            Timber.d("📦 Проверка сохранения symbol: boxId=$boxId, saved=$saved")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка установки symbol для бокса $boxId")
        }
    }
    
    /**
     * Получает symbol для token бокса
     */
    fun getSymbol(context: Context, boxId: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val symbolsJson = prefs.getString(KEY_SYMBOLS, "{}") ?: "{}"
            val symbolsObj = JSONObject(symbolsJson)
            
            val result = if (symbolsObj.has(boxId)) {
                symbolsObj.getString(boxId)
            } else {
                null
            }
            
            Timber.d("📦 Получение symbol для бокса $boxId: $result (всего записей: ${symbolsObj.length()})")
            result
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения symbol для бокса $boxId")
            null
        }
    }
    
    fun setFileType(context: Context, boxId: String, fileType: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_FILE_TYPES, "{}") ?: "{}"
            val obj = JSONObject(json)
            obj.put(boxId, fileType)
            prefs.edit().putString(KEY_FILE_TYPES, obj.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error setting file type for box $boxId")
        }
    }

    fun getFileType(context: Context, boxId: String): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_FILE_TYPES, "{}") ?: "{}"
            val obj = JSONObject(json)
            if (obj.has(boxId)) obj.getString(boxId) else "epub"
        } catch (e: Exception) {
            Timber.e(e, "Error getting file type for box $boxId")
            "epub"
        }
    }

    fun setBookTitle(context: Context, boxId: String, title: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_BOOK_TITLES, "{}") ?: "{}"
            val obj = JSONObject(json)
            obj.put(boxId, title)
            prefs.edit().putString(KEY_BOOK_TITLES, obj.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "Error setting book title for box $boxId")
        }
    }

    fun getBookTitle(context: Context, boxId: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_BOOK_TITLES, "{}") ?: "{}"
            val obj = JSONObject(json)
            if (obj.has(boxId)) obj.getString(boxId) else null
        } catch (e: Exception) {
            Timber.e(e, "Error getting book title for box $boxId")
            null
        }
    }

    /**
     * Удаляет бокс из хранилища
     */
    fun removeBox(context: Context, boxId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Удаляем из порядка
            val orderJson = prefs.getString(KEY_ORDER, "[]") ?: "[]"
            val orderArray = JSONArray(orderJson)
            val newOrderArray = JSONArray()
            
            for (i in 0 until orderArray.length()) {
                val item = orderArray.getJSONObject(i)
                if (item.getString("id") != boxId) {
                    newOrderArray.put(item)
                }
            }
            
            // Удаляем статус
            val statusesJson = prefs.getString(KEY_STATUSES, "{}") ?: "{}"
            val statusesObj = JSONObject(statusesJson)
            statusesObj.remove(boxId)
            
            // Удаляем сумму
            val amountsJson = prefs.getString(KEY_AMOUNTS, "{}") ?: "{}"
            val amountsObj = JSONObject(amountsJson)
            amountsObj.remove(boxId)
            
            // Удаляем mint
            val mintsJson = prefs.getString(KEY_MINTS, "{}") ?: "{}"
            val mintsObj = JSONObject(mintsJson)
            mintsObj.remove(boxId)
            
            // Удаляем флаг isToken
            val isTokenJson = prefs.getString(KEY_IS_TOKEN, "{}") ?: "{}"
            val isTokenObj = JSONObject(isTokenJson)
            isTokenObj.remove(boxId)
            
            // Удаляем decimals
            val decimalsJson = prefs.getString(KEY_DECIMALS, "{}") ?: "{}"
            val decimalsObj = JSONObject(decimalsJson)
            decimalsObj.remove(boxId)
            
            // Удаляем symbol
            val symbolsJson = prefs.getString(KEY_SYMBOLS, "{}") ?: "{}"
            val symbolsObj = JSONObject(symbolsJson)
            symbolsObj.remove(boxId)

            // Удаляем file type
            val fileTypesJson = prefs.getString(KEY_FILE_TYPES, "{}") ?: "{}"
            val fileTypesObj = JSONObject(fileTypesJson)
            fileTypesObj.remove(boxId)

            // Удаляем book title
            val titlesJson = prefs.getString(KEY_BOOK_TITLES, "{}") ?: "{}"
            val titlesObj = JSONObject(titlesJson)
            titlesObj.remove(boxId)
            
            prefs.edit()
                .putString(KEY_ORDER, newOrderArray.toString())
                .putString(KEY_STATUSES, statusesObj.toString())
                .putString(KEY_AMOUNTS, amountsObj.toString())
                .putString(KEY_MINTS, mintsObj.toString())
                .putString(KEY_IS_TOKEN, isTokenObj.toString())
                .putString(KEY_DECIMALS, decimalsObj.toString())
                .putString(KEY_SYMBOLS, symbolsObj.toString())
                .putString(KEY_FILE_TYPES, fileTypesObj.toString())
                .putString(KEY_BOOK_TITLES, titlesObj.toString())
                .apply()
            
            Timber.d("📦 Удален бокс из хранилища: $boxId")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка удаления бокса $boxId")
        }
    }
}


