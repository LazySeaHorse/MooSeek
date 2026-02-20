package com.mooseek.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val mediaRepository = MediaRepository(application.contentResolver)
    private val settingsRepository = SettingsRepository(application)
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    
    private val _isControllerReady = MutableStateFlow(false)
    val isControllerReady: StateFlow<Boolean> = _isControllerReady.asStateFlow()
    
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
            mediaController?.let { controller ->
                setupPlayerListener(controller)
                _isControllerReady.value = true
                
                // Sync initial state
                syncPlaybackState(controller)
            }
        }, MoreExecutors.directExecutor())
    }
    
    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentIndex = controller.currentMediaItemIndex
                val currentSong = _playbackState.value.queue.getOrNull(currentIndex)
                _playbackState.value = _playbackState.value.copy(
                    currentSong = currentSong,
                    currentIndex = currentIndex
                )
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _playbackState.value = _playbackState.value.copy(
                        duration = controller.duration
                    )
                }
            }
        })
        
        // Start position updates
        startPositionUpdates()
    }
    
    private fun syncPlaybackState(controller: MediaController) {
        // Sync player state from MediaController
        _playbackState.value = _playbackState.value.copy(
            isPlaying = controller.isPlaying,
            currentPosition = controller.currentPosition,
            duration = controller.duration,
            currentIndex = controller.currentMediaItemIndex
        )
        
        // Sync additional state from service (queue, shuffle, etc.)
        MusicPlaybackService.getPlaybackState()?.let { serviceState ->
            _playbackState.value = _playbackState.value.copy(
                queue = serviceState.queue,
                originalQueue = serviceState.originalQueue,
                currentSong = serviceState.currentSong,
                shuffleEnabled = serviceState.shuffleEnabled,
                currentShuffleStrategy = serviceState.currentShuffleStrategy
            )
        }
    }
    
    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPosition = controller.currentPosition
                        )
                    }
                }
                delay(500) // Update every 500ms
            }
        }
    }
    
    fun playSong(song: Song) {
        val index = _filteredSongs.value.indexOf(song)
        if (index != -1) {
            playQueue(_filteredSongs.value, index)
        }
    }
    
    fun playQueue(queue: List<Song>, startIndex: Int = 0) {
        // Update queue metadata immediately (this is app-specific, not from player)
        _playbackState.value = _playbackState.value.copy(
            queue = queue,
            originalQueue = queue,
            currentIndex = startIndex,
            currentSong = queue.getOrNull(startIndex)
        )
        
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
        
        // Wait for controller to be ready, then sync state
        viewModelScope.launch {
            waitForController()
            mediaController?.let { syncPlaybackState(it) }
        }
    }
    
    fun play() {
        viewModelScope.launch {
            waitForController()
            mediaController?.play()
        }
    }
    
    fun pause() {
        viewModelScope.launch {
            waitForController()
            mediaController?.pause()
        }
    }
    
    fun skipToNext() {
        viewModelScope.launch {
            waitForController()
            mediaController?.seekToNext()
        }
    }
    
    fun skipToPrevious() {
        viewModelScope.launch {
            waitForController()
            mediaController?.seekToPrevious()
        }
    }
    
    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            waitForController()
            mediaController?.seekTo(positionMs)
        }
    }
    
    private suspend fun waitForController(timeoutMs: Long = 5000) {
        val startTime = System.currentTimeMillis()
        while (mediaController == null && System.currentTimeMillis() - startTime < timeoutMs) {
            delay(50)
        }
    }
    
    fun toggleShuffle() {
        val intent = Intent(getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_TOGGLE_SHUFFLE
        }
        getApplication<Application>().startService(intent)
        
        // Wait a moment for service to process, then sync state
        viewModelScope.launch {
            delay(100)
            mediaController?.let { syncPlaybackState(it) }
        }
    }
    
    fun setShuffleStrategy(strategyName: String) {
        settingsRepository.setShuffleStrategy(strategyName)
        
        val intent = Intent(getApplication(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SET_SHUFFLE_STRATEGY
            putExtra(MusicPlaybackService.EXTRA_STRATEGY_NAME, strategyName)
        }
        getApplication<Application>().startService(intent)
        
        // Wait a moment for service to process, then sync state
        viewModelScope.launch {
            delay(100)
            mediaController?.let { syncPlaybackState(it) }
        }
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
        mediaController?.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
