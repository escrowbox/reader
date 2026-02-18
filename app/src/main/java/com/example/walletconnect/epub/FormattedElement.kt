package com.example.walletconnect.epub

/**
 * Структурированные элементы текста с форматированием
 */
sealed class FormattedElement {
    /**
     * Заголовок уровня 1-6
     */
    data class Heading(
        val level: Int,
        val text: String
    ) : FormattedElement()

    /**
     * Абзац текста
     */
    data class Paragraph(
        val text: String
    ) : FormattedElement()

    /**
     * Разрыв строки
     */
    data class LineBreak(
        val count: Int = 1
    ) : FormattedElement()

    /**
     * Горизонтальная линия (разделитель)
     */
    object HorizontalRule : FormattedElement()

    /**
     * Список (упорядоченный или неупорядоченный)
     */
    data class ListItem(
        val items: kotlin.collections.List<String>,
        val isOrdered: Boolean = false
    ) : FormattedElement()

    /**
     * Цитата
     */
    data class Quote(
        val text: String
    ) : FormattedElement()

    /**
     * Изображение
     */
    data class Image(
        val src: String, // Путь к изображению в EPUB архиве
        val alt: String = "" // Альтернативный текст
    ) : FormattedElement()
}








