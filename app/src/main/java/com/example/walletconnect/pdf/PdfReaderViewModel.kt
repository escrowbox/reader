package com.example.walletconnect.pdf

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walletconnect.utils.CheckpointIndexStore
import com.example.walletconnect.utils.TimerContractStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PdfReaderViewModel : ViewModel() {

    companion object {
        private const val PREFS_NAME = "pdf_reader_prefs"
        private const val KEY_VERTICAL_MODE = "is_vertical_mode"
    }

    var totalPages by mutableStateOf(0)
        private set
    var currentPage by mutableStateOf(0)
        private set
    var isVerticalMode by mutableStateOf(false)
        private set

    fun toggleMode(context: Context) {
        isVerticalMode = !isVerticalMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VERTICAL_MODE, isVerticalMode).apply()
    }

    private fun restoreMode(context: Context) {
        isVerticalMode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VERTICAL_MODE, false)
    }

    private var currentBoxId: String = ""
    private var lastSavedPage: Int = -1

    // Timer fields — mirror EpubReaderViewModel
    private var timerJob: Job? = null
    var remainingSeconds by mutableStateOf<Long?>(null)
        private set

    private var hasSwipeControl = false
    private var lastSwipeTime = System.currentTimeMillis()
    private var isTimerPaused = false

    private var hasHandControl = false
    private var isHandControlPaused = false

    private var isScreenPaused = false

    fun initialize(context: Context, boxId: String, pageCount: Int) {
        currentBoxId = boxId
        totalPages = pageCount
        restoreMode(context)

        val savedPage = if (boxId.isNotEmpty()) {
            CheckpointIndexStore.getCurrentPage(context, boxId).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        } else 0
        currentPage = savedPage
        lastSavedPage = savedPage

        CheckpointIndexStore.saveTotalPages(context, boxId, pageCount)

        val timerParams = TimerContractStore.getTimerParams(context, boxId)
        if (timerParams != null) {
            remainingSeconds = TimerContractStore.getRemainingSeconds(context, boxId)
            hasSwipeControl = timerParams.swipeControl
            hasHandControl = timerParams.handControl
            lastSwipeTime = System.currentTimeMillis()
            isTimerPaused = false
            isHandControlPaused = false
            isScreenPaused = false
            startTimer(context, boxId)
        }
    }

    private fun startTimer(context: Context, boxId: String) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (remainingSeconds != null && remainingSeconds!! > 0) {
                delay(1000)
                if (hasSwipeControl) {
                    val timeSinceLastSwipe = (System.currentTimeMillis() - lastSwipeTime) / 1000
                    if (timeSinceLastSwipe > 300) {
                        isTimerPaused = true
                        continue
                    } else {
                        isTimerPaused = false
                    }
                }
                if (!isTimerPaused && !isHandControlPaused && !isScreenPaused && remainingSeconds != null && remainingSeconds!! > 0) {
                    val newSeconds = remainingSeconds!! - 1
                    remainingSeconds = newSeconds
                    TimerContractStore.saveRemainingSeconds(context, boxId, newSeconds)
                }
            }
            if (remainingSeconds != null && remainingSeconds!! == 0L) {
                TimerContractStore.saveRemainingSeconds(context, boxId, 0L)
            }
        }
    }

    fun onActivityDetected() {
        if (hasSwipeControl) {
            lastSwipeTime = System.currentTimeMillis()
            isTimerPaused = false
        }
    }

    fun setMotionState(motionState: com.example.walletconnect.sensors.MotionDetector.MotionState?) {
        if (hasHandControl) {
            isHandControlPaused = motionState == com.example.walletconnect.sensors.MotionDetector.MotionState.STATIONARY
        }
    }

    fun setScreenPaused(paused: Boolean) {
        isScreenPaused = paused
    }

    fun stopTimer(context: Context) {
        timerJob?.cancel()
        timerJob = null
        if (currentBoxId.isNotEmpty() && remainingSeconds != null) {
            TimerContractStore.saveRemainingSeconds(context, currentBoxId, remainingSeconds!!)
        }
    }

    fun goToPage(context: Context, pageIndex: Int) {
        val validIndex = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        currentPage = validIndex
        if (currentBoxId.isNotEmpty() && validIndex != lastSavedPage) {
            CheckpointIndexStore.saveCurrentPage(context, currentBoxId, validIndex)
            CheckpointIndexStore.saveTotalPages(context, currentBoxId, totalPages)
            lastSavedPage = validIndex
        }
    }

    fun goToHome(context: Context) {
        goToPage(context, currentPage)
        currentBoxId = ""
        lastSavedPage = -1
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
