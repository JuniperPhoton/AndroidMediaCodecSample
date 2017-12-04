package com.juniperphoton.androidmediacodecsample.core

import android.media.MediaCodec
import java.nio.ByteBuffer

class VolumnAudioEffect : ISingleAudioEffect {
    var volume: Float = 1f

    override fun processFrame(src: ByteBuffer, bi: MediaCodec.BufferInfo): ByteArray {
        val result = ByteArray(bi.size)
        for (i in bi.offset until bi.offset + bi.size) {
            result[i - bi.offset] = (src.get(i) * volume).toByte()
        }
        return result
    }
}