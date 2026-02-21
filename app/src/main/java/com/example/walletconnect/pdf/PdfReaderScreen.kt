package com.example.walletconnect.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walletconnect.sensors.MotionDetector
import com.example.walletconnect.utils.TimerContractStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val RdrBgTop      = Color(0xFFF6F9FE)
private val RdrBgMid      = Color(0xFFEEF3FB)
private val RdrBgBot      = Color(0xFFE6EDF8)
private val RdrSurface    = Color(0xFFEDF1F8)
private val RdrBorderLo   = Color(0xFFBDCADB)
private val RdrBorderHi   = Color(0xFFF4F7FC)
private val RdrNavy       = Color(0xFF2D3A4F)
private val RdrNavyDark   = Color(0xFF1E2D3D)
private val RdrNavyMid    = Color(0xFF3D5166)
private val RdrTextLo     = Color(0xFF8896A8)
private val RdrBgBrush    = Brush.verticalGradient(listOf(RdrBgTop, RdrBgMid, RdrBgBot))
private val RdrAccentBrush = Brush.linearGradient(listOf(RdrNavyDark, RdrNavyMid))
private val RdrBorderBrush = Brush.linearGradient(listOf(RdrBorderHi, RdrBorderLo))
private val ShadowAmbient = Color(0x22000000)
private val ShadowSpot    = Color(0x2E000000)

@Composable
private fun getMotionState(motionDetector: MotionDetector?): MotionDetector.MotionState? {
    return motionDetector?.let {
        val state by it.motionState.collectAsState()
        state
    }
}

@Composable
fun PdfReaderScreen(
    pdfFile: File,
    boxId: String,
    onBack: () -> Unit,
    viewModel: PdfReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val pdfRenderer = remember {
        val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd)
    }

    DisposableEffect(Unit) {
        onDispose { pdfRenderer.close() }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context, boxId, pdfRenderer.pageCount)
    }

    val remainingSeconds = viewModel.remainingSeconds

    val timerParams = remember(boxId) {
        TimerContractStore.getTimerParams(context, boxId)
    }
    val hasHandControl = timerParams?.handControl == true

    val motionDetector = remember(hasHandControl) {
        if (hasHandControl) MotionDetector(context) else null
    }
    val motionState = getMotionState(motionDetector)

    DisposableEffect(hasHandControl, motionDetector) {
        if (hasHandControl && motionDetector != null) motionDetector.start()
        onDispose { motionDetector?.stop() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.setScreenPaused(true)
                Lifecycle.Event.ON_RESUME -> viewModel.setScreenPaused(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(motionState) { viewModel.setMotionState(motionState) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopTimer(context) }
    }

    BackHandler {
        viewModel.goToHome(context)
        viewModel.stopTimer(context)
        onBack()
    }

    val totalPages = viewModel.totalPages
    val currentPage = viewModel.currentPage
    val safePageCount = totalPages.coerceAtLeast(1)

    PdfPageContent(
        pdfRenderer = pdfRenderer,
        currentPage = currentPage,
        totalPages = safePageCount,
        isVerticalMode = viewModel.isVerticalMode,
        onToggleMode = { viewModel.toggleMode(context) },
        onActivityDetected = { viewModel.onActivityDetected() },
        onGoToPage = { viewModel.goToPage(context, it) },
        onBack = {
            viewModel.goToHome(context)
            viewModel.stopTimer(context)
            onBack()
        },
        remainingSeconds = remainingSeconds,
        motionState = if (hasHandControl) motionState else null
    )
}

// ─── Main content ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPageContent(
    pdfRenderer: PdfRenderer,
    currentPage: Int,
    totalPages: Int,
    isVerticalMode: Boolean,
    onToggleMode: () -> Unit,
    onActivityDetected: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onBack: () -> Unit,
    remainingSeconds: Long?,
    motionState: MotionDetector.MotionState?
) {
    var showSlider by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }

    LaunchedEffect(currentPage) { sliderValue = currentPage.toFloat() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8E8E8))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isVerticalMode) {
                    VerticalPdfContent(
                        pdfRenderer = pdfRenderer,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onActivityDetected = onActivityDetected,
                        onGoToPage = onGoToPage,
                        onToggleSlider = { showSlider = !showSlider }
                    )
                } else {
                    HorizontalPdfContent(
                        pdfRenderer = pdfRenderer,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onActivityDetected = onActivityDetected,
                        onGoToPage = onGoToPage,
                        onToggleSlider = { showSlider = !showSlider }
                    )
                }
            }

            val displayPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "${displayPage + 1}/$totalPages",
                    modifier = Modifier.align(Alignment.End),
                    style = TextStyle(fontSize = 10.sp, color = Color.Gray)
                )
                LinearProgressIndicator(
                    progress = { if (totalPages > 0) (displayPage + 1).toFloat() / totalPages else 0f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color.Gray,
                    trackColor = Color.LightGray.copy(alpha = 0.3f),
                )
            }
        }

        AnimatedVisibility(
            visible = showSlider,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it }
        ) {
            PdfTopBar(
                isVerticalMode = isVerticalMode,
                onToggleMode = onToggleMode,
                onBack = { showSlider = false; onBack() },
                remainingSeconds = remainingSeconds,
                motionState = motionState
            )
        }

        AnimatedVisibility(
            visible = showSlider && !isVerticalMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            PdfBottomSlider(
                sliderValue = sliderValue,
                totalPages = totalPages,
                onSliderChange = { sliderValue = it },
                onSliderFinished = {
                    onGoToPage(sliderValue.roundToInt())
                    showSlider = false
                }
            )
        }
    }
}

// ─── Horizontal pager mode ───────────────────────────────────────────────────

@Composable
private fun HorizontalPdfContent(
    pdfRenderer: PdfRenderer,
    currentPage: Int,
    totalPages: Int,
    onActivityDetected: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleSlider: () -> Unit
) {
    var isZoomed by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)),
        pageCount = { totalPages }
    )

    var lastExternalPage by remember { mutableIntStateOf(currentPage) }

    LaunchedEffect(currentPage, totalPages) {
        val target = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        lastExternalPage = target
        if (pagerState.currentPage != target) {
            if (kotlin.math.abs(pagerState.currentPage - target) > 5) pagerState.scrollToPage(target)
            else pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { settledPage ->
            if (settledPage != lastExternalPage) {
                lastExternalPage = settledPage
                onActivityDetected()
                onGoToPage(settledPage)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        userScrollEnabled = !isZoomed,
        key = { it }
    ) { pageIndex ->
        ZoomablePdfPage(
            pdfRenderer = pdfRenderer,
            pageIndex = pageIndex,
            onZoomChanged = { isZoomed = it },
            onTap = { onActivityDetected(); onToggleSlider() },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

// ─── Vertical scroll mode (zoomable) ─────────────────────────────────────────

@Composable
private fun VerticalPdfContent(
    pdfRenderer: PdfRenderer,
    currentPage: Int,
    totalPages: Int,
    onActivityDetected: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleSlider: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(currentPage) {
        if (listState.firstVisibleItemIndex != currentPage) {
            listState.animateScrollToItem(currentPage)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest { firstVisible ->
            onGoToPage(firstVisible)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }.collectLatest {
            onActivityDetected()
        }
    }

    fun clampX(s: Float, ox: Float): Float {
        if (s <= 1f) return 0f
        val maxX = (containerSize.width * (s - 1f)) / 2f
        return ox.coerceIn(-maxX, maxX)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f; offsetX = 0f
                        } else {
                            val newScale = DOUBLE_TAP_ZOOM
                            val focusX = tapOffset.x - containerSize.width / 2f
                            scale = newScale
                            offsetX = clampX(newScale, -focusX * (newScale - 1f))
                        }
                        onActivityDetected()
                        onToggleSlider()
                    },
                    onTap = { onActivityDetected(); onToggleSlider() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            scale = newScale
                            offsetX = clampX(newScale, offsetX + pan.x)
                            onActivityDetected()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1.05f) {
                            val pan = event.calculatePan()
                            if (abs(pan.x) > 0.5f) {
                                offsetX = clampX(scale, offsetX + pan.x)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offsetX
                },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(count = totalPages, key = { it }) { pageIndex ->
                PdfPageBitmap(
                    pdfRenderer = pdfRenderer,
                    pageIndex = pageIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                )
            }
        }
    }
}

@Composable
private fun PdfPageBitmap(
    pdfRenderer: PdfRenderer,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                val renderScale = 2
                val bmp = Bitmap.createBitmap(
                    page.width * renderScale,
                    page.height * renderScale,
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            modifier = modifier,
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(
            modifier = modifier.height(500.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

// ─── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun PdfTopBar(
    isVerticalMode: Boolean,
    onToggleMode: () -> Unit,
    onBack: () -> Unit,
    remainingSeconds: Long?,
    motionState: MotionDetector.MotionState?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp), clip = false, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(RdrBgBrush)
            .border(1.dp, RdrBorderBrush, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
                Text(text = "back")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val context = LocalContext.current
                val modeIcon = remember(isVerticalMode) {
                    val path = if (isVerticalMode) "icons/horisontal.png" else "icons/vertical.png"
                    BitmapFactory.decodeStream(context.assets.open(path))
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(4.dp, RoundedCornerShape(10.dp), ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                        .background(RdrSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, RdrBorderLo, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleMode() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = modeIcon.asImageBitmap(),
                        contentDescription = if (isVerticalMode) "horizontal mode" else "vertical mode",
                        modifier = Modifier.size(22.dp)
                    )
                }

                motionState?.let { state ->
                    val motionIcon = remember(state) {
                        val path = when (state) {
                            MotionDetector.MotionState.STATIONARY -> "icons/stationary.png"
                            MotionDetector.MotionState.MOVING -> "icons/moving.png"
                        }
                        BitmapFactory.decodeStream(context.assets.open(path))
                    }
                    Image(
                        bitmap = motionIcon.asImageBitmap(),
                        contentDescription = when (state) {
                            MotionDetector.MotionState.STATIONARY -> "stationary"
                            MotionDetector.MotionState.MOVING -> "moving"
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                remainingSeconds?.let { seconds ->
                    if (seconds >= 0) {
                        val h = seconds / 3600
                        val m = (seconds % 3600) / 60
                        val s = seconds % 60
                        Text(
                            text = String.format("%02d:%02d:%02d", h, m, s),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (seconds <= 0) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ─── Bottom slider ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfBottomSlider(
    sliderValue: Float,
    totalPages: Int,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), clip = false, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
            .background(RdrBgBrush, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .border(1.dp, RdrBorderBrush, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAGE",
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = RdrNavy)
                )
                Text(
                    text = "${sliderValue.roundToInt() + 1}  /  $totalPages",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = RdrTextLo)
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = RdrNavy,
                    activeTrackColor = RdrNavy,
                    inactiveTrackColor = RdrBorderLo,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .shadow(6.dp, CircleShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
                            .background(RdrAccentBrush, CircleShape)
                    )
                }
            )
        }
    }
}

// ─── Zoomable page (horizontal mode) ─────────────────────────────────────────

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
private fun ZoomablePdfPage(
    pdfRenderer: PdfRenderer,
    pageIndex: Int,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                val renderScale = 3
                val bmp = Bitmap.createBitmap(
                    page.width * renderScale,
                    page.height * renderScale,
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pageIndex) {
        scale = 1f; offsetX = 0f; offsetY = 0f
        onZoomChanged(false)
    }

    fun clampOffset(s: Float, ox: Float, oy: Float): Pair<Float, Float> {
        if (s <= 1f) return 0f to 0f
        val maxX = (containerSize.width * (s - 1f)) / 2f
        val maxY = (containerSize.height * (s - 1f)) / 2f
        return ox.coerceIn(-maxX, maxX) to oy.coerceIn(-maxY, maxY)
    }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .clipToBounds()
            .pointerInput(pageIndex) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                            onZoomChanged(false)
                        } else {
                            val newScale = DOUBLE_TAP_ZOOM
                            val focusX = tapOffset.x - containerSize.width / 2f
                            val focusY = tapOffset.y - containerSize.height / 2f
                            val (cx, cy) = clampOffset(newScale, -focusX * (newScale - 1f), -focusY * (newScale - 1f))
                            scale = newScale; offsetX = cx; offsetY = cy
                            onZoomChanged(true)
                        }
                        onTap()
                    },
                    onTap = { onTap() }
                )
            }
            .pointerInput(pageIndex) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            val (cx, cy) = clampOffset(newScale, offsetX + pan.x, offsetY + pan.y)
                            scale = newScale; offsetX = cx; offsetY = cy
                            onZoomChanged(newScale > 1.05f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1.05f) {
                            val pan = event.calculatePan()
                            if (abs(pan.x) > 0.5f || abs(pan.y) > 0.5f) {
                                val (cx, cy) = clampOffset(scale, offsetX + pan.x, offsetY + pan.y)
                                offsetX = cx; offsetY = cy
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offsetX; translationY = offsetY
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator()
        }
    }
}
