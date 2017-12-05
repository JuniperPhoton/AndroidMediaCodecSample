package com.juniperphoton.androidmediacodecsample.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.juniperphoton.androidmediacodecsample.core.EncodeCase
import com.juniperphoton.androidmediacodecsample.core.MediaExtractCase

class MediaExtractCaseActivity : AppCompatActivity(), Runnable {
    private lateinit var case: EncodeCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        case = MediaExtractCase(this)
        Thread(this).start()
    }

    override fun run() {
        case.start()
    }
}