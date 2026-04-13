package com.banknifty.bot

import android.util.Log
import kotlinx.coroutines.delay

class ZerodhaAPI : BrokerAPI {
    
    companion object {
        private const val TAG = "ZerodhaAPI"
    }
    
    override suspend fun placeSellOrder(symbol: String, qty: Int): OrderResult {
        Log.d(TAG, "📤 KiteConnect SELL → $symbol ×$qty")
        
        // Simulate order placement delay
        delay(1000 + (kotlin.math.random() * 500).toLong())
        
        // Realistic option premium: 135-175
        val avgPrice = 148.0 + kotlin.math.random() * 27
        
        Log.d(TAG, "✅ Order filled @ ₹${String.format("%.1f", avgPrice)}")
        return OrderResult(true, avgPrice)
    }
    
    override suspend fun placeBuySL(symbol: String, qty: Int, price: Double): OrderResult {
        Log.d(TAG, "🛑 Kite SL-M → $symbol ×$qty @ ₹${String.format("%.0f", price)}")
        delay(650)
        
        Log.d(TAG, "✅ SL order placed")
        return OrderResult(true, price)
    }
    
    override suspend fun placeMarketBuy(symbol: String, qty: Int): Boolean {
        Log.d(TAG, "💰 Kite Market BUY → $symbol ×$qty")
        delay(850)
        
        Log.d(TAG, "✅ Position squared off")
        return true
    }
    
    override suspend fun getBankNiftySpot(): Double {
        // BankNifty realistic simulation: 48k-53k range
        val basePrice = 50850.0
        val volatility = kotlin.math.sin(System.currentTimeMillis() / 180000.0) * 450  // 3min cycle
        val noise = kotlin.math.random() * 80 - 40
        
        val spot = basePrice + volatility + noise
        Log.v(TAG, "📈 BankNifty Spot: ${String.format("%.0f", spot)}")
        delay(300)
        
        return spot
    }
    
    override suspend fun getLatest3MinCandle(symbol: String): CandleData {
        val spot = getBankNiftySpot()
        val candleRange = 22.0 + kotlin.math.random() * 38  // 22-60pts range
        
        delay(950)  // Realistic API response
        
        val candle = CandleData(
            open = spot - 12,
            high = spot + candleRange,
            low = spot - candleRange * 0.65,
            close = spot + (kotlin.math.random() * candleRange * 2 - candleRange)
        )
        
        Log.v(TAG, "🕯️ 3M Candle: O:${String.format("%.0f", candle.open)} H:${String.format("%.0f", candle.high)}")
        return candle
    }
    
    override suspend fun getOptionLTP(symbol: String): Double {
        // Option premium simulation with theta decay
        val timeOfDay = System.currentTimeMillis() % 86400000  // 24hr cycle
        val decayFactor = (timeOfDay / 86400000.0) * 0.4  // 40% daily decay
        
        val baseLTP = 155.0
        val volatility = kotlin.math.sin(timeOfDay / 1800000.0) * 25  // 30min cycle
        val ltp = baseLTP * (1.0 - decayFactor) + volatility + kotlin.math.random() * 18 - 9
        
        delay(200)
        Log.v(TAG, "💵 $symbol LTP: ${String.format("%.1f", ltp)}")
        return kotlin.math.max(5.0, ltp)  // Min ₹5
    }
    
    // Additional Zerodha-specific methods
    suspend fun getAccountBalance(): Double {
        val balance = 275000.0 + kotlin.math.random() * 45000 - 25000
        delay(400)
        return balance
    }
    
    suspend fun getMargins(): Map<String, Double> {
        return mapOf(
            "available" to 185000.0,
            "utilised" to 45000.0,
            "cash" to 220000.0
        )
    }
    
    suspend fun getHoldings(): List<Holding> {
        delay(800)
        return emptyList()  // No holdings in simulation
    }
}

data class Holding(
    val symbol: String,
    val qty: Int,
    val avgPrice: Double
)