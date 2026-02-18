package com.example.walletconnect.utils

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * FileManager - обеспечивает управление файлами во внутреннем (приватном) хранилище приложения.
 * Файлы в этом хранилище недоступны другим приложениям и пользователю через проводник.
 */
object FileManager {
    private const val EPUB_DIR = "epubs"

    /**
     * Копирует файл из Uri (внешний источник) в приватную папку приложения.
     * Файл привязывается к конкретному boxId.
     * 
     * @return абсолютный путь к сохраненному файлу или null в случае ошибки.
     */
    fun saveEpubFile(context: Context, uri: Uri, boxId: String): String? {
        return try {
            // Создаем директорию для книг, если её нет
            val directory = File(context.filesDir, EPUB_DIR)
            if (!directory.exists()) {
                val created = directory.mkdirs()
                if (!created && !directory.exists()) {
                    Timber.e("❌ Не удалось создать директорию для EPUB")
                    return null
                }
            }

            // Формируем уникальное имя файла на основе ID бокса
            val fileName = "book_${boxId.lowercase()}.epub"
            val destinationFile = File(directory, fileName)

            // Копируем содержимое
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // Timber.d("📂 Файл EPUB успешно скопирован в приватное хранилище: ${destinationFile.absolutePath}")
            destinationFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка при сохранении EPUB файла во внутреннее хранилище")
            null
        }
    }

    /**
     * Проверяет наличие и возвращает объект File для конкретного бокса.
     */
    fun getEpubFile(context: Context, boxId: String): File? {
        val fileName = "book_${boxId.lowercase()}.epub"
        val file = File(File(context.filesDir, EPUB_DIR), fileName)
        return if (file.exists()) file else null
    }

    /**
     * Удаляет файл книги, если контракт более не актуален (опционально).
     */
    fun deleteEpubFile(context: Context, boxId: String): Boolean {
        return getEpubFile(context, boxId)?.delete() ?: false
    }
}








