package com.example.domain.engine.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.domain.engine.MediaProbeHelper
import com.example.domain.engine.TransitionAndChromaEngine
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip

/**
 * Memory-bounded media frame and bitmap provider for Video Export.
 *
 * Key optimizations against OOM on 256MB-heap and multi-hour 5-6 GB exports:
 * 1. Never preloads all project images at once; decodes lazily via 2-pass BitmapFactory inSampleSize.
 * 2. Stores decoded image bitmaps in a strict byte-bounded LRU cache driven by AdaptiveMemoryManager.
 * 3. Limits simultaneously open MediaMetadataRetrievers and decoded video frame bitmaps via LRU eviction.
 * 4. Defers bitmap.recycle() of evicted entries safely until outside any active Canvas draw pass.
 */
class MemorySafeMediaFrameProvider(
    private val context: Context,
    private val project: StudioProject,
    private val targetW: Int,
    private val targetH: Int,
    private val fastMode: Boolean,
    private var memoryProfile: AdaptiveMemoryProfile,
    private val drawableResolver: (String) -> Int
) {
    private data class CachedImageEntry(
        val key: String,
        val bitmap: Bitmap,
        val byteCount: Int
    )

    private val imageLru = LinkedHashMap<String, CachedImageEntry>(8, 0.75f, true)
    private var currentImageCacheBytes = 0L

    private val retrieverLru = LinkedHashMap<String, MediaMetadataRetriever>(4, 0.75f, true)
    private val sourceDurationsMs = mutableMapOf<String, Long>()
    private val lastVideoBucket = mutableMapOf<String, Long>()
    private val cachedVideoFrames = LinkedHashMap<String, Bitmap>(4, 0.75f, true)
    private val deferredRecycleQueue = mutableListOf<Bitmap>()

    // Reusable scratch bitmap for downscaled blur so blur never allocates every frame
    private var reusableBlurScratch: Bitmap? = null

    fun updateMemoryProfile(profile: AdaptiveMemoryProfile) {
        this.memoryProfile = profile
        enforceImageBudget()
        enforceVideoRetrieverBudget()
    }

    /**
     * Call at the start/end of every frame outside active drawBitmap calls to safely recycle evicted bitmaps.
     */
    fun flushDeferredRecycles() {
        if (deferredRecycleQueue.isEmpty()) return
        val iter = deferredRecycleQueue.iterator()
        while (iter.hasNext()) {
            val bmp = iter.next()
            if (!bmp.isRecycled) runCatching { bmp.recycle() }
            iter.remove()
        }
    }

    fun getImageBitmap(clip: TimelineClip, chromaKeyEnabled: Boolean, chromaKeyStrength: Float): Bitmap? {
        val src = clip.mediaSource ?: return null
        if (src.isVideo) return null
        val cacheKey = "${clip.id}_${chromaKeyEnabled}_${(chromaKeyStrength * 20).toInt()}"

        imageLru[cacheKey]?.let { existing ->
            if (!existing.bitmap.isRecycled) return existing.bitmap
            imageLru.remove(cacheKey)
            currentImageCacheBytes -= existing.byteCount
        }

        val maxEdge = minOf(maxOf(targetW, targetH), memoryProfile.maxDecodeEdgePx)
        val decoded = decodeSampledBitmap(
            uriString = src.uriString,
            drawableName = src.drawableName,
            reqW = minOf(targetW, maxEdge),
            reqH = minOf(targetH, maxEdge),
            allowRgb565 = !chromaKeyEnabled
        ) ?: return null

        val finalBmp = if (chromaKeyEnabled) {
            val keyed = TransitionAndChromaEngine.applyGreenScreenChromaKey(decoded, chromaKeyStrength)
            if (keyed !== decoded && !decoded.isRecycled) decoded.recycle()
            keyed
        } else {
            decoded
        }

        val bytes = finalBmp.allocationByteCount
        val entry = CachedImageEntry(cacheKey, finalBmp, bytes)
        imageLru[cacheKey] = entry
        currentImageCacheBytes += bytes
        enforceImageBudget(keepKey = cacheKey)
        return finalBmp
    }

    fun getVideoFrame(
        clip: TimelineClip,
        remappedTimeMs: Long,
        chromaKeyEnabled: Boolean,
        chromaKeyStrength: Float
    ): Bitmap? {
        val src = clip.mediaSource ?: return null
        if (!src.isVideo || src.uriString.isNullOrBlank()) return null

        val stepMs = when {
            memoryProfile.tier == MemoryTier.CRITICAL -> 66L
            fastMode -> 50L
            else -> 33L
        }
        val mmr = obtainRetriever(clip.id, src.uriString) ?: return null
        val srcDur = sourceDurationsMs[clip.id]
        val clampedTime = if (srcDur != null && srcDur > 0L) {
            remappedTimeMs.coerceIn(0L, (srcDur - 1L).coerceAtLeast(0L))
        } else {
            remappedTimeMs.coerceAtLeast(0L)
        }
        val bucketMs = (clampedTime / stepMs) * stepMs
        val cacheKey = "${clip.id}_$chromaKeyEnabled"
        val cached = cachedVideoFrames[cacheKey]
        if (lastVideoBucket[cacheKey] == bucketMs && cached != null && !cached.isRecycled) {
            return cached
        }

        val decodeW = minOf(targetW, memoryProfile.maxDecodeEdgePx)
        val decodeH = minOf(targetH, memoryProfile.maxDecodeEdgePx)

        val raw = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mmr.getScaledFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, decodeW, decodeH)
                    ?: mmr.getScaledFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, decodeW, decodeH)
            } else {
                mmr.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: mmr.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        }.getOrNull() ?: return cached?.takeIf { !it.isRecycled }

        val sized = if (raw.width > decodeW || raw.height > decodeH) {
            Bitmap.createScaledBitmap(raw, decodeW, decodeH, true).also {
                if (it !== raw && !raw.isRecycled) raw.recycle()
            }
        } else {
            raw
        }

        val processed = if (chromaKeyEnabled) {
            val keyed = TransitionAndChromaEngine.applyGreenScreenChromaKey(sized, chromaKeyStrength)
            if (keyed !== sized && !sized.isRecycled) sized.recycle()
            keyed
        } else {
            sized
        }

        cachedVideoFrames.put(cacheKey, processed)?.let { previous ->
            if (previous !== processed && !previous.isRecycled) {
                deferredRecycleQueue.add(previous)
            }
        }
        lastVideoBucket[cacheKey] = bucketMs
        enforceVideoFrameCacheLimit(keepKey = cacheKey)
        return processed
    }

    fun buildBlurProxy(source: Bitmap, downFactor: Float): Bitmap {
        val bw = (source.width * downFactor).toInt().coerceIn(8, 512)
        val bh = (source.height * downFactor).toInt().coerceIn(8, 512)
        val existing = reusableBlurScratch
        if (existing != null && !existing.isRecycled && existing.width == bw && existing.height == bh) {
            val c = android.graphics.Canvas(existing)
            c.drawBitmap(source, null, android.graphics.Rect(0, 0, bw, bh), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            return existing
        }
        if (existing != null && !existing.isRecycled) {
            deferredRecycleQueue.add(existing)
        }
        val created = Bitmap.createScaledBitmap(source, bw, bh, true)
        reusableBlurScratch = created
        return created
    }

    private fun obtainRetriever(clipId: String, uriString: String): MediaMetadataRetriever? {
        retrieverLru[clipId]?.let { return it }
        enforceVideoRetrieverBudget(reserveOneSlot = true)
        return runCatching {
            val mmr = MediaMetadataRetriever()
            if (MediaProbeHelper.setRetrieverDataSource(context, mmr, uriString)) {
                retrieverLru[clipId] = mmr
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durMs > 0L) sourceDurationsMs[clipId] = durMs
                mmr
            } else {
                mmr.release()
                null
            }
        }.getOrNull()
    }

    private fun decodeSampledBitmap(
        uriString: String?,
        drawableName: String?,
        reqW: Int,
        reqH: Int,
        allowRgb565: Boolean
    ): Bitmap? {
        return when {
            uriString != null -> runCatching {
                val uri = Uri.parse(uriString)
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
                val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, reqW, reqH)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = if (allowRgb565 && boundsOpts.outMimeType == "image/jpeg") {
                        Bitmap.Config.RGB_565
                    } else {
                        Bitmap.Config.ARGB_8888
                    }
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            }.getOrNull()
            drawableName != null -> runCatching {
                val resId = drawableResolver(drawableName)
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, resId, boundsOpts)
                val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, reqW, reqH)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = if (allowRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeResource(context.resources, resId, decodeOpts)
            }.getOrNull()
            else -> null
        }
    }

    private fun enforceImageBudget(keepKey: String? = null) {
        val budget = memoryProfile.maxBitmapCacheBytes
        if (currentImageCacheBytes <= budget) return
        val iter = imageLru.entries.iterator()
        while (iter.hasNext() && currentImageCacheBytes > budget) {
            val entry = iter.next()
            if (entry.key == keepKey) continue
            currentImageCacheBytes -= entry.value.byteCount
            if (!entry.value.bitmap.isRecycled) {
                deferredRecycleQueue.add(entry.value.bitmap)
            }
            iter.remove()
        }
    }

    private fun enforceVideoRetrieverBudget(reserveOneSlot: Boolean = false) {
        val limit = (memoryProfile.maxOpenVideoRetrievers - if (reserveOneSlot) 1 else 0).coerceAtLeast(1)
        val iter = retrieverLru.entries.iterator()
        while (iter.hasNext() && retrieverLru.size > limit) {
            val entry = iter.next()
            runCatching { entry.value.release() }
            iter.remove()
        }
    }

    private fun enforceVideoFrameCacheLimit(keepKey: String? = null) {
        val limit = memoryProfile.maxOpenVideoRetrievers.coerceAtLeast(1)
        val iter = cachedVideoFrames.entries.iterator()
        while (iter.hasNext() && cachedVideoFrames.size > limit) {
            val entry = iter.next()
            if (entry.key == keepKey) continue
            if (!entry.value.isRecycled) deferredRecycleQueue.add(entry.value)
            iter.remove()
        }
    }

    fun release() {
        flushDeferredRecycles()
        imageLru.values.forEach { if (!it.bitmap.isRecycled) runCatching { it.bitmap.recycle() } }
        imageLru.clear()
        currentImageCacheBytes = 0L

        cachedVideoFrames.values.forEach { if (!it.isRecycled) runCatching { it.recycle() } }
        cachedVideoFrames.clear()

        reusableBlurScratch?.let { if (!it.isRecycled) runCatching { it.recycle() } }
        reusableBlurScratch = null

        retrieverLru.values.forEach { runCatching { it.release() } }
        retrieverLru.clear()
    }

    companion object {
        fun calculateInSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
            if (rawW <= 0 || rawH <= 0 || reqW <= 0 || reqH <= 0) return 1
            var inSampleSize = 1
            if (rawH > reqH || rawW > reqW) {
                val halfH = rawH / 2
                val halfW = rawW / 2
                while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }
    }
}
