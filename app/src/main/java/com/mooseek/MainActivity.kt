package com.mooseek

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView
    private val logBuilder = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MediaServerService.ACTION_LOG) {
                val log = intent.getStringExtra(MediaServerService.EXTRA_LOG) ?: return
                appendLog(log)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        scrollView = ScrollView(this).apply {
            setPadding(16, 16, 16, 16)
        }
        
        logTextView = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextColor(android.graphics.Color.BLACK)
        }
        
        scrollView.addView(logTextView)
        setContentView(scrollView)
        
        if (checkPermissions()) {
            startMediaServer()
        }
    }
    
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MediaServerService.ACTION_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }
    
    override fun onStop() {
        super.onStop()
        unregisterReceiver(logReceiver)
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        return if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
            false
        } else {
            true
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startMediaServer()
        }
    }
    
    private fun startMediaServer() {
        val serverAddress = "http://${getIpAddress()}:8080"
        logTextView.text = "Server running at $serverAddress\n\n"
        
        val serviceIntent = Intent(this, MediaServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
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
    
    private fun appendLog(message: String) {
        val timestamp = dateFormat.format(Date())
        logBuilder.append("[$timestamp] $message\n")
        
        runOnUiThread {
            logTextView.text = logTextView.text.toString() + "[$timestamp] $message\n"
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}