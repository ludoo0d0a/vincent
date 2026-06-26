package fr.geoking.vincent.ar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Foreground service that holds a [MediaProjection] while recording the screen
 * (the AR camera feed + the Compose overlay, burned into the MP4). Required on
 * Android 14+, where MediaProjection must run inside a `mediaProjection` FGS.
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            teardown()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> {
                teardown()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
        val density = intent.getIntExtra(EXTRA_DENSITY, 320)
        val output = intent.getStringExtra(EXTRA_OUTPUT) ?: return

        startInForeground()

        if (data == null) {
            teardown(); stopSelf(); return
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mgr.getMediaProjection(resultCode, data)
        if (mp == null) {
            teardown(); stopSelf(); return
        }
        projection = mp
        mp.registerCallback(projectionCallback, null)

        try {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setVideoSize(width, height)
            rec.setVideoFrameRate(30)
            rec.setVideoEncodingBitRate(8_000_000)
            rec.setOutputFile(output)
            rec.prepare()
            recorder = rec

            virtualDisplay = mp.createVirtualDisplay(
                "vincent-ar",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface, null, null,
            )
            rec.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen recording", e)
            teardown(); stopSelf()
        }
    }

    private fun startInForeground() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AR recording", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(channel)
        }
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Vincent")
                .setContentText("Enregistrement AR en cours")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Vincent")
                .setContentText("Enregistrement AR en cours")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun teardown() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ArScreenRecord"
        private const val CHANNEL_ID = "ar_recording"
        private const val NOTIF_ID = 4242

        const val ACTION_START = "fr.geoking.vincent.ar.START_RECORD"
        const val ACTION_STOP = "fr.geoking.vincent.ar.STOP_RECORD"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_OUTPUT = "output_path"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DENSITY = "density"
    }
}
