package com.example.walletconnect.utils

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.zip.ZipInputStream
import kotlin.random.Random

/**
 * EpubTextExtractor – утилита для извлечения плоского текста из EPUB
 * и выбора 3 индексов чекпоинтов (начало, середина, конец книги).
 */
object EpubTextExtractor {

    /**
     * Извлекает весь текст из EPUB файла в один большой String.
     */
    fun extractFullText(context: Context, uri: Uri): String {
        return try {
            val builder = StringBuilder()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".html", ignoreCase = true) ||
                            entry.name.endsWith(".xhtml", ignoreCase = true)
                        ) {
                            val htmlContent = zip.bufferedReader().readText()
                            val doc = Jsoup.parse(htmlContent)
                            // Для читаемости добавляем явные разделители блоков
                            doc.select("p, h1, h2, h3, h4, h5, h6, div, li").append(" ")
                            builder.append(doc.text()).append(" ")
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            builder.toString().trim()
        } catch (e: Exception) {
            Timber.e(e, "Ошибка извлечения текста из EPUB")
            ""
        }
    }

    /**
     * Выбирает 3 индекса чекпоинтов: в начале, середине и конце текста.
     * Индексы привязаны к символам в fullText.
     */
    fun pickCheckpointIndices(fullText: String): List<Int> {
        if (fullText.isBlank()) return emptyList()

        val len = fullText.length
        if (len < 300) {
            // Мало текста – 3 точки примерно равномерно по книге
            val step = (len / 4).coerceAtLeast(1)
            return listOf(step, 2 * step, 3 * step.coerceAtMost(len - 1)).sorted()
        }

        val part = len / 3

        fun randomInRange(start: Int, end: Int): Int {
            if (end <= start) return start
            return Random.nextInt(start, end)
        }

        // 3 точки: начало, середина, конец
        val raw1 = randomInRange(0, part)
        val raw2 = randomInRange(part, 2 * part)
        val raw3 = randomInRange(2 * part, len)

        // Сдвигаем к ближайшей границе слова влево, чтобы не встать в середину слова
        fun snapToWordBoundary(idx: Int): Int {
            var i = idx.coerceIn(0, len - 1)
            while (i > 0 && !fullText[i].isWhitespace()) {
                i--
            }
            return (i + 1).coerceAtMost(len - 1)
        }

        return listOf(raw1, raw2, raw3)
            .map { snapToWordBoundary(it) }
            .sorted()
    }
}








