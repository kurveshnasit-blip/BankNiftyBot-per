package com.banknifty.bot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class TradingService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var strategy: StrategyEngine
    private lateinit var broker: BrokerAPI
    private lateinit var settings: AppSettings
    private var pollingJob: Job? = null
    
    companion object {
        private const val CHANNEL_ID = "BankNiftyBotChannel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "TradingService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔧 Service created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚀 Service STARTED")
        
        initializeComponents()
        startForeground(NOTIFICATION_ID, createNotification("Initializing strategy..."))
        startTradingLoop()
        
        return START_STICKY  // Auto-restart if killed
    }
    
    private fun initializeComponents() {
        settings = AppSettings.load(this)
        broker = when (settings.broker) {
            AppSettings.BrokerType.ZERODHA -> ZerodhaAPI()
            AppSettings.BrokerType.UPSTOX -> UpstoxAPI()
        }
        strategy = StrategyEngine(this, broker, settings)
        
        Log.d(TAG, "⚙️ Initialized: ${settings.broker} | ${settings.lots} lots | ${settings.mode}")
    }
    
    private fun startTradingLoop() {
        pollingJob = serviceScope.launch {
            Log.d(TAG, "⏰ Starting 3min polling loop")
            
            while (isActive && shouldContinueTrading()) {
                try {
                    val currentTime = System.currentTimeMillis()
                    
                    // Daily reset at 9:00 AM
                    if (isMarketOpenTime(currentTime)) {
                        strategy.resetDaily()
                    }
                    
                    // Fetch and process candle
                    val candle = fetchCurrentCandle()
                    strategy.process3MinCandle(candle, currentTime)
                    
                    // Update notification
                    updateNotification("Market: ${candle.close} | ${strategy.stateString}")
                    
                    delay(180000)  // 3 minutes exactly
                
                } catch (e: Exception) {
                    Log.e(TAG, "Trading loop error", e)
                    updateNotification("Error: ${e.message}")
                    delay(60000)  // Retry in 1 minute
                }
            }
            
            Log.d(TAG, "⏹️ Trading loop ended")
        }
    }
    
    private suspend fun fetchCurrentCandle(): Candle {
        return if (settings.mode == AppSettings.Mode.SIMULATION) {
            generateSimulationCandle()
        } else {
            fetchLiveCandle()
        }
    }
    
    private suspend fun generateSimulationCandle(): Candle {
        val basePrice = 51000.0 + (kotlin.random.Random.nextDouble() * 400 - 200)
        val range = 40.0 + kotlin.random.Random.nextDouble() * 40
        val candleTime = System.currentTimeMillis()
        
        return Candle(
            open = basePrice,
            high = basePrice + range,
            low = basePrice - range * 0.7,
            close = basePrice + (kotlin.random.Random.nextDouble() * range * 2 - range),
            timestamp = candleTime
        )
    }
    
    private suspend fun fetchLiveCandle(): Candle {
        val spotPrice = broker.getBankNiftySpot()
        val candleData = broker.getLatest3MinCandle("NSE:NIFTY BANK")
        
        return Candle(
            open = candleData.open,
            high = candleData.high,
            low = candleData.low,
            close = spotPrice,
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun shouldContinueTrading(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        // Market hours: Mon-Fri 9:15-15:20
        val inMarketHours = dayOfWeek in 2..6 && 
            ((hour == 9 && minute >= 15) || 
             (hour in 10..14) || 
             (hour == 15 && minute <= 20))
        
        return inMarketHours
    }
    
    private fun isMarketOpenTime(currentTime: Long): Boolean {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = sdf.format(Date(currentTime))
        val hourMin = timeStr.split(":")
        val hour = hourMin[0].toInt()
        
        return hour == 9 && hourMin[1].toInt() == 0  // 9:00 AM daily reset
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BankNifty Trading Bot",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for automated trading"
                setShowBadge(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🤖 BankNifty Bot")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)  // Replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        pollingJob?.cancel()
        Log.d(TAG, "🛑 Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}