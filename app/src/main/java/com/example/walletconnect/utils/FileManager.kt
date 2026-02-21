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
    private const val BOOKS_DIR = "epubs"

    private fun booksDir(context: Context): File {
        val dir = File(context.filesDir, BOOKS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveEpubFile(context: Context, uri: Uri, boxId: String): String? {
        return saveBookFile(context, uri, boxId, "epub")
    }

    fun savePdfFile(context: Context, uri: Uri, boxId: String): String? {
        return saveBookFile(context, uri, boxId, "pdf")
    }

    private fun saveBookFile(context: Context, uri: Uri, boxId: String, extension: String): String? {
        return try {
            val directory = booksDir(context)
            val fileName = "book_${boxId.lowercase()}.$extension"
            val destinationFile = File(directory, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            destinationFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Error saving $extension file")
            null
        }
    }

    fun getEpubFile(context: Context, boxId: String): File? {
        val file = File(booksDir(context), "book_${boxId.lowercase()}.epub")
        return if (file.exists()) file else null
    }

    fun getPdfFile(context: Context, boxId: String): File? {
        val file = File(booksDir(context), "book_${boxId.lowercase()}.pdf")
        return if (file.exists()) file else null
    }

    fun getBookFile(context: Context, boxId: String): File? {
        return getEpubFile(context, boxId) ?: getPdfFile(context, boxId)
    }

    fun deleteEpubFile(context: Context, boxId: String): Boolean {
        return getEpubFile(context, boxId)?.delete() ?: false
    }

    fun deletePdfFile(context: Context, boxId: String): Boolean {
        return getPdfFile(context, boxId)?.delete() ?: false
    }
}








