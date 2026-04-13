package com.banknifty.bot

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*

data class Trade(
    val id: Int,
    val symbol: String,
    val entryPremium: Double,
    val exitPremium: Double,
    val plPoints: Double,
    val timestamp: String
)

class DatabaseHelper(private val context: Context) {
    
    private val dbHelper = BankNiftyDbHelper(context)
    private val writableDatabase: SQLiteDatabase = dbHelper.writableDatabase
    private val readableDatabase: SQLiteDatabase = dbHelper.readableDatabase
    
    companion object {
        const val TABLE_USERS = "users"
        const val TABLE_TRADES = "trades"
    }
    
    fun validateUser(email: String, password: String): Boolean {
        val cursor = readableDatabase.query(
            TABLE_USERS,
            arrayOf("id"),
            "email = ? AND password = ?",
            arrayOf(email, password),
            null, null, null
        )
        
        val isValid = cursor.count > 0
        cursor.close()
        return isValid
    }
    
    fun saveTrade(trade: Trade) {
        val values = ContentValues().apply {
            put("symbol", trade.symbol)
            put("entry_premium", trade.entryPremium)
            put("exit_premium", trade.exitPremium)
            put("pl_points", trade.plPoints)
            put("timestamp", trade.timestamp)
        }
        writableDatabase.insert(TABLE_TRADES, null, values)
    }
    
    fun getAllTrades(): List<Trade> {
        val trades = mutableListOf<Trade>()
        val cursor = readableDatabase.query(
            TABLE_TRADES,
            null,
            null, null, null, null,
            "id DESC"  // Newest first
        )
        
        with(cursor) {
            while (moveToNext()) {
                val trade = Trade(
                    id = getInt(getColumnIndexOrThrow("id")),
                    symbol = getString(getColumnIndexOrThrow("symbol")),
                    entryPremium = getDouble(getColumnIndexOrThrow("entry_premium")),
                    exitPremium = getDouble(getColumnIndexOrThrow("exit_premium")),
                    plPoints = getDouble(getColumnIndexOrThrow("pl_points")),
                    timestamp = getString(getColumnIndexOrThrow("timestamp"))
                )
                trades.add(trade)
            }
        }
        cursor.close()
        return trades
    }
    
    fun getTradesCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_TRADES", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }
    
    fun clearTrades() {
        writableDatabase.delete(TABLE_TRADES, null, null)
    }
    
    fun close() {
        writableDatabase.close()
        readableDatabase.close()
        dbHelper.close()
    }
}

private class BankNiftyDbHelper(
    context: Context
) : SQLiteOpenHelper(context, "banknifty_bot_v2.db", null, 2) {
    
    override fun onCreate(db: SQLiteDatabase) {
        // Users table
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                created_at INTEGER DEFAULT ${System.currentTimeMillis()}
            )
        """)
        
        // Insert default admin user
        val defaultUser = ContentValues().apply {
            put("email", "admin")
            put("password", "admin123")
        }
        db.insert(TABLE_USERS, null, defaultUser)
        
        // Trades table
        db.execSQL("""
            CREATE TABLE $TABLE_TRADES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT NOT NULL,
                entry_premium REAL NOT NULL,
                exit_premium REAL,
                pl_points REAL,
                timestamp TEXT NOT NULL,
                created_at INTEGER DEFAULT ${System.currentTimeMillis()}
            )
        """)
        
        Log.d("DB", "Database created with default admin user")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TRADES")
            onCreate(db)
        }
    }
}