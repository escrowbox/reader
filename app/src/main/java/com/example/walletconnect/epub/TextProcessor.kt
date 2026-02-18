package com.example.walletconnect.epub

/**
 * Обработчик текста для разбиения форматированных элементов на примитивные элементы для пагинации.
 */
class TextProcessor {

    /**
     * Элементы текста, которые использует движок пагинации.
     */
    sealed class TextElement {
        data class Word(val text: String) : TextElement()
        data class Heading(val level: Int, val text: String) : TextElement()
        data class ParagraphBreak(val count: Int = 1) : TextElement()
        data class LineBreak(val count: Int = 1) : TextElement()
        data class Image(val src: String, val alt: String = "") : TextElement()
    }

    /**
     * Преобразует структурированные элементы EPUB в последовательность элементов текста для рендера.
     */
    fun processFormattedElements(
        formattedElements: List<FormattedElement>
    ): List<TextElement> {
        if (formattedElements.isEmpty()) return emptyList()

        val elements = mutableListOf<TextElement>()

        formattedElements.forEach { formattedElement ->
            when (formattedElement) {
                is FormattedElement.Heading -> {
                    elements.add(TextElement.Heading(formattedElement.level, formattedElement.text))
                    elements.add(TextElement.ParagraphBreak(2))
                }
                is FormattedElement.Paragraph -> {
                    val sentences = splitIntoSentences(formattedElement.text)
                    sentences.forEach { sentence ->
                        val sentenceWords = sentence.trim().split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                            .map { TextElement.Word(it) }
                        elements.addAll(sentenceWords)
                    }
                    elements.add(TextElement.ParagraphBreak(1))
                }
                is FormattedElement.LineBreak -> {
                    elements.add(TextElement.LineBreak(formattedElement.count))
                }
                is FormattedElement.HorizontalRule -> {
                    elements.add(TextElement.ParagraphBreak(2))
                }
                is FormattedElement.ListItem -> {
                    formattedElement.items.forEach { item ->
                        val sentences = splitIntoSentences(item)
                        sentences.forEach { sentence ->
                            val sentenceWords = sentence.trim().split(Regex("\\s+"))
                                .filter { it.isNotBlank() }
                                .map { TextElement.Word(it) }
                            elements.addAll(sentenceWords)
                        }
                        elements.add(TextElement.LineBreak(1))
                    }
                    elements.add(TextElement.ParagraphBreak(1))
                }
                is FormattedElement.Quote -> {
                    val sentences = splitIntoSentences(formattedElement.text)
                    sentences.forEach { sentence ->
                        val sentenceWords = sentence.trim().split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                            .map { TextElement.Word(it) }
                        elements.addAll(sentenceWords)
                    }
                    elements.add(TextElement.ParagraphBreak(1))
                }
                is FormattedElement.Image -> {
                    elements.add(TextElement.Image(formattedElement.src, formattedElement.alt))
                    elements.add(TextElement.ParagraphBreak(1))
                }
            }
        }

        return elements
    }

    /**
     * Разбивает текст на предложения по знакам пунктуации.
     */
    private fun splitIntoSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val regex = Regex("(?<=[.!?][\"»”’)]?)\\s+")
        return text.split(regex)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}

