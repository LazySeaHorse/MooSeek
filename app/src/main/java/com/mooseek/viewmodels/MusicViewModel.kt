package com.mooseek.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mooseek.MediaRepository
import com.mooseek.MediaServerService
import com.mooseek.MusicPlaybackService
import com.mooseek.PlaybackState
import com.mooseek.SettingsRepository
import com.mooseek.ThemeMode
import com.mooseek.models.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaRepository = MediaRepository(application)
    private val settingsRepository = SettingsRepository(application)
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
    
    private val _serverEnabled = MutableStateFlow(settingsRepository.getServerEnabled())
    val serverEnabled: StateFlow<Boolean> = _serverEnabled.asStateFlow()
    
    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()
    
    val shuffleStrategy: StateFlow<String> = settingsRepository.shuffleStrategy
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
    
    init {
        loadSongs()
        initializeMediaController()
        
        // Initialize shuffle strategy from settings
        val savedStrategy = settingsRepository.getShuffleStrategy()
        _playbackState.value = _playbackState.value.copy(
            currentShuffleStrategy = savedStrategy
        )
        
        // Start server if enabled
        if (settingsRepository.getServerEnabled()) {
            startServer()
        }
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
        settingsRepository.setShuffleStrategy(strategyName)
        
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
    
    fun toggleServer(enabled: Boolean) {
        settingsRepository.setServerEnabled(enabled)
        _serverEnabled.value = enabled
        
        if (enabled) {
            startServer()
        } else {
            stopServer()
        }
    }
    
    private fun startServer() {
        val intent = Intent(getApplication(), MediaServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        
        // Update server URL
        _serverUrl.value = "http://${getIpAddress()}:8080"
    }
    
    private fun stopServer() {
        val intent = Intent(getApplication(), MediaServerService::class.java)
        getApplication<Application>().stopService(intent)
        _serverUrl.value = null
    }
    
    private fun getIpAddress(): String {
        val wifiManager = getApplication<Application>().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }
    
    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }
    
    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
