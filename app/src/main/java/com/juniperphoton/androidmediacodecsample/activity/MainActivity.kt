package com.juniperphoton.androidmediacodecsample.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import com.juniperphoton.androidmediacodecsample.R

class MainActivity : AppCompatActivity() {
    companion object {
        private var permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE)

        private var caseActivities = arrayOf(AudioCodecCaseActivity::class.java,
                MediaExtractCaseActivity::class.java)

        private const val TAG = "MainActivity"
    }

    private lateinit var caseList: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        caseList = findViewById(R.id.caseList)
        setupButtons()
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "allPermissionsGranted: ${allPermissionsGranted()}")
    }

    private fun setupButtons() {
        caseActivities.forEach { a ->
            val button = Button(this, null)
            button.text = a.simpleName.toLowerCase()
            button.setOnClickListener {
                startActivity(a)
            }
            caseList.addView(button)
        }
    }

    private fun <T> startActivity(clazz: Class<T>) {
        startActivity(Intent(this, clazz))
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
}
