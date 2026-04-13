package com.banknifty.bot

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var serviceStatusText: TextView
    private lateinit var walletBalanceText: TextView
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var settingsButton: Button
    private lateinit var historyButton: Button
    
    private lateinit var dbHelper: DatabaseHelper
    private var isLoggedIn = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        dbHelper = DatabaseHelper(this)
        initViews()
        checkLoginStatus()
    }
    
    private fun initViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        serviceStatusText = findViewById(R.id.serviceStatusText)
        walletBalanceText = findViewById(R.id.walletBalanceText)
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)
        settingsButton = findViewById(R.id.settingsButton)
        historyButton = findViewById(R.id.historyButton)
        
        loginButton.setOnClickListener { attemptLogin() }
        startServiceButton.setOnClickListener { startTradingService() }
        stopServiceButton.setOnClickListener { stopTradingService() }
        settingsButton.setOnClickListener { 
            startActivity(Intent(this, SettingsActivity::class.java)) 
        }
        historyButton.setOnClickListener { 
            startActivity(Intent(this, TradeHistoryActivity::class.java)) 
        }
    }
    
    private fun checkLoginStatus() {
        val prefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        isLoggedIn = prefs.getBoolean("is_logged_in", false)
        
        if (isLoggedIn) {
            showDashboard()
            updateServiceStatus()
            updateWalletBalance()
        }
    }
    
    private fun attemptLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (dbHelper.validateUser(email, password)) {
            getSharedPreferences("app_state", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_logged_in", true)
                .apply()
            
            isLoggedIn = true
            showDashboard()
            Toast.makeText(this, "✅ Login successful", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Invalid credentials", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showDashboard() {
        // Hide login
        emailEditText.visibility = View.GONE
        passwordEditText.visibility = View.GONE
        loginButton.visibility = View.GONE
        
        // Show dashboard
        serviceStatusText.visibility = View.VISIBLE
        walletBalanceText.visibility = View.VISIBLE
        startServiceButton.visibility = View.VISIBLE
        stopServiceButton.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
        historyButton.visibility = View.VISIBLE
    }
    
    private fun startTradingService() {
        val serviceIntent = Intent(this, TradingService::class.java)
        startForegroundService(serviceIntent)
        Toast.makeText(this, "🚀 Trading Bot Started", Toast.LENGTH_SHORT).show()
        updateServiceStatus()
    }
    
    private fun stopTradingService() {
        val serviceIntent = Intent(this, TradingService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "⏹️ Trading Bot Stopped", Toast.LENGTH_SHORT).show()
        updateServiceStatus()
    }
    
    private fun updateServiceStatus() {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = manager.getRunningServices(Integer.MAX_VALUE)
        
        val isRunning = services.any { 
            it.service.className == "com.banknifty.bot.TradingService" 
        }
        
        serviceStatusText.text = if (isRunning) {
            "🟢 Bot Active - Monitoring Market"
        } else {
            "🔴 Bot Stopped"
        }
    }
    
    private fun updateWalletBalance() {
        // Mock - replace with real API call
        val balance = 245670 + (Math.random() * 10000).toInt()
        walletBalanceText.text = "💰 Balance: ₹${String.format("%,d", balance)}"
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        if (isLoggedIn) updateWalletBalance()
    }
    
    override fun onBackPressed() {
        if (!isLoggedIn) {
            super.onBackPressed()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Bot will continue running in background")
            .setPositiveButton("Exit") { _, _ -> super.onBackPressed() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}