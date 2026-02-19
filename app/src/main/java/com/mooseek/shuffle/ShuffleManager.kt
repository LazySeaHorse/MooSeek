package com.mooseek.shuffle

import android.content.Context
import android.content.SharedPreferences
import com.mooseek.models.Song

/**
 * Manages shuffle strategies and applies them to song queues.
 * Persists the selected strategy across app restarts.
 */
class ShuffleManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    private val strategies = listOf(
        StandardShuffle(),
        MillerShuffle(),
        WeightedShuffle()
    )
    
    private var currentStrategy: ShuffleStrategy
    
    init {
        val savedStrategyName = prefs.getString(KEY_STRATEGY, strategies[0].name)
        currentStrategy = strategies.find { it.name == savedStrategyName } ?: strategies[0]
    }
    
    /**
     * Get all available shuffle strategies
     */
    fun getAvailableStrategies(): List<ShuffleStrategy> = strategies
    
    /**
     * Get the currently selected strategy
     */
    fun getCurrentStrategy(): ShuffleStrategy = currentStrategy
    
    /**
     * Set the active shuffle strategy
     */
    fun setStrategy(strategy: ShuffleStrategy) {
        currentStrategy = strategy
        prefs.edit().putString(KEY_STRATEGY, strategy.name).apply()
    }
    
    /**
     * Set strategy by name
     */
    fun setStrategyByName(name: String) {
        strategies.find { it.name == name }?.let { setStrategy(it) }
    }
    
    /**
     * Apply current shuffle strategy to a list of songs
     */
    fun applyShuffle(songs: List<Song>, currentIndex: Int = -1): List<Song> {
        return currentStrategy.shuffle(songs, currentIndex)
    }
    
    /**
     * Check if shuffle is enabled
     */
    fun isShuffleEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
    }
    
    /**
     * Enable or disable shuffle
     */
    fun setShuffleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, enabled).apply()
    }
    
    /**
     * Get the Miller Shuffle instance for special operations
     */
    fun getMillerShuffle(): MillerShuffle? {
        return strategies.find { it is MillerShuffle } as? MillerShuffle
    }
    
    companion object {
        private const val PREFS_NAME = "shuffle_prefs"
        private const val KEY_STRATEGY = "selected_strategy"
        private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    }
}
