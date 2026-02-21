package com.example.walletconnect.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/**
 * CheckpointIndexStore – зашифрованное хранилище для индексов чекпоинтов книги.
 * Сохраняет и отдает список из 3 Int по идентификатору бокса (boxId).
 */
object CheckpointIndexStore {

    private const val PREFS_NAME = "secure_checkpoint_indices"
    private const val KEY_SUFFIX = "_indices"
    private const val FOUND_SUFFIX = "_found"
    private const val LABEL_SUFFIX = "_label"
    private const val PAGE_SUFFIX = "_current_page"
    private const val CHAR_INDEX_SUFFIX = "_char_index"
    private const val TOTAL_PAGES_SUFFIX = "_total_pages"

    // КРИТИЧЕСКАЯ ОПТИМИЗАЦИЯ: Кешируем SharedPreferences
    // Без кеша каждое обращение создает новый MasterKey → обращение к Android Keystore → лаги!
    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    /**
     * Возвращает закешированный экземпляр зашифрованных SharedPreferences.
     * Использует double-checked locking для thread-safety.
     */
    private fun getSecurePrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createSecurePrefs(context).also { cachedPrefs = it }
        }
    }

    /**
     * Создает новый экземпляр EncryptedSharedPreferences.
     * Вызывается только один раз благодаря кешированию.
     */
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
            Timber.e(e, "Ошибка инициализации CheckpointIndexStore, fallback на незашифрованное хранилище")
            context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Сохраняет список индексов чекпоинтов для конкретного boxId.
     */
    fun saveIndices(context: Context, boxId: String, indices: List<Int>) {
        if (indices.isEmpty()) return
        val safeIndices = indices.sorted().joinToString(",")
        try {
            getSecurePrefs(context)
                .edit()
                .putString(boxId.lowercase() + KEY_SUFFIX, safeIndices)
                .apply()
            // Timber.d("Индексы чекпоинтов для бокса $boxId сохранены: $safeIndices")
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения индексов чекпоинтов для бокса $boxId")
        }
    }

    /**
     * Возвращает отсортированный список индексов чекпоинтов для boxId.
     */
    fun getIndices(context: Context, boxId: String): List<Int> {
        return try {
            val raw = getSecurePrefs(context)
                .getString(boxId.lowercase() + KEY_SUFFIX, null)
                ?: return emptyList()

            raw.split(",")
                .mapNotNull { it.toIntOrNull() }
                .sorted()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения индексов чекпоинтов для бокса $boxId")
            emptyList()
        }
    }

    /**
     * Сохраняет список найденных индексов чекпоинтов для boxId.
     */
    fun saveFoundIndices(context: Context, boxId: String, foundIndices: List<Int>) {
        val safeIndices = foundIndices.sorted().joinToString(",")
        try {
            getSecurePrefs(context)
                .edit()
                .putString(boxId.lowercase() + FOUND_SUFFIX, safeIndices)
                .apply()
            // Timber.d("Найденные индексы чекпоинтов для бокса $boxId сохранены: $safeIndices")
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения найденных индексов чекпоинтов для бокса $boxId")
        }
    }

    /**
     * Возвращает список найденных индексов чекпоинтов для boxId.
     */
    fun getFoundIndices(context: Context, boxId: String): List<Int> {
        return try {
            val raw = getSecurePrefs(context)
                .getString(boxId.lowercase() + FOUND_SUFFIX, null)
                ?: return emptyList()

            raw.split(",")
                .mapNotNull { it.toIntOrNull() }
                .sorted()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения найденных индексов чекпоинтов для бокса $boxId")
            emptyList()
        }
    }

    /**
     * Добавляет индекс к списку найденных чекпоинтов для boxId.
     */
    fun markIndexAsFound(context: Context, boxId: String, index: Int) {
        val currentFound = getFoundIndices(context, boxId).toMutableSet()
        currentFound.add(index)
        saveFoundIndices(context, boxId, currentFound.toList())
    }

    /**
     * Сохраняет текст чекпоинта для boxId.
     */
    fun saveCheckpointLabel(context: Context, boxId: String, label: String) {
        if (label.isBlank()) return
        try {
            getSecurePrefs(context)
                .edit()
                .putString(boxId.lowercase() + LABEL_SUFFIX, label)
                .apply()
            // Timber.d("Текст чекпоинта для бокса $boxId сохранен: $label")
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения текста чекпоинта для бокса $boxId")
        }
    }

    /**
     * Возвращает текст чекпоинта для boxId или дефолтный текст.
     */
    fun getCheckpointLabel(context: Context, boxId: String): String {
        return try {
            getSecurePrefs(context)
                .getString(boxId.lowercase() + LABEL_SUFFIX, " [I find checkpoint] ")
                ?: " [I find checkpoint] "
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения текста чекпоинта для бокса $boxId")
            " [I find checkpoint] "
        }
    }

    /**
     * Сохраняет текущую страницу для конкретного boxId.
     */
    fun saveCurrentPage(context: Context, boxId: String, pageNumber: Int) {
        try {
            val prefs = getSecurePrefs(context)
            val oldPage = prefs.getInt(boxId.lowercase() + PAGE_SUFFIX, -1)
            prefs.edit()
                .putInt(boxId.lowercase() + PAGE_SUFFIX, pageNumber)
                .apply()
            // Timber.d("✅ CheckpointIndexStore: Сохранена страница для бокса $boxId: $pageNumber (было: $oldPage)")
            
            // Проверяем, что действительно сохранилось
            val saved = prefs.getInt(boxId.lowercase() + PAGE_SUFFIX, -999)
            if (saved != pageNumber) {
                Timber.e("❌ ОШИБКА: Сохранили $pageNumber, но прочитали $saved!")
            }
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения текущей страницы для бокса $boxId")
        }
    }

    /**
     * Возвращает сохраненную страницу для boxId или 0 (первая страница).
     */
    fun getCurrentPage(context: Context, boxId: String): Int {
        return try {
            val page = getSecurePrefs(context)
                .getInt(boxId.lowercase() + PAGE_SUFFIX, 0)
            // Timber.d("📖 CheckpointIndexStore: Загружена страница для бокса $boxId: $page")
            page
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения текущей страницы для бокса $boxId")
            0
        }
    }

    /**
     * Сохраняет индекс символа (позицию в тексте) для конкретного boxId.
     */
    fun saveCharIndex(context: Context, boxId: String, charIndex: Int) {
        try {
            val prefs = getSecurePrefs(context)
            val oldIndex = prefs.getInt(boxId.lowercase() + CHAR_INDEX_SUFFIX, -1)
            prefs.edit()
                .putInt(boxId.lowercase() + CHAR_INDEX_SUFFIX, charIndex)
                .apply()
            // Timber.d("✅ CheckpointIndexStore: Сохранён индекс символа для бокса $boxId: $charIndex (было: $oldIndex)")
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения индекса символа для бокса $boxId")
        }
    }

    /**
     * Сохраняет общее количество страниц для конкретного boxId.
     */
    fun saveTotalPages(context: Context, boxId: String, totalPages: Int) {
        if (totalPages <= 0) return
        try {
            getSecurePrefs(context)
                .edit()
                .putInt(boxId.lowercase() + TOTAL_PAGES_SUFFIX, totalPages)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка сохранения totalPages для бокса $boxId")
        }
    }

    /**
     * Возвращает сохранённое количество страниц для boxId или 0 если неизвестно.
     */
    fun getTotalPages(context: Context, boxId: String): Int {
        return try {
            getSecurePrefs(context)
                .getInt(boxId.lowercase() + TOTAL_PAGES_SUFFIX, 0)
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения totalPages для бокса $boxId")
            0
        }
    }

    /**
     * Возвращает сохранённый индекс символа для boxId или -1 (начало книги).
     */
    fun getCharIndex(context: Context, boxId: String): Int {
        return try {
            val charIndex = getSecurePrefs(context)
                .getInt(boxId.lowercase() + CHAR_INDEX_SUFFIX, -1)
            // Timber.d("📖 CheckpointIndexStore: Загружен индекс символа для бокса $boxId: $charIndex")
            charIndex
        } catch (e: Exception) {
            Timber.e(e, "Ошибка чтения индекса символа для бокса $boxId")
            -1
        }
    }
}


