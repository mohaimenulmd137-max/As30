package com.example.domain.engine

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.example.domain.model.StudioFontOption
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CustomFontManager {

    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val composeFamilyCache = ConcurrentHashMap<String, FontFamily>()

    /**
     * Copies a picked .ttf / .otf font file from a Document URI into internal storage
     * so it remains permanently available across app restarts and MP4 exports.
     */
    fun importFontFromUri(context: Context, uri: Uri): Pair<String, String>? {
        return runCatching {
            val resolver = context.contentResolver
            var displayName = "CustomFont.ttf"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx) ?: displayName
                    }
                }
            }
            val safeFileName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")}"
            val dir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
            val outFile = File(dir, safeFileName)
            resolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            val tf = Typeface.createFromFile(outFile)
            typefaceCache[outFile.absolutePath] = tf
            composeFamilyCache[outFile.absolutePath] = FontFamily(tf)

            val cleanLabel = displayName.substringBeforeLast('.').take(22).ifBlank { "Custom Font" }
            outFile.absolutePath to cleanLabel
        }.getOrNull()
    }

    fun resolveTypeface(context: Context, fontId: String, customFontPath: String?): Typeface {
        if (!customFontPath.isNullOrBlank()) {
            typefaceCache[customFontPath]?.let { return it }
            val file = File(customFontPath)
            if (file.exists()) {
                val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
                if (tf != null) {
                    typefaceCache[customFontPath] = tf
                    return tf
                }
            }
        }
        val opt = StudioFontOption.entries.firstOrNull { it.id == fontId } ?: StudioFontOption.HIND_SILIGURI
        return runCatching { ResourcesCompat.getFont(context, opt.fontResId) }.getOrNull() ?: Typeface.DEFAULT
    }

    fun resolveComposeFontFamily(fontId: String, customFontPath: String?): FontFamily {
        if (!customFontPath.isNullOrBlank()) {
            composeFamilyCache[customFontPath]?.let { return it }
            val file = File(customFontPath)
            if (file.exists()) {
                val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
                if (tf != null) {
                    val fam = FontFamily(tf)
                    typefaceCache[customFontPath] = tf
                    composeFamilyCache[customFontPath] = fam
                    return fam
                }
            }
        }
        return (StudioFontOption.entries.firstOrNull { it.id == fontId } ?: StudioFontOption.HIND_SILIGURI).family
    }
}
