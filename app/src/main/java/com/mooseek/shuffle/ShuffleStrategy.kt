package com.mooseek.shuffle

import com.mooseek.models.Song

/**
 * Interface for different shuffle algorithms.
 * Allows experimentation with various shuffle strategies.
 */
interface ShuffleStrategy {
    /**
     * Shuffle the given list of songs.
     * @param songs The original list of songs to shuffle
     * @param currentIndex The index of the currently playing song (-1 if none)
     * @return A new shuffled list of songs
     */
    fun shuffle(songs: List<Song>, currentIndex: Int = -1): List<Song>
    
    /**
     * Name of the shuffle strategy
     */
    val name: String
    
    /**
     * Description of how this shuffle strategy works
     */
    val description: String
}
