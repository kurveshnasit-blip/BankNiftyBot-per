package com.banknifty.bot

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TradeHistoryActivity : AppCompatActivity() {
    
    private lateinit var filterSpinner: Spinner
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var summaryText: TextView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tradeAdapter: TradeAdapter
    
    private val allTrades = mutableListOf<Trade>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trade_history)
        
        dbHelper = DatabaseHelper(this)
        tradeAdapter = TradeAdapter()
        
        initViews()
        setupRecyclerView()
        setupFilterSpinner()
        loadAllTrades()
    }
    
    private fun initViews() {
        filterSpinner = findViewById(R.id.filterSpinner)
        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        summaryText = findViewById(R.id.summaryText)
    }
    
    private fun setupRecyclerView() {
        recyclerView.apply {
            adapter = tradeAdapter
            layoutManager = LinearLayoutManager(this@TradeHistoryActivity)
            setHasFixedSize(true)
            
            // Add dividers
            addItemDecoration(
                DividerItemDecoration(
                    this@TradeHistoryActivity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }
    
    private fun setupFilterSpinner() {
        val filters = arrayOf(
            "All Trades (${dbHelper.getTradesCount()})",
            "Today",
            "Yesterday", 
            "This Month",
            "This Year"
        )
        
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            filters
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        filterSpinner.adapter = adapter
        
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterAndUpdate(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun loadAllTrades() {
        allTrades.clear()
        allTrades.addAll(dbHelper.getAllTrades())
        
        if (allTrades.isEmpty()) {
            showEmptyState()
        } else {
            showTrades()
            filterAndUpdate(0)  // Default: All trades
        }
    }
    
    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyText.visibility = View.VISIBLE
        summaryText.visibility = View.GONE
        emptyText.text = "📊 No trades yet\n\nStart the bot to see your trade history!"
        filterSpinner.isEnabled = false
    }
    
    private fun showTrades() {
        recyclerView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
        summaryText.visibility = View.VISIBLE
        filterSpinner.isEnabled = true
    }
    
    private fun filterAndUpdate(filterPosition: Int) {
        val filteredTrades = when (filterPosition) {
            0 -> allTrades  // All time
            1 -> allTrades.filter { isToday(it.timestamp) }
            2 -> allTrades.filter { isYesterday(it.timestamp) }
            3 -> allTrades.filter { isThisMonth(it.timestamp) }
            4 -> allTrades.filter { isThisYear(it.timestamp) }
            else -> allTrades
        }
        
        tradeAdapter.updateTrades(filteredTrades)
        
        // Update summary
        val totalPnL = tradeAdapter.getTotalPnL()
        val count = filteredTrades.size
        summaryText.text = "Total: $count trades | P&L: ${tradeAdapter.formatPoints(totalPnL)} points"
        
        // Update spinner text
        val spinner = filterSpinner.selectedView as? TextView
        spinner?.text = when (filterPosition) {
            0 -> "All Trades ($count)"
            1 -> "Today ($count)"
            2 -> "Yesterday ($count)"
            3 -> "This Month ($count)"
            4 -> "This Year ($count)"
            else -> "All Trades ($count)"
        }
        
        Toast.makeText(this, "$count trades loaded", Toast.LENGTH_SHORT).show()
    }
    
    // Date filtering
    private fun isToday(timestamp: String): Boolean {
        return try {
            val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = sdfDay.format(Date())
            val tradeDay = timestamp.substring(0, 10)
            today == tradeDay
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isYesterday(timestamp: String): Boolean {
        return try {
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            yesterday == timestamp.substring(0, 10)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isThisMonth(timestamp: String): Boolean {
        return try {
            val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val thisMonth = sdfMonth.format(Date())
            val tradeMonth = timestamp.substring(0, 7)
            thisMonth == tradeMonth
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isThisYear(timestamp: String): Boolean {
        return try {
            val thisYear = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            thisYear == timestamp.substring(0, 4)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadAllTrades()  // Refresh data
    }
}