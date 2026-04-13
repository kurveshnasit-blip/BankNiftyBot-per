package com.banknifty.bot

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StrategyEngine(
    private val context: Context,
    private val broker: BrokerAPI,
    private val settings: AppSettings
) {
    
    private var tradeState = TradeState.NONE
    private var morningHigh = 0.0
    private var morningLow = Double.MAX_VALUE
    private var upperLine = 0.0
    private var lowerLine = 0.0
    private var currentPosition: Position? = null
    private var checkpoints = mutableListOf<Double>()
    
    // 3rd Step Rule State
    private var slCount = 0
    private var lastSLDirection: Direction? = null
    
    private val dbHelper = DatabaseHelper(context)
    
    enum class TradeState {
        NONE, RANGE_SET, BREAKOUT_WAITING, POSITION_OPEN, DAY_ENDED
    }
    
    enum class Direction { UPPER, LOWER }
    
    data class Position(
        val symbol: String,
        val entryPremium: Double,
        val quantity: Int,
        val direction: Direction
    )
    
    suspend fun process3MinCandle(candle: Candle, currentTime: Long) {
        val time = Date(currentTime)
        val hour = SimpleDateFormat("HH", Locale.getDefault()).format(time).toInt()
        val minute = SimpleDateFormat("mm", Locale.getDefault()).format(time).toInt()
        
        Log.d("Strategy", "[${SimpleDateFormat("HH:mm", Locale.getDefault()).format(time)}] Candle: ${candle.close}")
        
        when (tradeState) {
            TradeState.NONE -> handleMorningRange(candle, hour, minute)
            TradeState.RANGE_SET -> handleBreakoutConfirmation(candle)
            TradeState.BREAKOUT_WAITING -> handleTriggerCandle(candle)
            TradeState.POSITION_OPEN -> handlePositionManagement()
            TradeState.DAY_ENDED -> Log.d("Strategy", "Day ended - no action")
        }
        
        // Time Exit 3:15 PM
        if (hour == 15 && minute >= 15 && currentPosition != null) {
            exitPosition("Time Exit 3:15 PM")
        }
    }
    
    private suspend fun handleMorningRange(candle: Candle, hour: Int, minute: Int) {
        // 9:15 AM - 9:45 AM range
        if (hour == 9 && minute in 15..44) {
            morningHigh = max(morningHigh, candle.high)
            morningLow = min(morningLow, candle.low)
        }
        
        // Range complete at 9:45
        if (hour == 9 && minute >= 45) {
            val rangeSize = morningHigh - morningLow
            Log.d("RANGE", "Morning Range: H=${"%.0f".format(morningHigh)} L=${"%.0f".format(morningLow)} (${"%.0f".format(rangeSize)}pts)")
            
            if (rangeSize > 325) {
                Log.e("Strategy", "❌ Range too wide (>$rangeSize > 325). Skipping day.")
                tradeState = TradeState.DAY_ENDED
                return
            }
            
            upperLine = morningHigh + 5
            lowerLine = morningLow - 5
            Log.d("LEVELS", "📊 Upper: ${"%.0f".format(upperLine)} | Lower: ${"%.0f".format(lowerLine)}")
            tradeState = TradeState.RANGE_SET
        }
    }
    
    private suspend fun handleBreakoutConfirmation(candle: Candle) {
        val bodySize = abs(candle.close - candle.open)
        
        // C1 Conditions: Close outside + body < 60pts
        val upperC1 = candle.close > upperLine && bodySize < 60
        val lowerC1 = candle.close < lowerLine && bodySize < 60
        
        if (upperC1) {
            Log.d("C1", "✅ UPPER C1: Close=${"%.0f".format(candle.close)} Body=${"%.0f".format(bodySize)}")
            tradeState = TradeState.BREAKOUT_WAITING
        } else if (lowerC1) {
            Log.d("C1", "✅ LOWER C1: Close=${"%.0f".format(candle.close)} Body=${"%.0f".format(bodySize)}")
            tradeState = TradeState.BREAKOUT_WAITING
        }
    }
    
    private suspend fun handleTriggerCandle(candle: Candle) {
        currentPosition?.let { pos ->
            // C2: Break C1 high/low by 1pt (simplified)
            val triggerMet = when (pos.direction) {
                Direction.UPPER -> candle.low > upperLine + 1
                Direction.LOWER -> candle.high < lowerLine - 1
            }
            
            if (triggerMet) {
                executeTrade(candle.close, pos.direction)
                tradeState = TradeState.POSITION_OPEN
            } else {
                tradeState = TradeState.RANGE_SET  // Reset for new setup
            }
        }
    }
    
    private suspend fun executeTrade(spotPrice: Double, direction: Direction) {
        if (!canTakeNewTrade(direction)) {
            Log.w("3rdStep", "❌ Trade blocked by 3rd Step Rule")
            tradeState = TradeState.RANGE_SET
            return
        }
        
        val symbol = getATMOptionSymbol(spotPrice, direction)
        val quantity = settings.lots * 25  // BankNifty lot size
        
        Log.d("EXECUTE", "🎯 $direction BREAKOUT → Sell $symbol x$quantity")
        
        if (settings.mode == AppSettings.Mode.SIMULATION) {
            val mockPremium = 150.0 + (Math.random() * 30)
            currentPosition = Position(symbol, mockPremium, quantity, direction)
            placeMockSL(symbol)
            Log.d("SIMULATION", "✅ SIM Trade: $symbol @₹${"%.0f".format(mockPremium)}")
        } else {
            val orderResult = broker.placeSellOrder(symbol, quantity)
            if (orderResult.isSuccess) {
                currentPosition = Position(symbol, orderResult.avgPrice, quantity, direction)
                placeStopLoss(symbol)
                Log.d("LIVE", "✅ LIVE Trade: $symbol @₹${orderResult.avgPrice}")
            }
        }
    }
    
    private fun canTakeNewTrade(direction: Direction): Boolean {
        return when {
            slCount == 0 -> true  // First trade always allowed
            slCount == 1 && lastSLDirection != direction -> true  // Opposite direction retry
            else -> false
        }
    }
    
    private fun getATMOptionSymbol(spotPrice: Double, direction: Direction): String {
        val strike = (spotPrice / 100).roundToInt() * 100
        return when (direction) {
            Direction.UPPER -> "BANKNIFTY${SimpleDateFormat("ddMMM", Locale.getDefault()).format(Date())}" +
                              "${String.format("%05d", strike)}PE"
            Direction.LOWER -> "BANKNIFTY${SimpleDateFormat("ddMMM", Locale.getDefault()).format(Date())}" +
                              "${String.format("%05d", strike)}CE"
        }
    }
    
    private suspend fun placeStopLoss(symbol: String) {
        currentPosition?.let { pos ->
            val slPrice = pos.entryPremium + 70  // 70pt SL
            broker.placeBuySL(symbol, pos.quantity, slPrice)
            Log.d("RISK", "🛑 SL: Buy $symbol x${pos.quantity} @₹${"%.0f".format(slPrice)}")
        }
    }
    
    private suspend fun placeMockSL(symbol: String) {
        Log.d("SIMULATION", "🛑 Mock SL placed @+70pts")
    }
    
    private suspend fun handlePositionManagement() {
        currentPosition?.let { pos ->
            val currentPremium = broker.getOptionLTP(pos.symbol)
            val premiumDrop = pos.entryPremium - currentPremium
            
            // Checkpoints: 30/60/90 pt drops
            if (premiumDrop >= 30 && checkpoints.size < 1) {
                checkpoints.add(currentPremium)
                Log.d("CHECKPOINT", "📍 30pt: ${"%.0f".format(currentPremium)}")
            } else if (premiumDrop >= 60 && checkpoints.size < 2) {
                checkpoints.add(currentPremium)
                Log.d("CHECKPOINT", "📍 60pt: ${"%.0f".format(currentPremium)}")
            } else if (premiumDrop >= 90) {
                checkpoints.add(currentPremium)
                Log.d("CHECKPOINT", "📍 90pt: ${"%.0f".format(currentPremium)}")
            }
            
            // Reversal check
            checkpoints.lastOrNull()?.let { checkpoint ->
                if (currentPremium >= checkpoint) {
                    exitPosition("Checkpoint Reversal: ${"%.0f".format(currentPremium)}")
                }
            }
        }
    }
    
    private suspend fun exitPosition(reason: String) {
        currentPosition?.let { pos ->
            val exitPremium = broker.getOptionLTP(pos.symbol)
            val plPoints = pos.entryPremium - exitPremium
            
            // SAVE TRADE TO DATABASE
            val trade = Trade(
                id = 0,
                symbol = pos.symbol,
                entryPremium = pos.entryPremium,
                exitPremium = exitPremium,
                plPoints = plPoints,
                timestamp = System.currentTimeMillis().toString()
            )
            dbHelper.saveTrade(trade)
            
            Log.d("EXIT", "🔴 $reason | P&L: ${"%.0f".format(plPoints)}pts | ${pos.symbol}")
            
            // 3rd Step Rule
            if (reason.contains("SL")) {
                slCount++
                lastSLDirection = pos.direction
                
                when (slCount) {
                    1 -> {
                        Log.w("3rdStep", "1️⃣ 1st ${pos.direction} SL → Watch for OPPOSITE direction")
                        tradeState = TradeState.RANGE_SET
                    }
                    2 -> {
                        Log.e("3rdStep", "2️⃣ 2nd SL → END DAY")
                        tradeState = TradeState.DAY_ENDED
                    }
                }
            } else {
                tradeState = TradeState.DAY_ENDED
            }
            
            currentPosition = null
            checkpoints.clear()
        }
    }
    
    fun resetDaily() {
        tradeState = TradeState.NONE
        morningHigh = 0.0
        morningLow = Double.MAX_VALUE
        currentPosition = null
        checkpoints.clear()
        slCount = 0
        lastSLDirection = null
        Log.d("RESET", "Daily reset complete")
    }
    
    val stateString: String
        get() = when (tradeState) {
            TradeState.NONE -> "Waiting 9:15..."
            TradeState.RANGE_SET -> "Range: ${"%.0f".format(upperLine)}/${"%.0f".format(lowerLine)}"
            TradeState.BREAKOUT_WAITING -> "C1 confirmed - C2 pending"
            TradeState.POSITION_OPEN -> "Position: ${currentPosition?.symbol}"
            TradeState.DAY_ENDED -> "Day ended"
        }
}