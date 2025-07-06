package com.mooseek

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URLDecoder

class MediaServerService : Service() {
    
    private var server: MediaServer? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        const val CHANNEL_ID = "MooseekServerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_LOG = "com.mooseek.ACTION_LOG"
        const val EXTRA_LOG = "log_message"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        val repository = MediaRepository(contentResolver)
        server = MediaServer(this, repository, 8080).apply {
            try {
                start()
                Log.d("Mooseek", "Server started on port 8080")
                sendLog("Server started on port 8080")
            } catch (e: IOException) {
                Log.e("Mooseek", "Failed to start server", e)
                sendLog("Failed to start server: ${e.message}")
            }
        }
    }
    
    override fun onDestroy() {
        server?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mooseek Server",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mooseek Server")
            .setContentText("Media server running on ${getIpAddress()}:8080")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }
    
    private fun sendLog(message: String) {
        val intent = Intent(ACTION_LOG).apply {
            putExtra(EXTRA_LOG, message)
        }
        sendBroadcast(intent)
    }
    
    inner class MediaServer(
        private val context: Context,
        private val repository: MediaRepository,
        port: Int
    ) : NanoHTTPD(port) {
        
        private val okHttpClient = OkHttpClient()
        private val json = Json { ignoreUnknownKeys = true }
        
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method
            
            sendLog("$method $uri")
            
            return when {
                uri == "/" -> serveAsset("index.html", "text/html")
                uri.endsWith(".js") -> serveAsset(uri.substring(1), "application/javascript")
                uri.endsWith(".css") -> serveAsset(uri.substring(1), "text/css")
                uri == "/list" -> handleList()
                uri.startsWith("/stream/") -> handleStream(uri.substring(8), session)
                uri == "/lyrics" -> handleLyrics(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
        
        private fun serveAsset(filename: String, mimeType: String): Response {
            return try {
                val input = context.assets.open(filename)
                newFixedLengthResponse(Response.Status.OK, mimeType, input, input.available().toLong())
            } catch (e: IOException) {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
        
        private fun handleList(): Response {
            return runBlocking {
                try {
                    val songs = repository.getAllSongs()
                    val jsonData = json.encodeToString(songs)
                    newFixedLengthResponse(Response.Status.OK, "application/json", jsonData)
                } catch (e: Exception) {
                    sendLog("Error fetching songs: ${e.message}")
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
                }
            }
        }
        
        private fun handleStream(encodedPath: String, session: IHTTPSession): Response {
            val path = URLDecoder.decode(encodedPath, "UTF-8")
            val file = File(path)
            
            if (!file.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
            }
            
            val mimeType = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/*"
            }
            
            val fileLength = file.length()
            val range = session.headers["range"]
            
            return if (range != null) {
                val ranges = parseRange(range, fileLength)
                if (ranges != null) {
                    val (start, end) = ranges
                    val contentLength = end - start + 1
                    
                    val response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        mimeType,
                        FileInputStream(file).apply { skip(start) },
                        contentLength
                    )
                    
                    response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                    response.addHeader("Accept-Ranges", "bytes")
                    response
                } else {
                    newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "Invalid range")
                }
            } else {
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    mimeType,
                    FileInputStream(file),
                    fileLength
                )
                response.addHeader("Accept-Ranges", "bytes")
                response
            }
        }
        
        private fun parseRange(range: String, fileLength: Long): Pair<Long, Long>? {
            val regex = "bytes=(\\d*)-(\\d*)".toRegex()
            val match = regex.find(range) ?: return null
            
            val start = match.groupValues[1].toLongOrNull() ?: 0
            val end = match.groupValues[2].toLongOrNull() ?: (fileLength - 1)
            
            return if (start <= end && end < fileLength) {
                start to end
            } else {
                null
            }
        }
        
        private fun handleLyrics(session: IHTTPSession): Response {
            val params = session.parameters
            val artist = params["artist"]?.firstOrNull() ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "text/plain", "Missing artist parameter"
            )
            val title = params["title"]?.firstOrNull() ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "text/plain", "Missing title parameter"
            )
            val apiKey = params["apiKey"]?.firstOrNull() ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, "text/plain", "Missing apiKey parameter"
            )
            
            return runBlocking {
                try {
                    val url = "https://api.musixmatch.com/ws/1.1/matcher.subtitle.get?" +
                            "q_track=$title&q_artist=$artist&apikey=$apiKey&format=json"
                    
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    val body = response.body?.string() ?: "{}"
                    
                    newFixedLengthResponse(Response.Status.OK, "application/json", body)
                } catch (e: Exception) {
                    sendLog("Error fetching lyrics: ${e.message}")
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
                }
            }
        }
    }
}