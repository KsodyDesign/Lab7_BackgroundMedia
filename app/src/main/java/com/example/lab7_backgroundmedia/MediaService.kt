package com.example.lab7_backgroundmedia

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MediaService : Service() {

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_QUERY = "action_query"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_STOP = "action_stop"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"

        const val BROADCAST_STATE_CHANGED = "com.example.player.STATE_CHANGED"
        const val EXTRA_STATE = "state"
        const val EXTRA_TITLE = "title"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TOTAL = "total"
    }

    private val playlist = listOf(
        TrackInfo(R.raw.sample_audio_1, "Track 1 - Demo melody 1", "Локальный учебный пример"),
        TrackInfo(R.raw.sample_audio_2, "Track 2 - Demo melody 2", "Локальный учебный пример"),
        TrackInfo(R.raw.sample_audio_3, "Track 3 - Demo melody 3", "Локальный учебный пример")
    )

    private var currentIndex = 0
    private val currentTrack: TrackInfo
        get() = playlist[currentIndex]

    private var currentState = "stopped"
    private var foreground = false
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "MediaServiceSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { safely { play() } }
                override fun onPause() { safely { pause() } }
                override fun onStop() { safely { stopAndDestroy() } }
                override fun onSkipToNext() { safely { next() } }
                override fun onSkipToPrevious() { safely { previous() } }
            })
            setPlaybackState(PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 0f).build())
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUERY) {
            sendState(currentState, currentTrack.title)
            if (!foreground) stopSelf(startId)
            return START_NOT_STICKY
        }
        // Every foreground-service launch must promote promptly, even Pause/Stop
        // pressed before the first Play. Loading resources happens afterwards.
        if (!foreground) {
            startForeground(NOTIFICATION_ID, buildNotification(currentTrack.title, "Готов к воспроизведению", false))
            foreground = true
        }
        try {
            when (intent?.action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_STOP -> stopAndDestroy()
                ACTION_NEXT -> next()
                ACTION_PREVIOUS -> previous()
                else -> play()
            }
        } catch (error: Exception) {
            handlePlaybackError()
        }
        return START_NOT_STICKY
    }

    private fun safely(action: () -> Unit) {
        try { action() } catch (error: Exception) { handlePlaybackError() }
    }

    private fun loadCurrentTrack() {
        mediaPlayer?.release()
        mediaPlayer = null
        mediaPlayer = MediaPlayer.create(this, currentTrack.resId)
            ?: throw IllegalStateException("Не удалось открыть трек")

        mediaPlayer?.setOnCompletionListener {
            // Автопереход к следующему треку (Критерий задания)
            safely {
                if (currentIndex < playlist.lastIndex) {
                    currentIndex++
                    loadCurrentTrack()
                    play()
                } else {
                    stopAndDestroy()
                }
            }
        }

        mediaPlayer?.setOnErrorListener { _, _, _ ->
            handlePlaybackError()
            true
        }
    }

    private fun play() {
        if (mediaPlayer == null) {
            loadCurrentTrack()
        }

        mediaPlayer?.start()
        startForeground(NOTIFICATION_ID, buildNotification(currentTrack.title, "Воспроизводится", true))
        foreground = true
        sendState("playing", currentTrack.title)
    }

    private fun pause() {
        if (mediaPlayer == null) {
            stopAndDestroy()
            return
        }
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        updateNotification(currentTrack.title, "На паузе", false)
        sendState("paused", currentTrack.title)
    }

    private fun next() {
        if (currentIndex < playlist.lastIndex) {
            currentIndex++
            loadCurrentTrack()
            play()
        } else {
            // Зацикливание на первый трек
            currentIndex = 0
            loadCurrentTrack()
            play()
        }
    }

    private fun previous() {
        if (currentIndex > 0) {
            currentIndex--
            loadCurrentTrack()
            play()
        } else {
            // На последний трек
            currentIndex = playlist.lastIndex
            loadCurrentTrack()
            play()
        }
    }

    private fun stopAndDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        sendState("stopped", currentTrack.title)
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun handlePlaybackError() {
        mediaPlayer?.release()
        mediaPlayer = null
        sendState("error", currentTrack.title)
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun sendState(state: String, title: String) {
        currentState = state
        val playing = state == "playing"
        val sessionState = when (state) {
            "playing" -> PlaybackStateCompat.STATE_PLAYING
            "paused" -> PlaybackStateCompat.STATE_PAUSED
            "error" -> PlaybackStateCompat.STATE_ERROR
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        mediaSession.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrack.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer?.duration?.toLong() ?: 0L)
            .build())
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(sessionState, mediaPlayer?.currentPosition?.toLong() ?: 0L, if (playing) 1f else 0f)
            .build())
        val intent = Intent(BROADCAST_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_INDEX, currentIndex + 1)
            putExtra(EXTRA_TOTAL, playlist.size)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun buildNotification(title: String, text: String, isPlaying: Boolean): Notification {
        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MediaService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Пауза" else "Играть"

        val playPauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MediaService::class.java).apply { action = playPauseAction },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("${currentTrack.artist} — $text")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSubText("${currentIndex + 1} из ${playlist.size}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Назад", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Вперед", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )

        return builder.build()
    }

    private fun updateNotification(title: String, text: String, isPlaying: Boolean) {
        val notification = buildNotification(title, text, isPlaying)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновое воспроизведение медиа",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал управления воспроизведением через MediaSession"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }
}
