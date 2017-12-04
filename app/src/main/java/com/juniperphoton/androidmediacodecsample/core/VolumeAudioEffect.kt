package com.juniperphoton.androidmediacodecsample.core

import android.media.MediaCodec
import android.util.Log
import java.nio.ByteBuffer

class VolumeAudioEffect : ISingleAudioEffect {
    @Volatile
    var volume: Float = 1f

    override fun processFrame(src: ByteBuffer, bi: MediaCodec.BufferInfo): ByteArray {
        val result = ByteArray(bi.size)
        for (i in bi.offset until bi.offset + bi.size) {
            result[i - bi.offset] = (src.get(i) * volume).toByte()
        }
        Log.d("VolumeAudioEffect", "" + volume)
        return result
    }
}