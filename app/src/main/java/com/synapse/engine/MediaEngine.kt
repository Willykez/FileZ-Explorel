package com.synapse.engine

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import java.io.FileDescriptor

class MediaEngine {
    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    var fftData = ByteArray(128)

    fun play(fd: FileDescriptor) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(fd)
            prepare()
            start()

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, samplingRate: Int) {
                        data?.let {
                            System.arraycopy(it, 0, fftData, 0, Math.min(it.size, fftData.size))
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        }
    }

    fun stop() {
        visualizer?.release()
        visualizer = null
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
