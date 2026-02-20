package com.mooseek

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mooseek.models.Song

class MusicPlaybackService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private lateinit var musicPlayer: MusicPlayer
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "music_playback_channel"
        const val ACTION_PLAY = "com.mooseek.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mooseek.ACTION_PAUSE"
        const val ACTION_NEXT = "com.mooseek.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mooseek.ACTION_PREVIOUS"
        const val ACTION_TOGGLE_SHUFFLE = "com.mooseek.ACTION_TOGGLE_SHUFFLE"
        const val ACTION_SET_SHUFFLE_STRATEGY = "com.mooseek.ACTION_SET_SHUFFLE_STRATEGY"
        const val EXTRA_SONGS = "extra_songs"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_STRATEGY_NAME = "extra_strategy_name"
        
        // Static reference to access player state (not ideal but works with MediaSessionService)
        private var instance: MusicPlaybackService? = null
        
        fun getPlaybackState(): PlaybackState? = instance?.musicPlayer?.playbackState?.value
    }
    
    override fun onCreate() {
        super.onCreate()
        
        instance = this
        
        createNotificationChannel()
        musicPlayer = MusicPlayer(this)
        
        mediaSession = MediaSession.Builder(this, musicPlayer.player)
            .build()
        
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_PLAY -> {
                val songs = intent.getParcelableArrayListExtra<Song>(EXTRA_SONGS)
                val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                
                if (songs != null && songs.isNotEmpty()) {
                    musicPlayer.setQueue(songs, startIndex)
                    musicPlayer.play()
                } else {
                    musicPlayer.play()
                }
            }
            ACTION_PAUSE -> musicPlayer.pause()
            ACTION_NEXT -> musicPlayer.skipToNext()
            ACTION_PREVIOUS -> musicPlayer.skipToPrevious()
            ACTION_TOGGLE_SHUFFLE -> musicPlayer.toggleShuffle()
            ACTION_SET_SHUFFLE_STRATEGY -> {
                val strategyName = intent.getStringExtra(EXTRA_STRATEGY_NAME)
                if (strategyName != null) {
                    musicPlayer.setShuffleStrategy(strategyName)
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        musicPlayer.release()
        instance = null
        super.onDestroy()
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == false) {
            stopSelf()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MooSeek")
            .setContentText("Music Player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
