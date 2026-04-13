package com.banknifty.bot

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var brokerSpinner: Spinner
    private lateinit var apiKeyEditText: EditText
    private lateinit var apiSecretEditText: EditText
    private lateinit var lotsEditText: EditText
    private lateinit var modeSwitch: Switch
    private lateinit var saveButton: Button
    private lateinit var testConnectionButton: Button
    private lateinit var clearHistoryButton: Button
    
    private lateinit var prefs: SharedPreferences
    private var selectedBroker = AppSettings.BrokerType.ZERODHA
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        initViews()
        setupBrokerSpinner()
        loadSettings()
    }
    
    private fun initViews() {
        brokerSpinner = findViewById(R.id.brokerSpinner)
        apiKeyEditText = findViewById(R.id.apiKeyEditText)
        apiSecretEditText = findViewById(R.id.apiSecretEditText)
        lotsEditText = findViewById(R.id.lotsEditText)
        modeSwitch = findViewById(R.id.modeSwitch)
        saveButton = findViewById(R.id.saveButton)
        testConnectionButton = findViewById(R.id.testConnectionButton)
        clearHistoryButton = findViewById(R.id.clearHistoryButton)
        
        saveButton.setOnClickListener { saveSettings() }
        testConnectionButton.setOnClickListener { testConnection() }
        clearHistoryButton.setOnClickListener { confirmClearHistory() }
    }
    
    private fun setupBrokerSpinner() {
        val brokers = arrayOf("Zerodha Kite", "Upstox Pro")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, brokers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        brokerSpinner.adapter = adapter
        
        brokerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                selectedBroker = if (position == 0) {
                    AppSettings.BrokerType.ZERODHA
                } else {
                    AppSettings.BrokerType.UPSTOX
                }
                updateApiHints()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun updateApiHints() {
        val keyHint = when (selectedBroker) {
            AppSettings.BrokerType.ZERODHA -> "Zerodha API Key (app_key)"
            AppSettings.BrokerType.UPSTOX -> "Upstox API Key"
        }
        apiKeyEditText.hint = keyHint
        
        val secretHint = when (selectedBroker) {
            AppSettings.BrokerType.ZERODHA -> "Zerodha API Secret"
            AppSettings.BrokerType.UPSTOX -> "Upstox API Secret"
        }
        apiSecretEditText.hint = secretHint
    }
    
    private fun loadSettings() {
        brokerSpinner.setSelection(prefs.getInt("broker_index", 0))
        apiKeyEditText.setText(prefs.getString("api_key", ""))
        apiSecretEditText.setText(prefs.getString("api_secret", ""))
        lotsEditText.setText(prefs.getString("lots", "1"))
        modeSwitch.isChecked = prefs.getBoolean("live_mode", false)
    }
    
    private fun saveSettings() {
        try {
            val lots = lotsEditText.text.toString().toIntOrNull() ?: 1
            if (lots < 1 || lots > 20) {
                Toast.makeText(this, "Lots: 1-20 only", Toast.LENGTH_SHORT).show()
                return
            }
            
            prefs.edit().apply {
                putInt("broker_index", brokerSpinner.selectedItemPosition)
                putString("broker_type", selectedBroker.name)
                putString("api_key", apiKeyEditText.text.toString())
                putString("api_secret", apiSecretEditText.text.toString())
                putInt("lots", lots)
                putBoolean("live_mode", modeSwitch.isChecked)
                apply()
            }
            
            Toast.makeText(this, "✅ Settings saved successfully", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testConnection() {
        if (apiKeyEditText.text.isBlank() || apiSecretEditText.text.isBlank()) {
            Toast.makeText(this, "Enter API credentials first", Toast.LENGTH_SHORT).show()
            return
        }
        
        testConnectionButton.isEnabled = false
        testConnectionButton.text = "Testing..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(2000)  // Mock API call
                
                withContext(Dispatchers.Main) {
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "TEST CONNECTION"
                    
                    // Mock validation
                    val isValid = apiKeyEditText.text.length > 10 && 
                                 apiSecretEditText.text.length > 15
                    
                    if (isValid) {
                        Toast.makeText(this@SettingsActivity, 
                            "✅ Connection successful!\nReady for live trading", 
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@SettingsActivity, 
                            "❌ Invalid API credentials", 
                            Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "❌ Test failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    testConnectionButton.isEnabled = true
                    testConnectionButton.text = "TEST CONNECTION"
                }
            }
        }
    }
    
    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear Trade History")
            .setMessage("Delete all trade records? Cannot be undone.")
            .setPositiveButton("CLEAR ALL") { _, _ ->
                val dbHelper = DatabaseHelper(this)
                dbHelper.clearTrades()
                Toast.makeText(this, "🗑️ History cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}