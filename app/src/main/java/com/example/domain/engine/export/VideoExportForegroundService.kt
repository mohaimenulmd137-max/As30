package com.example.domain.engine.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.domain.engine.ExportResult
import com.example.domain.engine.MediaProbeHelper

class VideoExportForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "chitraflow_export_channel"
        const val RESULT_CHANNEL_ID = "chitraflow_export_result_channel"
        const val NOTIFICATION_ID = 4401
        const val RESULT_NOTIFICATION_ID = 4402

        const val ACTION_START = "com.example.export.ACTION_START"
        const val ACTION_TOGGLE_PAUSE = "com.example.export.ACTION_TOGGLE_PAUSE"
        const val ACTION_CANCEL = "com.example.export.ACTION_CANCEL"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        fun updateNotificationProgress(context: Context, snap: ExportProgressSnapshot) {
            runCatching {
                ensureChannels(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                nm.notify(NOTIFICATION_ID, buildProgressNotification(context, snap))
            }
        }

        fun notifyCompletedAndStop(context: Context, result: ExportResult) {
            runCatching {
                ensureChannels(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val openIntent = PendingIntent.getActivity(
                    context,
                    10,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val title = if (result.success) {
                    "ভিডিও এক্সপোর্ট সম্পন্ন হয়েছে"
                } else {
                    result.errorCategory?.titleBn ?: "ভিডিও এক্সপোর্ট ব্যর্থ হয়েছে"
                }
                val detail = if (result.success) {
                    "${ExportStorageManager.formatBytesReadable(result.fileSizeBytes)} · ${result.savedLocationLabel}"
                } else {
                    result.errorMessage ?: "আবার চেষ্টা করুন"
                }
                val notif = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(detail)
                    .setContentIntent(openIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                nm.notify(RESULT_NOTIFICATION_ID, notif)
            }
            stopServiceSafely(context)
        }

        fun stopServiceSafely(context: Context) {
            runCatching {
                context.stopService(Intent(context, VideoExportForegroundService::class.java))
            }
        }

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Video Export Progress",
                            NotificationManager.IMPORTANCE_LOW
                        ).apply {
                            description = "Shows background video rendering and encoding progress"
                        }
                    )
                }
                if (nm.getNotificationChannel(RESULT_CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            RESULT_CHANNEL_ID,
                            "Video Export Status",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply {
                            description = "Notifies when background video export completes or needs attention"
                        }
                    )
                }
            }
        }

        private fun buildProgressNotification(
            context: Context,
            snap: ExportProgressSnapshot? = null,
            fallbackTitle: String = "ChitraFlow Studio",
            fallbackSubtitle: String = "ভিডিও এক্সপোর্ট প্রস্তুত হচ্ছে..."
        ): Notification {
            val openAppIntent = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val pausePending = PendingIntent.getService(
                context,
                2,
                Intent(context, VideoExportForegroundService::class.java).apply { action = ACTION_TOGGLE_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cancelPending = PendingIntent.getService(
                context,
                3,
                Intent(context, VideoExportForegroundService::class.java).apply { action = ACTION_CANCEL },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val pct = ((snap?.progress ?: 0.02f) * 100f).toInt().coerceIn(0, 100)
            val title = if (snap != null) {
                val pauseTag = if (snap.isPaused) " [বিরতি]" else ""
                "এক্সপোর্ট $pct%$pauseTag · ${snap.resolutionLabel} (${snap.codecLabel})"
            } else {
                "$fallbackTitle · এক্সপোর্ট চলছে"
            }

            val etaStr = if (snap != null && snap.estimatedRemainingMs > 0L) {
                " · বাকি ${MediaProbeHelper.formatDurationReadable(snap.estimatedRemainingMs)}"
            } else ""
            val content = if (snap != null) {
                "${snap.currentFrame}/${snap.totalFrames} ফ্রেম$etaStr · ${ExportStorageManager.formatBytesReadable(snap.outputBytesWritten)}"
            } else {
                fallbackSubtitle
            }

            val pauseLabel = if (snap?.isPaused == true) "চালু করুন" else "বিরতি"
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, snap == null)
                .setContentIntent(openAppIntent)
                .addAction(0, pauseLabel, pausePending)
                .addAction(0, "বাতিল", cancelPending)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> {
                ExportCoordinator.togglePause()
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                ExportCoordinator.cancelExport(applicationContext)
                stopForegroundSafely()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        ensureChannels(this)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "ChitraFlow Studio"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "ভিডিও এক্সপোর্ট চলছে..."
        val notification = buildProgressNotification(this, fallbackTitle = title, fallbackSubtitle = subtitle)

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                }
                else -> {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } catch (_: Throwable) {
            // Even if OEM foreground start is restricted, ExportCoordinator coroutine stays active
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ FGS 6-hour timeout guard: cleanly halt service before system triggers ANR
        ExportCoordinator.cancelExport(applicationContext)
        stopForegroundSafely()
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundSafely()
        super.onDestroy()
    }

    private fun stopForegroundSafely() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }
}
