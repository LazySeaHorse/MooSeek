package com.mooseek

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI

class RemoteControlActivity : AppCompatActivity() {
    private lateinit var ws: WebSocketClient
    private var isPlaying = false
    private var position = 0
    private var songId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val status = TextView(this)
        val playPause = Button(this)
        val next = Button(this).apply { text = "Next" }
        val prev = Button(this).apply { text = "Prev" }
        val seek = SeekBar(this)
        playPause.text = "Play"
        layout.addView(status)
        layout.addView(playPause)
        layout.addView(prev)
        layout.addView(next)
        layout.addView(seek)
        setContentView(layout)

        ws = object : WebSocketClient(URI("ws://10.0.2.2:8080")) { // 10.0.2.2 for Android emulator
            override fun onOpen(handshakedata: ServerHandshake?) {
                send(JSONObject().apply { put("type", "get_state") }.toString())
            }
            override fun onMessage(message: String?) {
                runOnUiThread {
                    val data = JSONObject(message)
                    if (data.optString("type") == "state") {
                        isPlaying = data.optBoolean("isPlaying")
                        position = data.optInt("position")
                        songId = data.optString("songId")
                        playPause.text = if (isPlaying) "Pause" else "Play"
                        status.text = "Song: $songId\nPos: $position\nPlaying: $isPlaying"
                        seek.progress = position
                    }
                }
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        ws.connect()

        playPause.setOnClickListener {
            val cmd = JSONObject().apply {
                put("type", "command")
                put("source", "phone")
                put("command", JSONObject().apply { put("isPlaying", !isPlaying) })
            }
            ws.send(cmd.toString())
        }
        next.setOnClickListener {
            val cmd = JSONObject().apply {
                put("type", "command")
                put("source", "phone")
                put("command", JSONObject().apply { put("next", true) })
            }
            ws.send(cmd.toString())
        }
        prev.setOnClickListener {
            val cmd = JSONObject().apply {
                put("type", "command")
                put("source", "phone")
                put("command", JSONObject().apply { put("prev", true) })
            }
            ws.send(cmd.toString())
        }
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val cmd = JSONObject().apply {
                        put("type", "command")
                        put("source", "phone")
                        put("command", JSONObject().apply { put("position", progress) })
                    }
                    ws.send(cmd.toString())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    override fun onDestroy() {
        ws.close()
        super.onDestroy()
    }
} 
