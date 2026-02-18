package com.example.walletconnect.epub

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Парсер для чтения EPUB файлов с сохранением структуры форматирования
 */
class EpubParser(private val context: Context) {

    /**
     * Результат парсинга EPUB файла
     */
    data class ParseResult(
        val elements: List<FormattedElement>,
        val images: Map<String, ByteArray> // Путь к изображению -> данные изображения
    )

    /**
     * Извлекает структурированное содержимое из EPUB файла
     */
    fun parseEpubFile(uri: Uri): ParseResult {
        val contentResolver = context.contentResolver
        val inputStream: InputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open file")

        return extractFormattedElementsFromEpub(inputStream)
    }

    /**
     * Извлекает структурированные элементы из всех HTML файлов внутри EPUB
     */
    private fun extractFormattedElementsFromEpub(inputStream: InputStream): ParseResult {
        val elements = mutableListOf<FormattedElement>()
        val zipInputStream = ZipInputStream(inputStream)
        var entry = zipInputStream.nextEntry
        var foundHtmlFiles = false
        val imageMap = mutableMapOf<String, ByteArray>() // Карта изображений: путь -> данные
        val htmlFiles = mutableListOf<String>() // Список HTML файлов для обработки

        // Проходим по архиву один раз, собираем изображения и HTML файлы
        while (entry != null) {
            val entryName = entry.name.lowercase()
            when {
                entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") || 
                entryName.endsWith(".png") || entryName.endsWith(".gif") || 
                entryName.endsWith(".webp") || entryName.endsWith(".svg") -> {
                    try {
                        val imageData = zipInputStream.readBytes()
                        imageMap[entry.name] = imageData
                    } catch (e: Exception) {
                        // Пропускаем изображения с ошибками чтения
                    }
                }
                entryName.endsWith(".html") || entryName.endsWith(".xhtml") -> {
                    try {
                        val html = zipInputStream.bufferedReader().readText()
                        htmlFiles.add(html)
                        foundHtmlFiles = true
                    } catch (e: Exception) {
                        // Пропускаем файлы с ошибками чтения
                    }
                }
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }

        zipInputStream.close()
        
        if (!foundHtmlFiles) {
            throw IllegalArgumentException("EPUB файл не содержит HTML файлов")
        }
        
        // Обрабатываем все HTML файлы
        htmlFiles.forEach { html ->
            val formattedElements = extractFormattedElementsFromHtml(html, imageMap)
            elements.addAll(formattedElements)
        }
        
        if (elements.isEmpty()) {
            throw IllegalArgumentException("Не удалось извлечь текст из EPUB файла")
        }
        
        return ParseResult(elements, imageMap)
    }

    /**
     * Извлекает структурированные элементы из HTML с помощью JSoup
     */
    private fun extractFormattedElementsFromHtml(
        html: String,
        imageMap: Map<String, ByteArray> = emptyMap()
    ): List<FormattedElement> {
        val doc = Jsoup.parse(html)
        doc.select("script, style").remove()
        
        val elements = mutableListOf<FormattedElement>()
        val body = doc.body() ?: return elements
        
        processElement(body, elements, imageMap)
        
        return elements
    }

    /**
     * Рекурсивно обрабатывает HTML элементы и преобразует их в FormattedElement
     */
    private fun processElement(
        element: Element, 
        result: MutableList<FormattedElement>,
        imageMap: Map<String, ByteArray> = emptyMap()
    ) {
        when (element.tagName().lowercase()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().substring(1).toIntOrNull() ?: 1
                val text = element.text().trim()
                if (text.isNotBlank()) {
                    result.add(FormattedElement.Heading(level, text))
                }
            }
            "p" -> {
                // Обрабатываем содержимое параграфа, включая изображения и текст
                processParagraphContent(element, result, imageMap)
            }
            "img" -> {
                val src = element.attr("src")
                val alt = element.attr("alt")
                if (src.isNotBlank()) {
                    // Нормализуем путь к изображению
                    val normalizedSrc = normalizeImagePath(src)
                    result.add(FormattedElement.Image(normalizedSrc, alt))
                }
            }
            "br" -> {
                result.add(FormattedElement.LineBreak(1))
            }
            "hr" -> {
                result.add(FormattedElement.HorizontalRule)
            }
            "ul", "ol" -> {
                val items = element.select("li").mapNotNull { it.text().trim() }
                    .filter { it.isNotBlank() }
                if (items.isNotEmpty()) {
                    result.add(FormattedElement.ListItem(items, element.tagName() == "ol"))
                }
            }
            "blockquote", "q" -> {
                val text = element.text().trim()
                if (text.isNotBlank()) {
                    result.add(FormattedElement.Quote(text))
                }
            }
            "div", "section", "article", "body" -> {
                // Обрабатываем все дочерние элементы рекурсивно
                element.children().forEach { child ->
                    processElement(child, result, imageMap)
                }
                // Также обрабатываем собственный текст элемента, если он есть
                val ownText = element.ownText().trim()
                if (ownText.isNotBlank()) {
                    // Разбиваем текст на строки и добавляем как параграфы
                    ownText.split("\n").forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotBlank()) {
                            result.add(FormattedElement.Paragraph(trimmedLine))
                        }
                    }
                }
            }
            else -> {
                // Для остальных элементов извлекаем текст, если он есть
                val text = element.ownText().trim()
                if (text.isNotBlank() && element.children().isEmpty()) {
                    // Если это текстовый элемент без дочерних элементов
                    result.add(FormattedElement.Paragraph(text))
                } else {
                    // Обрабатываем дочерние элементы
                    element.children().forEach { child ->
                        processElement(child, result, imageMap)
                    }
                }
            }
        }
    }

    /**
     * Обрабатывает содержимое параграфа, включая текст и изображения
     */
    private fun processParagraphContent(
        element: Element,
        result: MutableList<FormattedElement>,
        imageMap: Map<String, ByteArray>
    ) {
        // Добавляем текст параграфа (используем text() вместо ownText() для захвата всех вложенных элементов)
        val text = element.text().trim()
        if (text.isNotBlank()) {
            result.add(FormattedElement.Paragraph(text))
        }

        // Обрабатываем изображения внутри параграфа отдельно, если нужно
        element.select("img").forEach { child ->
            val src = child.attr("src")
            val alt = child.attr("alt")
            if (src.isNotBlank()) {
                val normalizedSrc = normalizeImagePath(src)
                result.add(FormattedElement.Image(normalizedSrc, alt))
            }
        }
    }

    /**
     * Нормализует путь к изображению (убирает относительные пути и ../)
     */
    private fun normalizeImagePath(path: String): String {
        // Убираем начальный ../ или ./
        var normalized = path.replace(Regex("^\\.\\.?/"), "")
        // Заменяем все ../ на пустую строку
        normalized = normalized.replace("../", "")
        return normalized
    }
}

