package com.sidharth.lumen.feature.speech

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sidharth.lumen.MainActivity
import com.sidharth.lumen.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps read-aloud running when the reader is not on screen (M11).
 *
 * This is the only reason Lumen asks for any permission. Android will not let audio
 * continue in the background without a foreground service, and from Android 13 that
 * service must be able to post a notification — which is also what gives the
 * lock-screen play/pause controls. The notification is the feature, not a nag.
 *
 * **The notification is posted here rather than left to Media3.** `MediaSessionService`
 * only raises its own notification once its player reports playing through the paths it
 * recognises, and Readium's TTS session adapter does not drive those the way an ExoPlayer
 * would. Relying on it meant the service never reached the foreground, so Android killed
 * the whole process a few seconds after the reader was backgrounded — taking the voice
 * with it. Calling `startForeground` in `onCreate` makes the guarantee ours and is what
 * the 5-second rule expects anyway.
 */
@AndroidEntryPoint
class SpeechService : MediaSessionService() {

    @Inject lateinit var controller: SpeechController

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val navigator = controller.navigator ?: run {
            // Started in a race with release(): nothing to play, so do not sit in the
            // foreground showing a notification for silence.
            stopSelf()
            return
        }

        val openReader = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = MediaSession.Builder(this, navigator.asMedia3Player())
            .setSessionActivity(openReader)
            .build()
        session = built

        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speech_notification)
            .setContentTitle(getString(R.string.speech_notification_title))
            .setContentText(getString(R.string.speech_notification_text))
            .setContentIntent(openReader)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(built))
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.speech_channel_name),
                // Low: the controls should be available without ever making a sound or
                // pushing a heads-up card over the page.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.speech_channel_description)
                setShowBadge(false)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not leave a voice talking to an empty room.
        controller.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            release()
            session = null
        }
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "lumen-read-aloud"
        const val NOTIFICATION_ID = 1
    }
}
