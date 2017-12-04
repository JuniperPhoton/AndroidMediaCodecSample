package com.juniperphoton.androidmediacodecsample.core

import android.media.MediaCodec
import java.nio.ByteBuffer

interface ISingleAudioEffect : IAudioEffect {
    fun processFrame(src: ByteBuffer, bi: MediaCodec.BufferInfo): ByteArray
}