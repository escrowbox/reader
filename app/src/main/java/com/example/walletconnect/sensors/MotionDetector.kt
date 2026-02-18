package com.example.walletconnect.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Детектор движения телефона на основе акселерометра
 * 
 * Использует анализ стандартного отклонения модуля ускорения
 * для определения состояния: STATIONARY (неподвижно) или MOVING (движется)
 */
class MotionDetector(context: Context) : SensorEventListener {
    
    enum class MotionState {
        STATIONARY,  // Телефон лежит неподвижно
        MOVING       // Телефон движется
    }
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // Параметры алгоритма
    private val samplingRateHz = 50 // Частота дискретизации ≥ 50 Hz
    private val windowSizeSeconds = 5 // Размер окна анализа
    private val windowStepSeconds = 1 // Шаг смещения окна
    private val threshold = 0.15 // Порог для std(a) (подобран экспериментально)
    private val stationaryWindowsRequired = 300 // Количество окон подряд для STATIONARY (60 секунд = 1 минута)
    
    // Буфер для хранения данных акселерометра
    private val samplesPerWindow = samplingRateHz * windowSizeSeconds
    private val accelerationBuffer = mutableListOf<Double>()
    
    // Счетчик последовательных "тихих" окон
    private var stationaryWindowsCount = 0
    
    // Текущее состояние
    private val _motionState = MutableStateFlow(MotionState.MOVING)
    val motionState: StateFlow<MotionState> = _motionState.asStateFlow()
    
    // Дополнительная информация для отладки
    private val _currentStd = MutableStateFlow(0.0)
    val currentStd: StateFlow<Double> = _currentStd.asStateFlow()
    
    private var isRunning = false
    private var lastProcessTime = 0L
    
    /**
     * Запуск детектора
     */
    fun start() {
        if (isRunning) return
        
        accelerometer?.let {
            // Регистрируем слушатель с частотой ~50 Hz (20000 мкс = 50 Hz)
            val samplingPeriodUs = (1_000_000 / samplingRateHz)
            sensorManager.registerListener(this, it, samplingPeriodUs)
            isRunning = true
            lastProcessTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Остановка детектора
     */
    fun stop() {
        if (!isRunning) return
        
        sensorManager.unregisterListener(this)
        isRunning = false
        accelerationBuffer.clear()
        stationaryWindowsCount = 0
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // Вычисляем модуль ускорения: a = sqrt(x² + y² + z²)
        val magnitude = sqrt(x.pow(2) + y.pow(2) + z.pow(2))
        
        // Добавляем в буфер
        accelerationBuffer.add(magnitude.toDouble())
        
        // Ограничиваем размер буфера
        if (accelerationBuffer.size > samplesPerWindow) {
            accelerationBuffer.removeAt(0)
        }
        
        // Обрабатываем окно каждую секунду
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime >= windowStepSeconds * 1000) {
            lastProcessTime = currentTime
            processWindow()
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не требуется для данной задачи
    }
    
    /**
     * Обработка скользящего окна данных
     */
    private fun processWindow() {
        if (accelerationBuffer.size < samplesPerWindow) {
            // Недостаточно данных для анализа
            return
        }
        
        // Убираем гравитацию путем вычитания среднего значения
        val mean = accelerationBuffer.average()
        val detrended = accelerationBuffer.map { it - mean }
        
        // Вычисляем стандартное отклонение
        val variance = detrended.map { it.pow(2) }.average()
        val std = sqrt(variance)
        
        _currentStd.value = std
        
        // Проверяем порог
        if (std < threshold) {
            stationaryWindowsCount++
        } else {
            stationaryWindowsCount = 0
        }
        
        // Обновляем состояние
        val newState = if (stationaryWindowsCount >= stationaryWindowsRequired) {
            MotionState.STATIONARY
        } else {
            MotionState.MOVING
        }
        
        if (_motionState.value != newState) {
            _motionState.value = newState
        }
    }
    
    /**
     * Получить текущее состояние
     */
    fun getCurrentState(): MotionState = _motionState.value
    
    /**
     * Проверить, запущен ли детектор
     */
    fun isRunning(): Boolean = isRunning
}

