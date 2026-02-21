package com.example.walletconnect.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.walletconnect.ui.theme.TirtoWritterFontFamily
import com.example.walletconnect.ui.theme.NeumorphicBackground
import com.example.walletconnect.ui.theme.NeumorphicText
import com.example.walletconnect.ui.theme.NeumorphicTextSecondary
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset

private enum class InfoLang { EN, RU }

/**
 * Экран с информацией о приложении (readme)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var infoLang by remember { mutableStateOf(InfoLang.EN) }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = NeumorphicBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NeumorphicBackground,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                            .drawBehind {
                                val strokeWidth = 3.dp.toPx()
                                drawLine(
                                    color = NeumorphicTextSecondary.copy(alpha = 0.9f),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Escrow reader",
                            fontFamily = TirtoWritterFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeumorphicText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                        )
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(32.dp)
                                .clickable {
                                    infoLang = when (infoLang) {
                                        InfoLang.EN -> InfoLang.RU
                                        InfoLang.RU -> InfoLang.EN
                                    }
                                }
                        ) { }
                    }
                }
        }
    ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "Escrow Reader is an application that makes reading an interesting activity."
                        InfoLang.RU -> "Escrow Reader — это приложение, которое делает чтение интересным занятием."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )

                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "How does this work?"
                        InfoLang.RU -> "Как это работает?"
                    },
                    fontFamily = TirtoWritterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeumorphicText
                )
                
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "You make a deposit that you risk losing if you fail to complete the reading within the set deadline."
                        InfoLang.RU -> "Вы вносите депозит, который рискуете потерять, если не завершите чтение в срок."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )

                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "You determine the deposit amount and deadline yourself. A smart contract on the Solana blockchain will automatically return your deposit once it verifies that the read has completed successfully."
                        InfoLang.RU -> "Вы сами определяете сумму депозита и дедлайн. Смарт контракт на блокчейне Solana автоматически вернёт ваш депозит, когда убедится, что чтение успешно завершено."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )

                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "The application offers two reading modes to choose from: checkpoint detection and timer reset."
                        InfoLang.RU -> "Приложение предлагает на выбор, два режима чтения: поиск чекпоинтов и обнуление таймера."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )

                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "Checkpoint detection"
                        InfoLang.RU -> "Поиск чекпоинтов"
                    },
                    fontFamily = TirtoWritterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeumorphicText
                )




                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "A checkpoint is any word or sentence, for example, \"This is a checkpoint!!!\", that does not visually stand out from the surrounding text in color or font. You may define the checkpoint text at your own discretion. The application places three checkpoints at the beginning, in the middle, and at the end of the book, randomly. For the book to be considered read, you must find all of them before the deadline. If you fail to do it in time, then you will lose your deposit."
                        InfoLang.RU -> "Чекпоинт — это любое слово или предложение (например, «Это чекпоинт!»), визуально не выделяющееся из окружающего текста, цветом или шрифтом. Текст чекпоинта Вы определяете на свое усмотрение. Приложение размещает  три чекпоинта, в начале, середине и конце книги, случайным образом. Чтобы книга считалась прочитанной, нужно найти все три, до наступления дедлайна. Если не успеете, то потеряете свой депозит."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "Timer reset"
                        InfoLang.RU -> "Обнуление таймера"
                    },
                    fontFamily = TirtoWritterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeumorphicText
                )

                
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "You choose a book and set the number of hours you plan to spend on it. Once you start flipping through the pages, the timer begins. If you haven't turned the page for more than 5 minutes, the timer pauses. Reading is not required, but you must reset the timer before the deadline. If you fail to do so, you will lose your deposit."
                        InfoLang.RU -> "Вы выбираете книгу и определяете количество часов, которое хотите на нее потратить. Когда вы начинаете листать страницы, запускается таймер. Если Вы не перелистывали страницу дольше 5 минут, таймер приостанавливается, пока вы не продолжите. Читать не обязательно, но до наступления дедлайна, вы должны обнулить таймер. Если не успеете — потеряете свой депозит."
                    },
                    
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )
                
               
                

                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "Warning"
                        InfoLang.RU -> "Предупреждение"
                    },
                    fontFamily = TirtoWritterFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeumorphicText
                )
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "The app stores a unique key for each deposit in your phone's memory. Without the key, the contract will not be able to return the deposit, so do not delete the app, otherwise the keys will be irretrievably lost. Reinstalling the app will not restore it."
                        InfoLang.RU -> "Приложение хранит уникальный ключ для каждого депозита в памяти телефона. Без ключа, контракт не сможет вернуть депозит. Не удаляйте приложение, иначе ключи будут безвозвратно потеряны. Переустановка приложения их не восстановит."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )
                

                
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "The application code is open, its behavior is completely predictable, and there are no other ways to appropriate your money."
                        InfoLang.RU -> "Код приложения открыт, его поведение абсолютно предсказуемо и других способов присвоить Ваши деньги, в нем не предусмотрено."
                    },
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )
                

                
                val uriHandler = LocalUriHandler.current
                val contractUrl = "https://github.com/escrowbox/reader/tree/main/contract"
                val appUrl = "https://github.com/escrowbox/reader"
                val contractLinkText = when (infoLang) {
                    InfoLang.EN -> "contract code"
                    InfoLang.RU -> "код контракта"
                }
                val appLinkText = when (infoLang) {
                    InfoLang.EN -> "app code"
                    InfoLang.RU -> "код приложения"
                }
                val linkStyle = SpanStyle(
                    color = NeumorphicText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                val contractAnnotated = buildAnnotatedString {
                    pushStringAnnotation(tag = "URL", annotation = contractUrl)
                    pushStyle(linkStyle)
                    append(contractLinkText)
                    pop()
                    pop()
                }
                val appAnnotated = buildAnnotatedString {
                    pushStringAnnotation(tag = "URL", annotation = appUrl)
                    pushStyle(linkStyle)
                    append(appLinkText)
                    pop()
                    pop()
                }
                ClickableText(
                    text = contractAnnotated,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicText
                    ),
                    onClick = { offset ->
                        contractAnnotated.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { uriHandler.openUri(it.item) }
                    }
                )
                ClickableText(
                    text = appAnnotated,
                    modifier = Modifier.padding(bottom = 40.dp),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 16.sp,
                        color = NeumorphicText
                    ),
                    onClick = { offset ->
                        appAnnotated.getStringAnnotations(
                            tag = "URL",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { uriHandler.openUri(it.item) }
                    }
                )
            }
        }
    }
}