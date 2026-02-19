package com.mooseek

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mooseek.models.Song
import com.mooseek.shuffle.ShuffleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val shuffleManager = ShuffleManager(context)
    
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlaybackState(isPlaying = isPlaying)
                }
                
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIndex = currentMediaItemIndex
                    val currentSong = _playbackState.value.queue.getOrNull(currentIndex)
                    updatePlaybackState(
                        currentSong = currentSong,
                        currentIndex = currentIndex
                    )
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        updatePlaybackState(duration = duration)
                    }
                }
            })
        }
    
    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        val mediaItems = songs.map { song ->
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.id
            )
            MediaItem.fromUri(uri)
        }
        
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        
        updatePlaybackState(
            queue = songs,
            originalQueue = songs,
            currentIndex = startIndex,
            currentSong = songs.getOrNull(startIndex),
            shuffleEnabled = shuffleManager.isShuffleEnabled(),
            currentShuffleStrategy = shuffleManager.getCurrentStrategy().name
        )
    }
    
    fun play() {
        player.play()
    }
    
    fun pause() {
        player.pause()
    }
    
    fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }
    
    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }
    
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }
    
    fun getCurrentPosition(): Long = player.currentPosition
    
    fun toggleShuffle() {
        val currentState = _playbackState.value
        val newShuffleState = !currentState.shuffleEnabled
        shuffleManager.setShuffleEnabled(newShuffleState)
        
        if (newShuffleState) {
            // Apply shuffle
            val shuffledQueue = shuffleManager.applyShuffle(
                currentState.originalQueue,
                currentState.currentIndex
            )
            setQueue(shuffledQueue, 0)
        } else {
            // Restore original order
            val currentSong = currentState.currentSong
            val originalIndex = currentState.originalQueue.indexOfFirst { 
                it.id == currentSong?.id 
            }
            setQueue(currentState.originalQueue, originalIndex.coerceAtLeast(0))
        }
        
        updatePlaybackState(shuffleEnabled = newShuffleState)
    }
    
    fun setShuffleStrategy(strategyName: String) {
        shuffleManager.setStrategyByName(strategyName)
        updatePlaybackState(currentShuffleStrategy = strategyName)
        
        // Re-apply shuffle if currently enabled
        if (_playbackState.value.shuffleEnabled) {
            val currentState = _playbackState.value
            val shuffledQueue = shuffleManager.applyShuffle(
                currentState.originalQueue,
                currentState.currentIndex
            )
            setQueue(shuffledQueue, 0)
        }
    }
    
    fun getAvailableShuffleStrategies() = shuffleManager.getAvailableStrategies()
    
    fun release() {
        player.release()
    }
    
    private fun updatePlaybackState(
        currentSong: Song? = _playbackState.value.currentSong,
        isPlaying: Boolean = _playbackState.value.isPlaying,
        currentPosition: Long = player.currentPosition,
        duration: Long = _playbackState.value.duration,
        queue: List<Song> = _playbackState.value.queue,
        currentIndex: Int = _playbackState.value.currentIndex,
        shuffleEnabled: Boolean = _playbackState.value.shuffleEnabled,
        originalQueue: List<Song> = _playbackState.value.originalQueue,
        currentShuffleStrategy: String = _playbackState.value.currentShuffleStrategy
    ) {
        _playbackState.value = _playbackState.value.copy(
            currentSong = currentSong,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            queue = queue,
            currentIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            originalQueue = originalQueue,
            currentShuffleStrategy = currentShuffleStrategy
        )
    }
}
