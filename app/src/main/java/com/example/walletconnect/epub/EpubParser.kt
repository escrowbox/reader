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
        val images: Map<String, ByteArray>,
        val language: String? = null
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
        val imageMap = mutableMapOf<String, ByteArray>()
        val htmlFiles = mutableListOf<String>()
        var detectedLanguage: String? = null

        while (entry != null) {
            val entryName = entry.name.lowercase()
            when {
                entryName.endsWith(".opf") -> {
                    try {
                        val opfContent = zipInputStream.bufferedReader().readText()
                        val opfDoc = Jsoup.parse(opfContent)
                        val lang = opfDoc.select("dc\\:language, language").first()?.text()
                        if (!lang.isNullOrBlank()) {
                            detectedLanguage = lang.trim().take(5)
                        }
                    } catch (_: Exception) {}
                }
                entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") || 
                entryName.endsWith(".png") || entryName.endsWith(".gif") || 
                entryName.endsWith(".webp") || entryName.endsWith(".svg") -> {
                    try {
                        val imageData = zipInputStream.readBytes()
                        imageMap[entry.name] = imageData
                    } catch (_: Exception) {}
                }
                entryName.endsWith(".html") || entryName.endsWith(".xhtml") -> {
                    try {
                        val html = zipInputStream.bufferedReader().readText()
                        htmlFiles.add(html)
                        foundHtmlFiles = true
                    } catch (_: Exception) {}
                }
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }

        zipInputStream.close()
        
        if (!foundHtmlFiles) {
            throw IllegalArgumentException("EPUB файл не содержит HTML файлов")
        }
        
        // Если язык не найден в .opf, пытаемся извлечь из первого HTML
        if (detectedLanguage == null && htmlFiles.isNotEmpty()) {
            detectedLanguage = extractLanguageFromHtml(htmlFiles.first())
        }
        
        htmlFiles.forEach { html ->
            val formattedElements = extractFormattedElementsFromHtml(html, imageMap)
            elements.addAll(formattedElements)
        }
        
        if (elements.isEmpty()) {
            throw IllegalArgumentException("Не удалось извлечь текст из EPUB файла")
        }
        
        return ParseResult(elements, imageMap, detectedLanguage)
    }

    /**
     * Извлекает язык из атрибутов HTML-документа (xml:lang или lang)
     */
    private fun extractLanguageFromHtml(html: String): String? {
        return try {
            val doc = Jsoup.parse(html)
            val htmlElement = doc.select("html").first() ?: return null
            val lang = htmlElement.attr("xml:lang").ifBlank {
                htmlElement.attr("lang")
            }
            lang.trim().takeIf { it.isNotBlank() }?.take(5)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val SHY_PLACEHOLDER = "\uFDD0"
        const val SOFT_HYPHEN = "\u00AD"
    }

    /**
     * Заменяет HTML-сущности мягкого переноса на плейсхолдер перед парсингом Jsoup,
     * т.к. Jsoup.text() удаляет \u00AD.
     */
    private fun preserveSoftHyphens(html: String): String {
        return html
            .replace("&shy;", SHY_PLACEHOLDER)
            .replace("&#173;", SHY_PLACEHOLDER)
            .replace("&#xAD;", SHY_PLACEHOLDER, ignoreCase = true)
    }

    /**
     * Восстанавливает мягкие переносы из плейсхолдера после извлечения текста.
     */
    private fun restoreSoftHyphens(text: String): String {
        return text.replace(SHY_PLACEHOLDER, SOFT_HYPHEN)
    }

    /**
     * Извлекает структурированные элементы из HTML с помощью JSoup
     */
    private fun extractFormattedElementsFromHtml(
        html: String,
        imageMap: Map<String, ByteArray> = emptyMap()
    ): List<FormattedElement> {
        val doc = Jsoup.parse(preserveSoftHyphens(html))
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
                val text = restoreSoftHyphens(element.text()).trim()
                if (text.isNotBlank()) {
                    result.add(FormattedElement.Heading(level, text))
                }
            }
            "p" -> {
                processParagraphContent(element, result, imageMap)
            }
            "img" -> {
                val src = element.attr("src")
                val alt = element.attr("alt")
                if (src.isNotBlank()) {
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
                val items = element.select("li")
                    .mapNotNull { restoreSoftHyphens(it.text()).trim() }
                    .filter { it.isNotBlank() }
                if (items.isNotEmpty()) {
                    result.add(FormattedElement.ListItem(items, element.tagName() == "ol"))
                }
            }
            "blockquote", "q" -> {
                val text = restoreSoftHyphens(element.text()).trim()
                if (text.isNotBlank()) {
                    result.add(FormattedElement.Quote(text))
                }
            }
            "div", "section", "article", "body" -> {
                element.children().forEach { child ->
                    processElement(child, result, imageMap)
                }
                val ownText = restoreSoftHyphens(element.ownText()).trim()
                if (ownText.isNotBlank()) {
                    ownText.split("\n").forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotBlank()) {
                            result.add(FormattedElement.Paragraph(trimmedLine))
                        }
                    }
                }
            }
            else -> {
                val text = restoreSoftHyphens(element.ownText()).trim()
                if (text.isNotBlank() && element.children().isEmpty()) {
                    result.add(FormattedElement.Paragraph(text))
                } else {
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
        val text = restoreSoftHyphens(element.text()).trim()
        if (text.isNotBlank()) {
            result.add(FormattedElement.Paragraph(text))
        }

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

