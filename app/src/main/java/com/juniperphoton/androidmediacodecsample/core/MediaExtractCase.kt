package com.juniperphoton.androidmediacodecsample.core

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer

class MediaExtractCase(context: Context) : EncodeCase(context) {
    companion object {
        private const val TAG = "MediaExtractCase"
    }

    private var extractor: MediaExtractor = MediaExtractor()

    init {
        extractor.setDataSource(context, Uri.parse("/sdcard/video_test.mp4"), null)
    }

    private var videoTrack = -1
    private var audioTrack = -2
    private var maxVideoSample = 16384

    override fun start() {
        super.start()

        val tracks = extractor.trackCount
        for (i in 0 until tracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime.startsWith("audio")) {
                extractor.selectTrack(i)
                audioTrack = i
                Log.d(TAG, "audio format is $format, track is $i")
            } else if (mime.startsWith("video")) {
                extractor.selectTrack(i)
                videoTrack = i

                // It's required to know the max input size
                maxVideoSample = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                Log.d(TAG, "video format is $format, track is $i")
            }
        }

        val dstBuffer = ByteBuffer.allocateDirect(maxVideoSample)

        var eos = false
        while (!eos && !requestStop) {
            var time = extractor.sampleTime
            if (time == -1L) {
                break
            }

            val track = extractor.sampleTrackIndex
            if (track == videoTrack) {
                val size = extractor.readSampleData(dstBuffer, 0)
                time = extractor.sampleTime
                Log.d(TAG, "[v] read sample data, size = $size, time = $time")
            } else if (track == audioTrack) {
                val size = extractor.readSampleData(dstBuffer, 0)
                time = extractor.sampleTime
                Log.d(TAG, "[a] read sample data, size = $size, time = $time")
            }

            eos = !extractor.advance()
            if (eos) {
                Log.d(TAG, "reach eos!")
            }
        }
    }
}