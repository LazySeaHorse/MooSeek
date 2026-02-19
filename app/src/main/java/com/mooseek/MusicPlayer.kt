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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicPlayer(context: Context) {
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
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
            currentIndex = startIndex,
            currentSong = songs.getOrNull(startIndex)
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
    
    fun release() {
        player.release()
    }
    
    private fun updatePlaybackState(
        currentSong: Song? = _playbackState.value.currentSong,
        isPlaying: Boolean = _playbackState.value.isPlaying,
        currentPosition: Long = player.currentPosition,
        duration: Long = _playbackState.value.duration,
        queue: List<Song> = _playbackState.value.queue,
        currentIndex: Int = _playbackState.value.currentIndex
    ) {
        _playbackState.value = _playbackState.value.copy(
            currentSong = currentSong,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            queue = queue,
            currentIndex = currentIndex
        )
    }
}
