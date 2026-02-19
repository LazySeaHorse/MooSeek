package com.mooseek

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "mooseek_settings",
        Context.MODE_PRIVATE
    )
    
    private val _serverEnabled = MutableStateFlow(getServerEnabled())
    val serverEnabled: StateFlow<Boolean> = _serverEnabled.asStateFlow()
    
    private val _shuffleStrategy = MutableStateFlow(getShuffleStrategy())
    val shuffleStrategy: StateFlow<String> = _shuffleStrategy.asStateFlow()
    
    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    
    fun setServerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVER_ENABLED, enabled).apply()
        _serverEnabled.value = enabled
    }
    
    fun getServerEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVER_ENABLED, false)
    }
    
    fun setShuffleStrategy(strategy: String) {
        prefs.edit().putString(KEY_SHUFFLE_STRATEGY, strategy).apply()
        _shuffleStrategy.value = strategy
    }
    
    fun getShuffleStrategy(): String {
        return prefs.getString(KEY_SHUFFLE_STRATEGY, "Standard Shuffle") ?: "Standard Shuffle"
    }
    
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }
    
    fun getThemeMode(): ThemeMode {
        val modeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.valueOf(modeName ?: ThemeMode.SYSTEM.name)
    }
    
    companion object {
        private const val KEY_SERVER_ENABLED = "server_enabled"
        private const val KEY_SHUFFLE_STRATEGY = "shuffle_strategy"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}
