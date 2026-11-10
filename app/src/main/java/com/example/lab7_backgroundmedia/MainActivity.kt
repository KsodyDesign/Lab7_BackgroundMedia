package com.example.lab7_backgroundmedia

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : BaseActivity() {

    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackNumber: TextView
    private lateinit var tvStatus: TextView

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra(MediaService.EXTRA_STATE) ?: "stopped"
            val title = intent?.getStringExtra(MediaService.EXTRA_TITLE) ?: ""
            val index = intent?.getIntExtra(MediaService.EXTRA_INDEX, 1) ?: 1
            val total = intent?.getIntExtra(MediaService.EXTRA_TOTAL, 3) ?: 3

            updateUi(state, title, index, total)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        tvTrackNumber = findViewById(R.id.tvTrackNumber)
        tvStatus = findViewById(R.id.tvStatus)

        // Запрос разрешения на показ уведомлений для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // Кнопки управления сервисом
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            sendServiceAction(MediaService.ACTION_PLAY)
        }

        findViewById<Button>(R.id.btnPause).setOnClickListener {
            sendServiceAction(MediaService.ACTION_PAUSE)
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            sendServiceAction(MediaService.ACTION_NEXT)
        }

        findViewById<Button>(R.id.btnPrevious).setOnClickListener {
            sendServiceAction(MediaService.ACTION_PREVIOUS)
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            sendServiceAction(MediaService.ACTION_STOP)
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, MediaService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateUi(state: String, title: String, index: Int, total: Int) {
        if (title.isNotEmpty()) {
            tvTrackTitle.text = title
        }
        tvTrackNumber.text = "Трек $index из $total"

        when (state) {
            "playing" -> {
                tvStatus.text = "▶ Воспроизводится"
                tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
                tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            }
            "paused" -> {
                tvStatus.text = "⏸ На паузе"
                tvStatus.setBackgroundColor(Color.parseColor("#FFF9C4"))
                tvStatus.setTextColor(Color.parseColor("#F57F17"))
            }
            "stopped" -> {
                tvStatus.text = "⏹ Остановлено"
                tvStatus.setBackgroundColor(Color.parseColor("#EEEEEE"))
                tvStatus.setTextColor(Color.parseColor("#616161"))
            }
            "error" -> {
                tvStatus.text = "⚠ Ошибка трека"
                tvStatus.setBackgroundColor(Color.parseColor("#FFCDD2"))
                tvStatus.setTextColor(Color.parseColor("#C62828"))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stateReceiver,
            IntentFilter(MediaService.BROADCAST_STATE_CHANGED)
        )
        // A normal start from the visible Activity asks for state without starting playback.
        startService(Intent(this, MediaService::class.java).setAction(MediaService.ACTION_QUERY))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
    }
}
