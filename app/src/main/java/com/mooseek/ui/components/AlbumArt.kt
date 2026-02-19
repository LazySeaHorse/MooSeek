package com.mooseek.ui.components

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun AlbumArt(
    albumId: Long,
    modifier: Modifier = Modifier
) {
    val albumArtUri: Uri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        albumId
    )
    
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = albumArtUri,
            contentDescription = "Album Art",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            error = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "No Album Art",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
