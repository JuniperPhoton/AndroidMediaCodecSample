package com.juniperphoton.androidmediacodecsample

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.IOException

@Suppress("deprecation")
class AudioEncoderCore(private val context: Context) {
    companion object {
        private const val INPUT_AUDIO_FILE_PATH = "/sdcard/audio_only.mp3"
        private const val TAG = "AudioEncoderCore"

        private const val AUDIO_MIME_TYPE = "audio/mp4a-latm"

        private const val TIMEOUT_USEC = 1L

        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128000
        private const val MAX_INPUT_SIZE = 16384

        const val RECORD_STATUS_INITED = 0
        const val RECORD_STATUS_STARTED = 1
        const val RECORD_STATUS_STOPPED = 2


        private fun createMediaFormat(): MediaFormat {
            val audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, SAMPLE_RATE, 2)
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO)
            return audioFormat
        }
    }

    var onStarted: (() -> Unit)? = null
    var onCompleted: (() -> Unit)? = null

    @Volatile
    private var requestStop = false

    @Volatile
    var recordStatus: Int = RECORD_STATUS_INITED

    private var handler = Handler(Looper.getMainLooper())

    private var audioExtractor: MediaExtractor = MediaExtractor()
    private lateinit var audioDecoder: MediaCodec
    private lateinit var audioEncoder: MediaCodec
    private lateinit var muxer: MediaMuxer

    init {
        try {
            val name = "${System.currentTimeMillis()}.mp3"
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val file = File(path, name)
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(e.message ?: "")
        }
    }

    fun stop() {
        requestStop = true
    }

    fun record() {
        var format: MediaFormat? = null
        var mimeType = ""
        var audioTrack = -1
        audioExtractor.setDataSource(context, Uri.parse(INPUT_AUDIO_FILE_PATH), null)
        val trackCount = audioExtractor.trackCount
        for (i in 0 until trackCount) {
            format = audioExtractor.getTrackFormat(i)
            mimeType = format.getString(MediaFormat.KEY_MIME)
            audioExtractor.selectTrack(i)
            audioTrack = i
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

            muxer.stop()
            muxer.release()
        } catch (e: Exception) {
            e.printStackTrace()
            toast(e.message ?: "")
        }

        Log.i(TAG, "completed!")
        toast("completed!")

        onCompleted?.invoke()
    }

    private fun generate() {
        var audioDecoderInputBuffers = audioDecoder.inputBuffers
        var audioDecoderOutputBuffers = audioDecoder.outputBuffers
        var audioEncoderInputBuffers = audioEncoder.inputBuffers
        var audioEncoderOutputBuffers = audioEncoder.outputBuffers

        var audioExtractorDone = false
        var audioDecoderDone = false
        var audioEncoderDone = false

        var encoderOutputAudioFormat: MediaFormat? = null
        var decoderOutputAudioFormat: MediaFormat? = null

        var muxing = false
        var audioExtractedFrameCount = 0
        var audioDecodedFrameCount = 0
        var audioEncodedFrameCount = 0
        var pendingAudioDecoderOutputBufferIndex = -1

        var outputAudioTrack = -1

        var audioDecoderOutputBufferInfo = MediaCodec.BufferInfo()
        var audioEncoderOutputBufferInfo = MediaCodec.BufferInfo()

        toast("start!")

        while (!audioEncoderDone && !requestStop) {
            // Extract audio from file and feed to decoder.
            // Do not extract audio if we have determined the output format but we are not yet
            // ready to mux the frames.
            while (!audioExtractorDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                Log.i(TAG, "[0] Extract audio from file and feed to decoder")
                val decoderInputBufferIndex = audioDecoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "no audio decoder input buffer")
                    break
                }
                Log.d(TAG, "audio decoder: returned input buffer:" + decoderInputBufferIndex)
                val decoderInputBuffer = audioDecoderInputBuffers[decoderInputBufferIndex]
                val size = audioExtractor.readSampleData(decoderInputBuffer, 0)
                val presentationTime = audioExtractor.sampleTime
                Log.d(TAG, "audio extractor: returned buffer of size" + size)
                Log.d(TAG, "audio extractor: returned buffer for time" + presentationTime)
                if (size >= 0) {
                    audioDecoder.queueInputBuffer(
                            decoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            audioExtractor.sampleFlags)
                }
                audioDecoderDone = !audioExtractor.advance()
                if (audioExtractorDone) {
                    Log.d(TAG, "audio extractor: EOS")
                    audioDecoder.queueInputBuffer(
                            decoderInputBufferIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                audioExtractedFrameCount++
                // We extracted a frame, let's try something else next.
                break
            }

            // Poll output frames from the audio decoder.
            // Do not poll if we already have a pending buffer to feed to the encoder.
            while (!audioDecoderDone && pendingAudioDecoderOutputBufferIndex == -1
                    && (encoderOutputAudioFormat == null || muxing)) {
                Log.i(TAG, "[1] Poll output frames from the audio decoder")
                val decoderOutputBufferIndex = audioDecoder.dequeueOutputBuffer(
                        audioDecoderOutputBufferInfo, TIMEOUT_USEC)
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "no audio decoder output buffer")
                    break
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "audio decoder: output buffers changed")
                    audioDecoderOutputBuffers = audioDecoder.outputBuffers
                    break
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputAudioFormat = audioDecoder.outputFormat
                    Log.d(TAG, "audio decoder: output format changed:" + decoderOutputAudioFormat)
                    break
                }
                Log.d(TAG, "audio decoder: returned output buffer:" + decoderOutputBufferIndex)
                Log.d(TAG, "audio decoder: returned buffer of size" + audioDecoderOutputBufferInfo.size)
                if (audioDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    Log.d(TAG, "audio decoder: codec config buffer")
                    audioDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false)
                    break
                }
                Log.d(TAG, "audio decoder: returned buffer for time" + audioDecoderOutputBufferInfo.presentationTimeUs)
                pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex
                Log.d(TAG, "audio decoder: output buffer is now pending:" + pendingAudioDecoderOutputBufferIndex)
                audioDecodedFrameCount++
                // We extracted a pending frame, let's try something else next.
                break
            }

            // Feed the pending decoded audio buffer to the audio encoder.
            while (pendingAudioDecoderOutputBufferIndex != -1) {
                Log.i(TAG, "[2] Feed the pending decoded audio buffer to the audio encoder.")
                Log.d(TAG, "audio decoder: attempting to process pending buffer:" + pendingAudioDecoderOutputBufferIndex)
                val encoderInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC)
                if (encoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "no audio encoder input buffer")
                    break
                }
                Log.d(TAG, "audio encoder: returned input buffer:" + encoderInputBufferIndex)
                val encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex]
                val size = audioDecoderOutputBufferInfo.size
                val presentationTime = audioDecoderOutputBufferInfo.presentationTimeUs
                Log.d(TAG, "audio decoder: processing pending buffer:" + pendingAudioDecoderOutputBufferIndex)
                Log.d(TAG, "audio decoder: pending buffer of size " + size)
                Log.d(TAG, "audio decoder: pending buffer for time " + presentationTime)
                if (size >= 0) {
                    Log.d(TAG, "audio encoder: queueInputBuffer, size: " + size)
                    val decoderOutputBuffer = audioDecoderOutputBuffers[pendingAudioDecoderOutputBufferIndex]
                            .duplicate()
                    decoderOutputBuffer.position(audioDecoderOutputBufferInfo.offset)
                    decoderOutputBuffer.limit(audioDecoderOutputBufferInfo.offset + size)
                    encoderInputBuffer.position(0)
                    encoderInputBuffer.put(decoderOutputBuffer)
                    audioEncoder.queueInputBuffer(
                            encoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            audioDecoderOutputBufferInfo.flags)
                }
                audioDecoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false)
                pendingAudioDecoderOutputBufferIndex = -1
                if (audioDecoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    Log.d(TAG, "audio decoder: EOS")
                    audioDecoderDone = true
                }
                // We enqueued a pending frame, let's try something else next.
                break
            }

            // Poll frames from the audio encoder and send them to the muxer.
            while (!audioEncoderDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                Log.i(TAG, "[3] Poll frames from the audio encoder and send them to the muxer")
                val encoderOutputBufferIndex = audioEncoder.dequeueOutputBuffer(
                        audioEncoderOutputBufferInfo, TIMEOUT_USEC)
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "audio encoder: no audio encoder output buffer")
                    break
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.d(TAG, "audio encoder: output buffers changed")
                    audioEncoderOutputBuffers = audioEncoder.outputBuffers
                    break
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "audio encoder: output format changed")
                    if (outputAudioTrack >= 0) {
                        fail("audio encoder changed its output format again?")
                    }
                    encoderOutputAudioFormat = audioEncoder.outputFormat
                    break
                }
                Log.d(TAG, "audio encoder: returned output buffer:" + encoderOutputBufferIndex)
                Log.d(TAG, "audio encoder: returned buffer of size" + audioEncoderOutputBufferInfo.size)

                if (!muxing) {
                    throw RuntimeException("should have muxing")
                }
                val encoderOutputBuffer = audioEncoderOutputBuffers[encoderOutputBufferIndex]
                if (audioEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    Log.d(TAG, "audio encoder: codec config buffer")
                    // Simply ignore codec config buffers.
                    audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                    break
                }
                Log.d(TAG, "audio encoder: returned buffer for time" + audioEncoderOutputBufferInfo.presentationTimeUs)
                if (audioEncoderOutputBufferInfo.size != 0) {
                    audioEncodedFrameCount++
                }
                muxer.writeSampleData(
                        outputAudioTrack, encoderOutputBuffer, audioEncoderOutputBufferInfo)
                if (audioEncoderOutputBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    Log.d(TAG, "audio encoder: EOS")
                    audioEncoderDone = true
                }
                audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
                // We enqueued an encoded frame, let's try something else next.
                break
            }

            if (!muxing && (encoderOutputAudioFormat != null)) {
                Log.i(TAG, "[4] muxer: adding audio track.")
                outputAudioTrack = muxer.addTrack(encoderOutputAudioFormat)
                Log.d(TAG, "muxer: starting")
                muxer.start()
                muxing = true
            }

            if (audioExtractor.sampleTime == -1L) {
                break
            }
        }

        Log.i(TAG, "ext count $audioExtractedFrameCount")
        Log.i(TAG, "decode count $audioDecodedFrameCount")
        Log.i(TAG, "encode count $audioEncodedFrameCount")
    }

    private fun fail(s: String) {
        toast(s)
        throw RuntimeException(s)
    }

    private fun toast(s: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, s, Toast.LENGTH_SHORT)
        } else {
            handler.post {
                Toast.makeText(context, s, Toast.LENGTH_LONG).show()
            }
        }
    }
}
