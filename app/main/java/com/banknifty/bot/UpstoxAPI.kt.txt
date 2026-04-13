package com.banknifty.bot

import kotlinx.coroutines.delay

class UpstoxAPI : BrokerAPI {
    
    override suspend fun placeSellOrder(symbol: String, qty: Int): OrderResult {
        Log.d("UpstoxAPI", "📤 Placing SELL: $symbol x$qty")
        delay(1200)  // Mock API delay
        
        // Random fill price 140-170
        val fillPrice = 145.0 + kotlin.math.random() * 25
        return OrderResult(true, fillPrice)
    }
    
    override suspend fun placeBuySL(symbol: String, qty: Int, price: Double): OrderResult {
        Log.d("UpstoxAPI", "🛑 Placing SL: $symbol x$qty @${"%.0f".format(price)}")
        delay(600)
        return OrderResult(true, price)
    }
    
    override suspend fun placeMarketBuy(symbol: String, qty: Int): Boolean {
        Log.d("UpstoxAPI", "💰 Market BUY: $symbol x$qty")
        delay(900)
        return true
    }
    
    override suspend fun getBankNiftySpot(): Double {
        // Realistic BankNifty range: 48k-52k
        val base = 50500.0
        val fluctuation = kotlin.math.sin(System.currentTimeMillis() / 100000.0) * 500
        return (base + fluctuation + kotlin.math.random() * 100 - 50)
    }
    
    override suspend fun getLatest3MinCandle(symbol: String): CandleData {
        val spot = getBankNiftySpot()
        val range = 25.0 + kotlin.math.random() * 35
        
        delay(800)  // API response time
        
        return CandleData(
            open = spot - 10,
            high = spot + range,
            low = spot - range * 0.6,
            close = spot + (kotlin.math.random() * range * 2 - range)
        )
    }
    
    override suspend fun getOptionLTP(symbol: String): Double {
        // Option premium simulation (decays over time)
        val basePremium = 160.0
        val timeFactor = (System.currentTimeMillis() % 3600000) / 3600000.0  // Hourly cycle
        val decay = kotlin.math.sin(timeFactor * kotlin.math.PI) * 40
        return kotlin.math.max(20.0, basePremium + decay + kotlin.math.random() * 20 - 10)
    }
    
    // Upstox-specific methods (mock)
    suspend fun getAccountBalance(): Double {
        return 250000.0 + kotlin.math.random() * 50000  // ₹2.5L-3L
    }
    
    suspend fun getPositions(): List<Position> {
        return emptyList()  // No open positions in mock
    }
}