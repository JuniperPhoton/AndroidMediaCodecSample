package com.juniperphoton.androidmediacodecsample.activity

import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.juniperphoton.androidmediacodecsample.R
import com.juniperphoton.androidmediacodecsample.core.AudioEncodeCase
import com.juniperphoton.androidmediacodecsample.core.EncodeCase
import com.juniperphoton.androidmediacodecsample.core.VolumeAudioEffect
import java.io.File

class AudioEncodeCaseActivity : AppCompatActivity(), Runnable {
    private lateinit var button: Button
    private lateinit var statusText: TextView
    private lateinit var seekBar: SeekBar

    private lateinit var case: EncodeCase
    private var backgroundThread: Thread? = null

    private var volumeAudioEffect = VolumeAudioEffect()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_encode_case)

        button = findViewById(R.id.startButton)
        statusText = findViewById(R.id.statusText)
        seekBar = findViewById(R.id.seekBar)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeAudioEffect.volume = (progress * 1f) / 100
            }
        })

        case = AudioEncodeCase.Builder(this) {
            inputFilePath = "/sdcard/audio_test.mp3"
            val name = "${System.currentTimeMillis()}.mp3"
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            outputFilePath = File(path, name).path

            effect = volumeAudioEffect
        }.build()

        button.setOnClickListener {
            when (case.recordStatus) {
                EncodeCase.RECORD_STATUS_INITIALIZED -> {
                    backgroundThread = Thread(this).apply {
                        start()
                    }
                }
                EncodeCase.RECORD_STATUS_STARTED -> {
                    case.stop()
                    statusText.text = "Stopping..."
                }
            }
        }

        case.onStarted = {
            runOnUiThread {
                button.text = "Stop"
                button.isEnabled = true
                statusText.text = "Working..."
            }
        }
        case.onCompleted = {
            runOnUiThread {
                button.text = "Start"
                statusText.text = "Completed..."
            }
        }
    }

    override fun run() {
        case.start()
    }
}