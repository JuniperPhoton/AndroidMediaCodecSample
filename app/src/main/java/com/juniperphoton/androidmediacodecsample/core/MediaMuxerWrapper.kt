package com.juniperphoton.androidmediacodecsample.core

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer

class MediaMuxerWrapper(private val outputType: Int,
                        private val outputFilePath: String) {
    companion object {
        const val OUTPUT_TYPE_MIXED = 0
        const val OUTPUT_TYPE_VIDEO_ONLY = 1
        const val OUTPUT_TYPE_AUDIO_ONLY = 2

        private const val TAG = "MediaMuxerWrapper"
    }

    private val allTracksAdded: Boolean
        get() {
            return when (outputType) {
                OUTPUT_TYPE_MIXED -> {
                    audioTrack >= 0 && videoTrack >= 0
                }
                OUTPUT_TYPE_VIDEO_ONLY -> {
                    videoTrack >= 0
                }
                OUTPUT_TYPE_AUDIO_ONLY -> {
                    audioTrack >= 0
                }
                else -> {
                    throw IllegalArgumentException("Unknown output type")
                }
            }
        }

    var audioTrack: Int = -1
    var videoTrack: Int = -1

    private var muxer: MediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    var muxerStarted = false
        private set

    fun setVideoTrack(format: MediaFormat?) {
        format ?: return
        videoTrack = muxer.addTrack(format)
        Log.d(TAG, "video track added: $videoTrack")
        tryStart()
    }

    fun setAudioTrack(format: MediaFormat?) {
        format ?: return
        audioTrack = muxer.addTrack(format)
        Log.d(TAG, "audio track added: $audioTrack")
        tryStart()
    }

    fun writeVideoSampleData(byteBuf: ByteBuffer,
                             bufferInfo: MediaCodec.BufferInfo) {
        if (videoTrack < 0) {
            fail("video track not added")
        }
        Log.d(TAG, "writing video data: ${bufferInfo.size}")
        muxer.writeSampleData(videoTrack, byteBuf, bufferInfo)
    }

    fun writeAudioSampleData(byteBuf: ByteBuffer,
                             bufferInfo: MediaCodec.BufferInfo) {
        if (audioTrack < 0) {
            fail("audio track not added")
        }
        Log.d(TAG, "writing audio data: ${bufferInfo.size}")
        muxer.writeSampleData(audioTrack, byteBuf, bufferInfo)
    }

    fun stop() {
        muxer.stop()
        muxer.release()
    }

    private fun tryStart() {
        if (allTracksAdded && !muxerStarted) {
            Log.d(TAG, "===muxer started")
            muxer.start()
            muxerStarted = true
        }
    }

    private fun fail(s: String) {
        throw IllegalArgumentException(s)
    }
}