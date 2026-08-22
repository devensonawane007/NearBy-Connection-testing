package com.example.samekanprivatetrekroom.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager as AndroidAudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.sqrt
import com.example.samekanprivatetrekroom.data.local.Logger
import com.example.samekanprivatetrekroom.data.local.PermissionManager

object MuLawCodec {
    private const val MAX = 32635

    fun encode(pcm: ShortArray): ByteArray {
        val mulaw = ByteArray(pcm.size)
        for (i in pcm.indices) {
            mulaw[i] = encodeSample(pcm[i])
        }
        return mulaw
    }

    fun decode(mulaw: ByteArray): ShortArray {
        val pcm = ShortArray(mulaw.size)
        for (i in mulaw.indices) {
            pcm[i] = decodeSample(mulaw[i])
        }
        return pcm
    }

    private fun encodeSample(sample: Short): Byte {
        var s = sample.toInt()
        val sign = if (s < 0) {
            s = -s
            0x80
        } else {
            0
        }
        if (s > MAX) s = MAX
        s += 132
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && (s and mask) == 0) {
            exponent--
            mask = mask shr 1
        }
        val mantissa = (s shr (exponent + 3)) and 0x0F
        return ((sign or (exponent shl 4) or mantissa) xor 0xFF).toByte()
    }

    private fun decodeSample(mulawByte: Byte): Short {
        val value = mulawByte.toInt() xor 0xFF
        val sign = value and 0x80
        val exponent = (value shr 4) and 0x07
        var mantissa = value and 0x0F
        var sample = (mantissa shl (exponent + 3)) + 132
        if (exponent > 0) {
            sample += (1 shl (exponent + 2))
        }
        if (sign != 0) {
            sample = -sample
        }
        return sample.toShort()
    }
}

class PTTManager(private val context: Context) {
    companion object {
        private const val TAG = "PTTManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val JITTER_BUFFER_MIN_SIZE = 3
        private const val JITTER_BUFFER_DELAY_MS = 150L
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false

    // Jitter Buffer variables
    data class AudioSegment(val seq: Long, val pcm: ShortArray) : Comparable<AudioSegment> {
        override fun compareTo(other: AudioSegment): Int = this.seq.compareTo(other.seq)
    }
    private val jitterBuffer = PriorityQueue<AudioSegment>()
    private val bufferLock = ReentrantLock()
    private var expectedSeq = -1L
    private var lastChunkSize = 1024

    private val scope = CoroutineScope(Dispatchers.Default)

    // UI state flows
    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow = _isRecordingFlow.asStateFlow()

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow = _isPlayingFlow.asStateFlow()

    private val _currentSpeaker = MutableStateFlow<String?>(null)
    val currentSpeaker = _currentSpeaker.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel = _audioLevel.asStateFlow()

    private var onChunkRecorded: ((ByteArray, Long) -> Unit)? = null
    private var localRecordSeq = 0L

    fun setOnChunkRecordedListener(listener: (ByteArray, Long) -> Unit) {
        onChunkRecorded = listener
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        val permissionManager = PermissionManager(context)
        if (!permissionManager.isPermissionGranted(android.Manifest.permission.RECORD_AUDIO)) {
            Logger.warn(TAG, "PTT recording requested but RECORD_AUDIO permission is not granted.")
            return
        }

        if (isPlaying) {
            Logger.warn(TAG, "Cannot start recording while incoming voice stream is playing.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Logger.error(TAG, "Invalid AudioRecord buffer size calculated.")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_IN,
                AUDIO_FORMAT,
                minBufferSize * BUFFER_SIZE_FACTOR
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.error(TAG, "AudioRecord initialization failed.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            _isRecordingFlow.value = true
            localRecordSeq = 0L
            Logger.info(TAG, "PTT recording started.")

            scope.launch(Dispatchers.IO) {
                val bufferSize = minBufferSize / 2
                val audioBuffer = ShortArray(bufferSize)

                while (isRecording) {
                    val readResult = audioRecord?.read(audioBuffer, 0, bufferSize) ?: -1
                    if (readResult > 0) {
                        val activeBuffer = audioBuffer.copyOfRange(0, readResult)
                        val rms = calculateRms(activeBuffer)
                        val maxRms = 32767f
                        _audioLevel.value = (rms / maxRms).coerceIn(0f, 1f)

                        val compressed = MuLawCodec.encode(activeBuffer)
                        onChunkRecorded?.invoke(compressed, localRecordSeq++)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Exception starting audio recording", e)
            stopRecording()
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        _isRecordingFlow.value = false
        _audioLevel.value = 0f
        Logger.info(TAG, "PTT recording stopping and destroying recorder.")
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Logger.error(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }

    fun playAudioChunk(speakerName: String, seq: Long, compressedData: ByteArray) {
        if (isRecording) {
            Logger.debug(TAG, "Ignoring incoming voice packet since we are recording.")
            return
        }

        _currentSpeaker.value = speakerName
        _isPlayingFlow.value = true

        val pcmData = MuLawCodec.decode(compressedData)
        lastChunkSize = pcmData.size

        bufferLock.lock()
        try {
            jitterBuffer.add(AudioSegment(seq, pcmData))
        } finally {
            bufferLock.unlock()
        }

        if (!isPlaying) {
            startPlaybackLoop()
        }
    }

    private fun startPlaybackLoop() {
        isPlaying = true
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)

        try {
            audioTrack = AudioTrack(
                AndroidAudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                CHANNEL_OUT,
                AUDIO_FORMAT,
                minBufferSize * BUFFER_SIZE_FACTOR,
                AudioTrack.MODE_STREAM
            )

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Logger.error(TAG, "AudioTrack initialization failed.")
                isPlaying = false
                _isPlayingFlow.value = false
                _currentSpeaker.value = null
                return
            }

            audioTrack?.play()
            Logger.info(TAG, "PTT voice playback stream started with Jitter Buffer.")

            scope.launch(Dispatchers.IO) {
                expectedSeq = 0L
                var idleAttempts = 0

                // Prefill Jitter Buffer to counter initial jitter
                while (true) {
                    var bufferSize: Int
                    bufferLock.lock()
                    try {
                        bufferSize = jitterBuffer.size
                    } finally {
                        bufferLock.unlock()
                    }
                    if (bufferSize >= JITTER_BUFFER_MIN_SIZE || idleAttempts > 15) {
                        break
                    }
                    delay(10)
                    idleAttempts++
                }

                idleAttempts = 0
                while (isPlaying) {
                    var segment: AudioSegment? = null
                    bufferLock.lock()
                    try {
                        if (jitterBuffer.isNotEmpty()) {
                            val top = jitterBuffer.peek()
                            if (top != null) {
                                if (top.seq == expectedSeq) {
                                    segment = jitterBuffer.poll()
                                } else if (top.seq < expectedSeq) {
                                    // Late packet, drop it
                                    jitterBuffer.poll()
                                }
                            }
                        }
                    } finally {
                        bufferLock.unlock()
                    }

                    if (segment != null) {
                        idleAttempts = 0
                        val rms = calculateRms(segment.pcm)
                        val maxRms = 32767f
                        _audioLevel.value = (rms / maxRms).coerceIn(0f, 1f)

                        audioTrack?.write(segment.pcm, 0, segment.pcm.size)
                        expectedSeq++
                    } else {
                        // Loss Concealment or Waiting
                        delay(20)
                        
                        var size: Int
                        bufferLock.lock()
                        try {
                            size = jitterBuffer.size
                        } finally {
                            bufferLock.unlock()
                        }

                        if (size > 0) {
                            // Gap detected in sequence numbers. Write silent frame to conceal.
                            Logger.warn(TAG, "Concealing loss for expected sequence: $expectedSeq")
                            val silenceFrame = ShortArray(lastChunkSize)
                            audioTrack?.write(silenceFrame, 0, silenceFrame.size)
                            _audioLevel.value = 0f
                            expectedSeq++
                        } else {
                            idleAttempts++
                            if (idleAttempts > 15) { // ~300ms of silence
                                break
                            }
                        }
                    }
                }
                stopPlayback()
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Exception starting voice playback stream", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        if (!isPlaying) return
        isPlaying = false
        _isPlayingFlow.value = false
        _currentSpeaker.value = null
        _audioLevel.value = 0f
        bufferLock.lock()
        try {
            jitterBuffer.clear()
        } finally {
            bufferLock.unlock()
        }
        expectedSeq = -1L
        Logger.info(TAG, "PTT voice playback stream stopped.")
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Logger.error(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    private fun calculateRms(pcm: ShortArray): Float {
        var sum = 0.0
        for (s in pcm) {
            sum += s * s
        }
        val avg = sum / pcm.size
        return sqrt(avg).toFloat()
    }
}
