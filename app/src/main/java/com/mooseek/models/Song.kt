package com.mooseek.models

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long
)