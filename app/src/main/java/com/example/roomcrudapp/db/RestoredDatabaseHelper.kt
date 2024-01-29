package com.example.roomcrudapp.db

import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabaseHook

class RestoredDatabaseHelper(context: Context, dbName: String,private val password: String) :
    SQLiteOpenHelper(context, dbName, null, 1) {

    private val TAG = "RestoredDatabaseHelper"

    init {
        SQLiteDatabase.loadLibs(context)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Database creation logic if needed
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Database upgrade logic if needed
    }

    // Method to execute a SELECT query
    fun executeSelectQuery(query: String): Cursor? {
        return try {
            val database = getWritableDatabase(password)
            database.rawQuery(query, null)
        } catch (e: SQLException) {
            Log.e(TAG, "Error executing query: $query", e)
            null
        }
    }
}

class MyDatabaseHook(password: String) : SQLiteDatabaseHook {
    private val encryptionKey: ByteArray = SQLiteDatabase.getBytes(password.toCharArray())

    override fun preKey(database: SQLiteDatabase?) {
        database?.rawExecSQL("ATTACH DATABASE '$database' AS encrypted KEY '$encryptionKey'")
    }

    override fun postKey(database: SQLiteDatabase?) {
        // Post-key actions, if any
    }
}
