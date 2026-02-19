package com.mooseek.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mooseek.MediaRepository
import com.mooseek.MusicPlaybackService
import com.mooseek.PlaybackState
import com.mooseek.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaRepository = MediaRepository(application)
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    
    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    init {
        loadSongs()
        initializeMediaController()
    }
    
    private fun loadSongs() {
        viewModelScope.launch {
            val loadedSongs = mediaRepository.getAllSongs()
            _songs.value = loadedSongs
            _filteredSongs.value = loadedSongs
        }
    }
    
    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicPlaybackService::class.java)
        )
        
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())
    }
    
    fun playSong(song: Song) {
        val index = _filteredSongs.value.indexOf(song)
        if (index != -1) {
            playQueue(_filteredSongs.value, index)
        }
    }
    
    fun playQueue(queue: List<Song>, startIndex: Int = 0) {
        val intent = Intent(getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_PLAY
            putExtra(MusicPlaybackService.EXTRA_SONGS, ArrayList(queue))
            putExtra(MusicPlaybackService.EXTRA_START_INDEX, startIndex)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        
        _playbackState.value = _playbackState.value.copy(
            queue = queue,
            currentIndex = startIndex,
            currentSong = queue.getOrNull(startIndex)
        )
    }
    
    fun play() {
        mediaController?.play()
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
    }
    
    fun pause() {
        mediaController?.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }
    
    fun skipToNext() {
        mediaController?.seekToNext()
    }
    
    fun skipToPrevious() {
        mediaController?.seekToPrevious()
    }
    
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }
    
    fun toggleShuffle() {
        val intent = Intent(getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_TOGGLE_SHUFFLE
        }
        getApplication<Application>().startService(intent)
        
        _playbackState.value = _playbackState.value.copy(
            shuffleEnabled = !_playbackState.value.shuffleEnabled
        )
    }
    
    fun setShuffleStrategy(strategyName: String) {
        val intent = Intent(getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SET_SHUFFLE_STRATEGY
            putExtra(MusicPlaybackService.EXTRA_STRATEGY_NAME, strategyName)
        }
        getApplication<Application>().startService(intent)
        
        _playbackState.value = _playbackState.value.copy(
            currentShuffleStrategy = strategyName
        )
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterSongs(query)
    }
    
    private fun filterSongs(query: String) {
        _filteredSongs.value = if (query.isBlank()) {
            _songs.value
        } else {
            _songs.value.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
