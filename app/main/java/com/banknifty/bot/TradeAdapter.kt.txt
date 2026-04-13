package com.banknifty.bot

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TradeAdapter(private val trades: MutableList<Trade> = mutableListOf()) : 
    RecyclerView.Adapter<TradeAdapter.TradeViewHolder>() {
    
    class TradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.tradeCard)
        val symbolText: TextView = itemView.findViewById(R.id.symbolText)
        val entryText: TextView = itemView.findViewById(R.id.entryText)
        val exitText: TextView = itemView.findViewById(R.id.exitText)
        val plText: TextView = itemView.findViewById(R.id.plText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trade, parent, false)
        return TradeViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TradeViewHolder, position: Int) {
        val trade = trades[position]
        
        // Symbol
        holder.symbolText.text = trade.symbol
        
        // Entry/Exit prices
        holder.entryText.text = "Entry: ₹${formatPrice(trade.entryPremium)}"
        holder.exitText.text = "Exit: ₹${formatPrice(trade.exitPremium)}"
        
        // P&L with color
        val plPoints = trade.plPoints
        holder.plText.text = "P&L: ${formatPoints(plPoints)} points"
        holder.plText.setTextColor(getPlColor(plPoints))
        
        // Card background based on P&L
        val cardColor = if (plPoints >= 0) {
            ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_light)
        } else {
            ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_light)
        }
        holder.cardView.setCardBackgroundColor(cardColor)
        
        // Time
        holder.timeText.text = formatTime(trade.timestamp)
        
        // Click to expand details
        holder.itemView.setOnClickListener {
            showTradeDetails(holder.itemView.context, trade)
        }
    }
    
    override fun getItemCount(): Int = trades.size
    
    fun updateTrades(newTrades: List<Trade>) {
        trades.clear()
        trades.addAll(newTrades)
        notifyDataSetChanged()
    }
    
    fun getTotalPnL(): Double {
        return trades.sumOf { it.plPoints }
    }
    
    private fun formatPrice(price: Double): String {
        return if (price == 0.0) "—" else String.format("%.0f", price)
    }
    
    private fun formatPoints(points: Double): String {
        return if (points == 0.0) "—" else String.format("%.0f", points)
    }
    
    private fun getPlColor(plPoints: Double): Int {
        return when {
            plPoints > 0 -> Color.parseColor("#4CAF50")  // Green
            plPoints < 0 -> Color.parseColor("#F44336")  // Red
            else -> Color.parseColor("#757575")          // Grey
        }
    }
    
    private fun formatTime(timestamp: String): String {
        return try {
            val date = Date(timestamp.toLong())
            SimpleDateFormat("HH:mm\ndd MMM", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            "Invalid time"
        }
    }
    
    private fun showTradeDetails(context: android.content.Context, trade: Trade) {
        AlertDialog.Builder(context)
            .setTitle(trade.symbol)
            .setMessage("""
                Entry: ₹${formatPrice(trade.entryPremium)}
                Exit: ₹${formatPrice(trade.exitPremium)}
                P&L: ${formatPoints(trade.plPoints)} points
                Time: ${formatTime(trade.timestamp)}
                ID: ${trade.id}
            """.trimIndent())
            .setPositiveButton("Close", null)
            .show()
    }
}