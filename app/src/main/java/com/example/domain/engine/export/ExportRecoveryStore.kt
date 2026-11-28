package com.example.domain.engine.export

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

object ExportRecoveryStore {

    private const val PREFS_NAME = "chitraflow_export_recovery"
    private const val KEY_RECORD = "active_or_failed_record"

    fun saveRecord(context: Context, record: ExportRecoveryRecord) {
        val json = JSONObject().apply {
            put("exportId", record.exportId)
            put("projectId", record.projectId)
            put("projectName", record.projectName)
            put("resolutionName", record.resolutionName)
            put("fps", record.fps)
            put("codecName", record.codecName)
            put("qualityName", record.qualityName)
            put("modeName", record.modeName)
            put("targetUriString", record.targetUriString ?: "")
            put("tempFilePath", record.tempFilePath ?: "")
            put("lastCompletedFrame", record.lastCompletedFrame)
            put("totalFrames", record.totalFrames)
            put("timestampMs", record.timestampMs)
            put("errorCategoryName", record.errorCategoryName ?: "")
            put("failureReason", record.failureReason ?: "")
        }
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORD, json.toString())
                .apply()
        }
    }

    fun updateProgressFrame(context: Context, exportId: String, frameIndex: Int) {
        val existing = loadRecord(context) ?: return
        if (existing.exportId != exportId) return
        saveRecord(context, existing.copy(lastCompletedFrame = frameIndex, timestampMs = System.currentTimeMillis()))
    }

    fun markFailed(
        context: Context,
        exportId: String,
        category: ExportErrorCategory,
        reason: String
    ) {
        val existing = loadRecord(context) ?: return
        if (existing.exportId != exportId) return
        saveRecord(
            context,
            existing.copy(
                errorCategoryName = category.name,
                failureReason = reason,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    fun loadRecord(context: Context): ExportRecoveryRecord? {
        val raw = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_RECORD, null)
        }.getOrNull() ?: return null
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            ExportRecoveryRecord(
                exportId = obj.getString("exportId"),
                projectId = obj.getString("projectId"),
                projectName = obj.optString("projectName", "Project"),
                resolutionName = obj.optString("resolutionName", "P1080"),
                fps = obj.optInt("fps", 30),
                codecName = obj.optString("codecName", "H264"),
                qualityName = obj.optString("qualityName", "HIGH"),
                modeName = obj.optString("modeName", "SMART_AUTO"),
                targetUriString = obj.optString("targetUriString").takeIf { it.isNotBlank() },
                tempFilePath = obj.optString("tempFilePath").takeIf { it.isNotBlank() },
                lastCompletedFrame = obj.optInt("lastCompletedFrame", 0),
                totalFrames = obj.optInt("totalFrames", 0),
                timestampMs = obj.optLong("timestampMs", 0L),
                errorCategoryName = obj.optString("errorCategoryName").takeIf { it.isNotBlank() },
                failureReason = obj.optString("failureReason").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    fun cleanupAbandonedArtifactsAndClear(context: Context) {
        val rec = loadRecord(context)
        if (rec != null) {
            rec.targetUriString?.let { uriStr ->
                if (uriStr.startsWith("content://")) {
                    runCatching { context.contentResolver.delete(Uri.parse(uriStr), null, null) }
                }
            }
            rec.tempFilePath?.let { path ->
                runCatching {
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            }
        }
        ExportStorageManager.cleanupStaleTempFiles(context)
        clearRecord(context)
    }

    fun clearRecord(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RECORD)
                .apply()
        }
    }
}
