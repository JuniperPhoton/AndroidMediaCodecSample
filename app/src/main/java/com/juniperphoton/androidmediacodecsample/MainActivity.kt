package com.juniperphoton.androidmediacodecsample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity(), Runnable {
    companion object {
        private var permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private lateinit var audioEncoderCore: AudioEncoderCore
    private var backgroundThread: Thread? = null

    private lateinit var button: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioEncoderCore = AudioEncoderCore(this)

        button = findViewById(R.id.btn)
        statusText = findViewById(R.id.statusText)

        button.setOnClickListener {
            when (audioEncoderCore.recordStatus) {
                AudioEncoderCore.RECORD_STATUS_INITED -> {
                    runOnPermissionsGranted {
                        backgroundThread = Thread(this).apply {
                            start()
                        }
                    }
                }
                AudioEncoderCore.RECORD_STATUS_STARTED -> {
                    audioEncoderCore.stop()
                    statusText.text = "Stopping..."
                }
            }
        }

        audioEncoderCore.onStarted = {
            runOnUiThread {
                button.text = "Stop"
                button.isEnabled = true
                statusText.text = "Working..."
            }
        }
        audioEncoderCore.onCompleted = {
            runOnUiThread {
                button.text = "Start"
                statusText.text = "Completed..."
            }
        }

        requestPermissions(permissions, 0)
    }

    private fun allPermissionsGranted(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun runOnPermissionsGranted(block: () -> Unit) {
        if (allPermissionsGranted()) {
            block()
        }
    }

    override fun run() {
        audioEncoderCore.record()
    }
}
