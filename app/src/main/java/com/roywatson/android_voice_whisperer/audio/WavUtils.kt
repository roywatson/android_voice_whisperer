package com.roywatson.android_voice_whisperer.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

object WavUtils {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val HEADER_SIZE = 44

    /**
     * Writes a standard 44-byte RIFF/WAVE header for 16-bit mono 16kHz PCM.
     */
    fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val header = ByteArray(HEADER_SIZE)

        // RIFF chunk descriptor
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        // WAVE format
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // 'fmt ' subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat: 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // BlockAlign
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // 'data' subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, HEADER_SIZE)
    }

    /**
     * Updates an existing WAV file's total size fields after recording stops.
     */
    fun updateWavHeaderSizes(file: File) {
        val totalAudioLen = file.length() - HEADER_SIZE
        val totalDataLen = totalAudioLen + 36

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(
                byteArrayOf(
                    (totalDataLen and 0xff).toByte(),
                    ((totalDataLen shr 8) and 0xff).toByte(),
                    ((totalDataLen shr 16) and 0xff).toByte(),
                    ((totalDataLen shr 24) and 0xff).toByte()
                )
            )

            raf.seek(40)
            raf.write(
                byteArrayOf(
                    (totalAudioLen and 0xff).toByte(),
                    ((totalAudioLen shr 8) and 0xff).toByte(),
                    ((totalAudioLen shr 16) and 0xff).toByte(),
                    ((totalAudioLen shr 24) and 0xff).toByte()
                )
            )
        }
    }

    /**
     * Reads a 16-bit mono 16kHz WAV file and returns normalized [-1.0f, 1.0f] float samples.
     */
    fun readWavToFloatArray(file: File): FloatArray {
        val pcm = readWavToShortArray(file)
        val floats = FloatArray(pcm.size)
        for (i in pcm.indices) {
            floats[i] = pcm[i] / 32768.0f
        }
        return floats
    }

    /**
     * Reads 16-bit PCM shorts from a WAV file, skipping the 44-byte header.
     * Uses direct little-endian bit shifting for zero-allocation performance.
     */
    fun readWavToShortArray(file: File): ShortArray {
        val totalBytes = file.length() - HEADER_SIZE
        if (totalBytes <= 0) return ShortArray(0)

        val shortsCount = (totalBytes / 2).toInt()
        val shorts = ShortArray(shortsCount)

        FileInputStream(file).use { input ->
            input.skip(HEADER_SIZE.toLong())
            val buffer = ByteArray(8192)
            var shortIdx = 0

            var bytesRead = input.read(buffer)
            while (bytesRead != -1 && shortIdx < shortsCount) {
                var i = 0
                while (i + 1 < bytesRead && shortIdx < shortsCount) {
                    val low = buffer[i].toInt() and 0xFF
                    val high = buffer[i + 1].toInt()
                    shorts[shortIdx++] = ((high shl 8) or low).toShort()
                    i += 2
                }
                bytesRead = input.read(buffer)
            }
        }
        return shorts
    }
}
