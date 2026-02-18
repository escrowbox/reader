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
import androidx.compose.ui.draw.shadow

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                            spotColor = Color.White.copy(alpha = 0.5f)
                        ),
                    color = NeumorphicBackground,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
                    .padding(top = 16.dp, bottom = 0.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                
                Text(
                    text = when (infoLang) {
                        InfoLang.EN -> "Escrow Reader is a Solana dApp that gives reading real meaning."
                        InfoLang.RU -> "Escrow Reader — это Solana dApp, которое наделяет чтение настоящим смыслом."
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
                        InfoLang.RU -> "Приложении предлагает на выбор, два режима чтения: поиск чекпоинтов и обнуление таймера."
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
                        InfoLang.EN -> "You choose a book and set the number of hours you plan to spend on it. Once you start flipping through the pages, the timer begins. When you stop flipping, the timer pauses. Reading is not required, but you must reset the timer before the deadline. If you fail to do so, you will lose your deposit."
                        InfoLang.RU -> "Вы выбираете книгу и определяете количество часов, которое хотите на нее потратить. Когда вы начинаете листать страницы, запускается таймер. Когда перестаете листать, таймер останавливается. Читать не обязательно, но до наступления дедлайна, вы должны обнулить таймер. Если не успеете — потеряете свой депозит."
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
                val contractUrl = "https://github.com/escrowbox/reader/contract"
                val appUrl = "https://github.com/escrowbox/reader"
                val linkStyle = SpanStyle(
                    color = Color(0xFFDEB887)
                )
                val contractLinkText = when (infoLang) {
                    InfoLang.EN -> "contract code on Github"
                    InfoLang.RU -> "код контракта на Github"
                }
                val appLinkText = when (infoLang) {
                    InfoLang.EN -> "app code on Github"
                    InfoLang.RU -> "код приложения на Github"
                }
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
                val baseTextStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = TirtoWritterFontFamily,
                    fontSize = 14.sp,
                    color = NeumorphicTextSecondary
                )
                ClickableText(
                    text = contractAnnotated,
                    modifier = Modifier.padding(bottom = 10.dp),
                    style = baseTextStyle,
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
                    style = baseTextStyle,
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