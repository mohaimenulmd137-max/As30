package com.example.domain.engine.export

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.example.domain.model.PreviewResolution
import java.util.concurrent.atomic.AtomicInteger

class AdaptiveMemoryManager(
    private val context: Context,
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val resolution: PreviewResolution
) : ComponentCallbacks2 {

    private val trimLevelFlag = AtomicInteger(0)

    @Volatile
    var peakHeapUsedMb: Long = 0L
        private set

    fun register() {
        runCatching { context.applicationContext.registerComponentCallbacks(this) }
    }

    fun unregister() {
        runCatching { context.applicationContext.unregisterComponentCallbacks(this) }
    }

    fun evaluateProfile(thermalLevel: ExportThermalLevel = ExportThermalLevel.NORMAL): AdaptiveMemoryProfile {
        val rt = Runtime.getRuntime()
        val heapLimitBytes = rt.maxMemory().coerceAtLeast(64L * 1024L * 1024L)
        val allocatedBytes = rt.totalMemory()
        val freeWithinAllocBytes = rt.freeMemory()
        val heapUsedBytes = (allocatedBytes - freeWithinAllocBytes).coerceAtLeast(0L)
        val heapHeadroomBytes = (heapLimitBytes - heapUsedBytes).coerceAtLeast(0L)

        val heapUsedMb = heapUsedBytes / (1024L * 1024L)
        val heapLimitMb = heapLimitBytes / (1024L * 1024L)
        val heapHeadroomMb = heapHeadroomBytes / (1024L * 1024L)
        if (heapUsedMb > peakHeapUsedMb) peakHeapUsedMb = heapUsedMb

        val am = runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(memInfo) }
        val availSysMb = (memInfo.availMem / (1024L * 1024L)).takeIf { it > 0L } ?: 1024L
        val sysLowMem = memInfo.lowMemory

        val lastTrim = trimLevelFlag.get()
        val heapUsageRatio = heapUsedBytes.toFloat() / heapLimitBytes.toFloat()

        val tier = when {
            sysLowMem || heapHeadroomMb < 38L || heapUsageRatio > 0.86f || lastTrim >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                MemoryTier.CRITICAL
            }
            heapLimitMb <= 256L || heapHeadroomMb < 85L || availSysMb < 512L || lastTrim >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                MemoryTier.LOW
            }
            availSysMb >= 2200L && heapLimitMb >= 512L && heapHeadroomMb > 220L -> {
                MemoryTier.HIGH
            }
            else -> MemoryTier.BALANCED
        }

        val maxBitmapCacheBytes = when (tier) {
            MemoryTier.CRITICAL -> 8L * 1024L * 1024L
            MemoryTier.LOW -> 18L * 1024L * 1024L
            MemoryTier.BALANCED -> 36L * 1024L * 1024L
            MemoryTier.HIGH -> 56L * 1024L * 1024L // Hard-capped even on high-RAM phones to prevent bloat
        }

        val maxRetrievers = when (tier) {
            MemoryTier.CRITICAL -> 1
            MemoryTier.LOW -> 2
            MemoryTier.BALANCED -> 3
            MemoryTier.HIGH -> 4
        }

        val maxDecodeEdge = when (tier) {
            MemoryTier.CRITICAL -> minOf(maxOf(targetWidth, targetHeight), 1280)
            MemoryTier.LOW -> minOf(maxOf(targetWidth, targetHeight), 1920)
            MemoryTier.BALANCED, MemoryTier.HIGH -> maxOf(targetWidth, targetHeight).coerceIn(720, 3840)
        }

        val thermalYield = when (thermalLevel) {
            ExportThermalLevel.NORMAL -> if (tier == MemoryTier.CRITICAL) 8L else 0L
            ExportThermalLevel.MODERATE -> 4L
            ExportThermalLevel.SEVERE -> 18L
            ExportThermalLevel.CRITICAL -> 45L
        }

        val audioChunkSamples = when (tier) {
            MemoryTier.CRITICAL -> 2048
            MemoryTier.LOW -> 4096
            else -> 4410
        }

        return AdaptiveMemoryProfile(
            tier = tier,
            availableSystemRamMb = availSysMb,
            heapUsedMb = heapUsedMb,
            heapLimitMb = heapLimitMb,
            heapHeadroomMb = heapHeadroomMb,
            maxBitmapCacheBytes = maxBitmapCacheBytes,
            maxOpenVideoRetrievers = maxRetrievers,
            maxDecodeEdgePx = maxDecodeEdge,
            preferHardwareCanvas = resolution != PreviewResolution.P720 || tier != MemoryTier.CRITICAL,
            chunkedAudioBufferSamples = audioChunkSamples,
            thermalYieldMs = thermalYield
        )
    }

    override fun onTrimMemory(level: Int) {
        trimLevelFlag.set(level)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        trimLevelFlag.set(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}
