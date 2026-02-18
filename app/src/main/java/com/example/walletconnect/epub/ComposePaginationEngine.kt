package com.example.walletconnect.epub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/**
 * Срез страницы - содержит диапазон символов из общего текста
 */
data class PageSlice(
    val startIndex: Int,      // Индекс первого символа (inclusive)
    val endIndex: Int,        // Индекс последнего символа (exclusive)
    val startLine: Int,       // Первая строка на странице
    val endLine: Int,         // Последняя строка (exclusive)
    val pageNumber: Int       // Номер страницы
)

/**
 * Результат пагинации
 */
data class PaginationResult(
    val fullText: AnnotatedString,           // Весь текст книги как AnnotatedString
    val pages: List<PageSlice>,              // Срезы по страницам
    val textStyle: TextStyle,                // Стиль текста (должен быть идентичен при рендеринге)
    val imageMarkers: Map<IntRange, String>, // Маркеры изображений (диапазон -> src)
    val chapterStartIndices: Set<Int>        // Индексы начала глав (для разрыва страницы)
)

/**
 * Движок пагинации на основе Compose TextLayoutResult
 */
class ComposePaginationEngine {

    /**
     * Главная функция пагинации
     */
    fun paginate(
        elements: List<TextProcessor.TextElement>,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        pageWidth: Float,
        pageHeight: Float,
        checkpointIndices: List<Int> = emptyList(),
        foundCheckpointIndices: Set<Int> = emptySet(),
        checkpointLabel: String = " [I find checkpoint] "
    ): PaginationResult {
        val textResult = buildAnnotatedText(
            elements = elements,
            checkpointIndices = checkpointIndices,
            foundCheckpointIndices = foundCheckpointIndices,
            checkpointLabel = checkpointLabel
        )
        
        val pages = paginateByLayout(
            text = textResult.text,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            chapterStartIndices = textResult.chapterStartIndices
        )
        
        return PaginationResult(
            fullText = textResult.text,
            pages = pages,
            textStyle = textStyle,
            imageMarkers = textResult.imageMarkers,
            chapterStartIndices = textResult.chapterStartIndices
        )
    }

    private data class AnnotatedTextResult(
        val text: AnnotatedString,
        val imageMarkers: Map<IntRange, String>,
        val chapterStartIndices: Set<Int>
    )

    /**
     * Собирает единый AnnotatedString из элементов текста, помечая заголовки и места картинок
     * и вставляя кликабельные чекпоинты по заранее вычисленным индексам.
     * Найденные чекпоинты отображаются зеленым цветом и не кликабельны.
     */
    private fun buildAnnotatedText(
        elements: List<TextProcessor.TextElement>,
        checkpointIndices: List<Int>,
        foundCheckpointIndices: Set<Int>,
        checkpointLabel: String
    ): AnnotatedTextResult {
        val imageMarkers = mutableMapOf<IntRange, String>()
        val chapterStartIndices = mutableSetOf<Int>()

        val sortedCheckpoints = checkpointIndices.sorted()
        var checkpointPointer = 0

        val annotatedString = buildAnnotatedString {
            var isFirstWordInParagraph = true

            fun insertPendingCheckpoints() {
                while (checkpointPointer < sortedCheckpoints.size &&
                    length >= sortedCheckpoints[checkpointPointer]
                ) {
                    val checkpointIndex = sortedCheckpoints[checkpointPointer]
                    val isFound = checkpointIndex in foundCheckpointIndices
                    val startPos = length

                    // Применяем стиль в зависимости от того, найден чекпоинт или нет
                    withStyle(
                        SpanStyle(
                            color = if (isFound) Color(0xFF4CAF50) else Color.Black // Зеленый для найденных, черный для ненайденных
                        )
                    ) {
                        append(checkpointLabel)
                    }

                    // Добавляем аннотацию только для ненайденных чекпоинтов (чтобы они были кликабельны)
                    if (!isFound) {
                        addStringAnnotation(
                            tag = "checkpoint",
                            annotation = checkpointIndex.toString(), // Сохраняем оригинальный индекс для обработки клика
                            start = startPos,
                            end = startPos + checkpointLabel.length
                        )
                    }

                    checkpointPointer++
                }
            }

            elements.forEach { element ->
                insertPendingCheckpoints()

                when (element) {
                    is TextProcessor.TextElement.Word -> {
                        if (!isFirstWordInParagraph) {
                            append(" ")
                        }
                        append(element.text)
                        isFirstWordInParagraph = false
                    }

                    is TextProcessor.TextElement.Heading -> {
                        if (length > 0) {
                            append("\n\n")
                        }
                        chapterStartIndices.add(length)
                        withStyle(
                            SpanStyle(
                                fontSize = when (element.level) {
                                    1 -> 28.sp
                                    2 -> 24.sp
                                    3 -> 22.sp
                                    else -> 20.sp
                                },
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(element.text)
                        }
                        append("\n\n")
                        isFirstWordInParagraph = true
                    }

                    is TextProcessor.TextElement.ParagraphBreak -> {
                        append("\n".repeat(element.count.coerceAtLeast(1)))
                        isFirstWordInParagraph = true
                    }

                    is TextProcessor.TextElement.LineBreak -> {
                        append("\n".repeat(element.count.coerceAtLeast(1)))
                        isFirstWordInParagraph = true
                    }

                    is TextProcessor.TextElement.Image -> {
                        // Изображения пока не отображаем, только помечаем позиции при необходимости
                    }
                }
            }

            // На всякий случай вставляем оставшиеся чекпоинты в конец
            insertPendingCheckpoints()
        }

        return AnnotatedTextResult(
            text = annotatedString,
            imageMarkers = imageMarkers,
            chapterStartIndices = chapterStartIndices
        )
    }

    private fun paginateByLayout(
        text: AnnotatedString,
        textMeasurer: TextMeasurer,
        textStyle: TextStyle,
        pageWidth: Float,
        pageHeight: Float,
        chapterStartIndices: Set<Int> = emptySet()
    ): List<PageSlice> {
        if (text.isEmpty()) return emptyList()
        
        val layoutResult = textMeasurer.measure(
            text = text,
            style = textStyle,
            constraints = Constraints(maxWidth = pageWidth.toInt())
        )
        
        val pages = mutableListOf<PageSlice>()
        var currentPageStartLine = 0
        var currentPageStartIndex = 0
        var accumulatedHeight = 0f
        
        for (lineIndex in 0 until layoutResult.lineCount) {
            val lineStart = layoutResult.getLineStart(lineIndex)
            val lineTop = layoutResult.getLineTop(lineIndex)
            val lineBottom = layoutResult.getLineBottom(lineIndex)
            val lineHeight = lineBottom - lineTop
            
            val isChapterStart = chapterStartIndices.contains(lineStart)
            val hasContentOnPage = currentPageStartLine < lineIndex
            
            if (isChapterStart && hasContentOnPage) {
                pages.add(PageSlice(
                    startIndex = currentPageStartIndex,
                    endIndex = lineStart,
                    startLine = currentPageStartLine,
                    endLine = lineIndex,
                    pageNumber = pages.size
                ))
                currentPageStartLine = lineIndex
                currentPageStartIndex = lineStart
                accumulatedHeight = lineHeight
                continue
            }
            
            val wouldExceedPage = accumulatedHeight + lineHeight > pageHeight
            
            if (wouldExceedPage && hasContentOnPage) {
                val pageEndIndex = lineStart
                pages.add(PageSlice(
                    startIndex = currentPageStartIndex,
                    endIndex = pageEndIndex,
                    startLine = currentPageStartLine,
                    endLine = lineIndex,
                    pageNumber = pages.size
                ))
                currentPageStartLine = lineIndex
                currentPageStartIndex = pageEndIndex
                accumulatedHeight = lineHeight
            } else {
                accumulatedHeight += lineHeight
            }
        }
        
        if (currentPageStartLine < layoutResult.lineCount) {
            pages.add(PageSlice(
                startIndex = currentPageStartIndex,
                endIndex = text.length,
                startLine = currentPageStartLine,
                endLine = layoutResult.lineCount,
                pageNumber = pages.size
            ))
        }
        
        return pages
    }
}

