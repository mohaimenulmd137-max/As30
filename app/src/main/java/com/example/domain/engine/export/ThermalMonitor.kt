package com.example.domain.engine.export

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.util.concurrent.atomic.AtomicReference

class ThermalMonitor(private val context: Context) {

    private val currentLevel = AtomicReference(ExportThermalLevel.NORMAL)
    private var powerManager: PowerManager? = null
    private var listenerRegistered = false

    private val thermalListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { status ->
            currentLevel.set(mapAndroidThermalStatus(status))
        }
    } else {
        null
    }

    fun start() {
        powerManager = runCatching { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && !listenerRegistered) {
            runCatching {
                val listener = thermalListener as PowerManager.OnThermalStatusChangedListener
                powerManager?.addThermalStatusListener(listener)
                listenerRegistered = true
                currentLevel.set(mapAndroidThermalStatus(powerManager!!.currentThermalStatus))
            }
        } else {
            currentLevel.set(readBatteryFallbackThermal())
        }
    }

    fun pollCurrentLevel(): ExportThermalLevel {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            runCatching {
                currentLevel.set(mapAndroidThermalStatus(powerManager!!.currentThermalStatus))
            }
        } else {
            currentLevel.set(readBatteryFallbackThermal())
        }
        return currentLevel.get()
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && listenerRegistered) {
            runCatching {
                val listener = thermalListener as PowerManager.OnThermalStatusChangedListener
                powerManager?.removeThermalStatusListener(listener)
            }
            listenerRegistered = false
        }
    }

    private fun mapAndroidThermalStatus(status: Int): ExportThermalLevel {
        return when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> ExportThermalLevel.CRITICAL
            status >= PowerManager.THERMAL_STATUS_SEVERE -> ExportThermalLevel.SEVERE
            status >= PowerManager.THERMAL_STATUS_MODERATE -> ExportThermalLevel.MODERATE
            else -> ExportThermalLevel.NORMAL
        }
    }

    private fun readBatteryFallbackThermal(): ExportThermalLevel {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return ExportThermalLevel.NORMAL
        val tempTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300)
        val tempC = tempTenthsC / 10f
        return when {
            tempC >= 48f -> ExportThermalLevel.CRITICAL
            tempC >= 44f -> ExportThermalLevel.SEVERE
            tempC >= 40f -> ExportThermalLevel.MODERATE
            else -> ExportThermalLevel.NORMAL
        }
    }
}
