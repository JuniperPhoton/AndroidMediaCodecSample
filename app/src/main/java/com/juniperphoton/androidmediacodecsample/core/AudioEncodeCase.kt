package com.juniperphoton.androidmediacodecsample.core

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

@Suppress("deprecation")
class AudioEncodeCase(private val context: Context) : EncodeCase(context) {
    companion object {
        private const val INPUT_AUDIO_FILE_PATH = "/sdcard/audio_only.mp3"
        private const val TAG = "AudioEncodeCase"

        private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"

        private const val TIMEOUT_USEC = 1L

        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
        private const val MAX_INPUT_SIZE = 16384

        private fun createMediaFormat(): MediaFormat {
            val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, SAMPLE_RATE, 2)
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
            return audioFormat
        }
    }

    private var audioExtractor: MediaExtractor = MediaExtractor()
    private lateinit var audioDecoder: MediaCodec
    private lateinit var audioEncoder: MediaCodec

    private lateinit var mediaMuxerWrapper: MediaMuxerWrapper

    init {
        try {
            val name = "${System.currentTimeMillis()}.mp3"
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val file = File(path, name)
            mediaMuxerWrapper = MediaMuxerWrapper(MediaMuxerWrapper.OUTPUT_TYPE_AUDIO_ONLY, file.path)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(e.message ?: "")
        }
    }

    override fun stop() {
        requestStop = true
    }

    override fun start() {
        var format: MediaFormat? = null
        var mimeType = ""

        audioExtractor.setDataSource(context, Uri.parse(INPUT_AUDIO_FILE_PATH), null)

        val trackCount = audioExtractor.trackCount

        for (i in 0 until trackCount) {
            format = audioExtractor.getTrackFormat(i)
            mimeType = format.getString(MediaFormat.KEY_MIME)
            audioExtractor.selectTrack(i)
        }
        try {
            audioDecoder = MediaCodec.createDecoderByType(mimeType)
            audioDecoder.configure(format, null, null, 0)
            audioDecoder.start()

            audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
            audioEncoder.configure(createMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        onStarted?.invoke()

        generate()

        try {
            audioExtractor.release()

            audioDecoder.stop()
            audioDecoder.release()

            audioEncoder.stop()
            audioEncoder.release()

            mediaMuxerWrapper.stop()
        } catch (e: Exception) {
            e.printStackTrace()
            toast(e.message ?: "")
        }

        Log.i(TAG, "completed!")
        toast("completed!")

        onCompleted?.invoke()
    }

    private var encoderOutputAudioFormat: MediaFormat? = null
    private var audioEos = false
    private var pendingAudioDecoderOutputBufferIndex = -1

    private fun generate() {
        toast("start!")
        Log.i(TAG, "start!")
        while (!audioEos && !requestStop) {
            decode(audioExtractor, audioDecoder)
            encode(audioDecoder, audioEncoder)
            drainToMuxer(audioEncoder)
            if (audioExtractor.sampleTime == -1L) {
                break
            }
        }
    }

    private fun decode(extractor: MediaExtractor, decoder: MediaCodec) {
        // Extract audio from file and feed to decoder.
        // Do not extract audio if we have determined the output format but we are not yet
        // ready to mux the frames.
        var audioDecoderInputBuffers = decoder.inputBuffers
        while (!audioEos && (encoderOutputAudioFormat == null || mediaMuxerWrapper.muxerStarted)) {
            Log.i(TAG, "[0] Extract audio from file and feed to decoder")
            val decoderInputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "no audio decoder input buffer")
                break
            }
            Log.d(TAG, "audio decoder: returned input buffer:" + decoderInputBufferIndex)
            val decoderInputBuffer = audioDecoderInputBuffers[decoderInputBufferIndex]
            val size = extractor.readSampleData(decoderInputBuffer, 0)
            val presentationTime = extractor.sampleTime
            Log.d(TAG, "audio extractor: returned buffer of size" + size)
            if (size >= 0) {
                decoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        extractor.sampleFlags)
            }
            audioEos = !extractor.advance()
            if (audioEos) {
                Log.d(TAG, "audio extractor: EOS")
                decoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            // We extracted a frame, let's try something else next.
            break
        }
    }

    private fun encode(decoder: MediaCodec, encoder: MediaCodec) {
        // Poll output frames from the audio decoder.
        // Do not poll if we already have a pending buffer to feed to the encoder.
        var bufferInfo = MediaCodec.BufferInfo()
        var audioDecoderOutputBuffers = decoder.outputBuffers

        while (!audioEos && pendingAudioDecoderOutputBufferIndex == -1
                && (encoderOutputAudioFormat == null || mediaMuxerWrapper.muxerStarted)) {
            Log.i(TAG, "[1] Poll output frames from the audio decoder")
            val decoderOutputBufferIndex = decoder.dequeueOutputBuffer(
                    bufferInfo, TIMEOUT_USEC)
            if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "no audio decoder output buffer")
                break
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "audio decoder: output buffers changed")
                audioDecoderOutputBuffers = decoder.outputBuffers
                break
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "audio decoder: output format changed:${decoder.outputFormat}")
                break
            }
            Log.d(TAG, "audio decoder: returned output buffer:" + decoderOutputBufferIndex)
            Log.d(TAG, "audio decoder: returned buffer of size" + bufferInfo.size)
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                Log.d(TAG, "audio decoder: codec config buffer")
                decoder.releaseOutputBuffer(decoderOutputBufferIndex, false)
                break
            }
            pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex
            Log.d(TAG, "audio decoder: output buffer is now pending:" + pendingAudioDecoderOutputBufferIndex)
            // We extracted a pending frame, let's try something else next.
            break
        }

        var audioEncoderInputBuffers = encoder.inputBuffers

        // Feed the pending decoded audio buffer to the audio encoder.
        while (!audioEos && pendingAudioDecoderOutputBufferIndex != -1) {
            Log.i(TAG, "[2] Feed the pending decoded audio buffer to the audio encoder: " +
                    "$pendingAudioDecoderOutputBufferIndex")
            val encoderInputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
            if (encoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "no audio encoder input buffer")
                break
            }
            Log.d(TAG, "audio encoder: returned input buffer:" + encoderInputBufferIndex)
            val encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex]
            val size = bufferInfo.size
            val presentationTime = bufferInfo.presentationTimeUs
            Log.d(TAG, "audio decoder: processing pending buffer: $pendingAudioDecoderOutputBufferIndex, size is $size")
            if (size >= 0) {
                Log.d(TAG, "audio encoder: queueInputBuffer, size: $size")
                val decoderOutputBuffer = audioDecoderOutputBuffers[pendingAudioDecoderOutputBufferIndex]
                        .duplicate()
                decoderOutputBuffer.position(bufferInfo.offset)
                decoderOutputBuffer.limit(bufferInfo.offset + size)
                val result = handleBuffer(decoderOutputBuffer, bufferInfo)
                encoderInputBuffer.position(0)
                encoderInputBuffer.put(result)
                encoder.queueInputBuffer(
                        encoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        bufferInfo.flags)
            }
            decoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false)
            pendingAudioDecoderOutputBufferIndex = -1
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "audio decoder: EOS")
                audioEos = true
            }
            // We enqueued a pending frame, let's try something else next.
            break
        }
    }

    private fun handleBuffer(bf: ByteBuffer, bi: MediaCodec.BufferInfo): ByteArray {
        val result = ByteArray(bi.size)
        for (i in bi.offset until bi.offset + bi.size) {
            result[i - bi.offset] = (bf.get(i) * 1f).toByte()
        }
        return result
    }

    private fun drainToMuxer(encoder: MediaCodec) {
        // Poll frames from the audio encoder and send them to the muxer.
        var audioEncoderOutputBufferInfo = MediaCodec.BufferInfo()
        var audioEncoderOutputBuffers = encoder.outputBuffers

        while (!audioEos && (encoderOutputAudioFormat == null || mediaMuxerWrapper.muxerStarted)) {
            Log.i(TAG, "[3] Poll frames from the audio encoder and send them to the muxer")
            val encoderOutputBufferIndex = encoder.dequeueOutputBuffer(
                    audioEncoderOutputBufferInfo, TIMEOUT_USEC)
            if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "audio encoder: no audio encoder output buffer")
                break
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                Log.d(TAG, "audio encoder: output buffers changed")
                audioEncoderOutputBuffers = encoder.outputBuffers
                break
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "audio encoder: output format changed")
                encoderOutputAudioFormat = encoder.outputFormat
                mediaMuxerWrapper.setAudioTrack(encoderOutputAudioFormat!!)
                break
            }
            Log.d(TAG, "audio encoder: returned output buffer: $encoderOutputBufferIndex," +
                    " size is ${audioEncoderOutputBufferInfo.size}")
            if (!mediaMuxerWrapper.muxerStarted) {
                throw RuntimeException("should have muxing")
            }
            val encoderOutputBuffer = audioEncoderOutputBuffers[encoderOutputBufferIndex]
            if (audioEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                Log.d(TAG, "audio encoder: codec config buffer")
                // Simply ignore codec config buffers.
                encoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                break
            }
            mediaMuxerWrapper.writeAudioSampleData(encoderOutputBuffer, audioEncoderOutputBufferInfo)
            if (audioEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "audio encoder: EOS")
                audioEos = true
            }
            encoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
            // We enqueued an encoded frame, let's try something else next.
            break
        }
    }
}
