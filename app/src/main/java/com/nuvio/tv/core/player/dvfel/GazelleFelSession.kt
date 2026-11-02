package com.nuvio.tv.core.player.dvfel

import android.util.Log
import java.nio.ByteBuffer

/**
 * Owns one libdvfelplay handle. Used only on the ExoPlayer playback thread (the renderer's
 * onQueueInputBuffer and the codec adapter's flush/release), as libdvfelplay requires.
 */
internal class GazelleFelSession {
    var handle = 0L
        private set
    val isOpen: Boolean get() = handle != 0L

    fun open(width: Int, height: Int, mode: Int, elType: Int): Boolean {
        check(!isOpen) { "session already open" }
        handle = DvFelPlay.open(width, height, mode)
        if (isOpen) {
            GazelleFelState.onOpened(DvFelPlay.mode(handle), elType)
            Log.i(TAG, "opened ${width}x$height mode=${DvFelPlay.mode(handle)} elType=$elType")
        }
        return isOpen
    }

    /** Filters [data] (position..limit) in place and commits [ptsUs]; returns the new length or < 0. */
    fun filterAndCommit(data: ByteBuffer, ptsUs: Long): Int {
        val n = DvFelPlay.filterInPlace(handle, data, data.position(), data.remaining())
        if (n >= 0) DvFelPlay.commit(handle, ptsUs)
        return n
    }

    /** After the MediaCodec flush (seek). Blocks up to debug.dvfp.seek_drain_ms on Gazelle; expected. */
    fun flush() {
        if (isOpen) DvFelPlay.flush(handle)
    }

    /** After the MediaCodec release: ends the kernel session. */
    fun close() {
        if (!isOpen) return
        snapshotStats()
        GazelleFelState.stats?.let { Log.i(TAG, "close stats=${it.joinToString(",")}") }
        DvFelPlay.close(handle)
        handle = 0L
        GazelleFelState.onClosed()
    }

    fun snapshotStats() {
        if (isOpen) GazelleFelState.stats = DvFelPlay.stats(handle)
    }

    private companion object {
        const val TAG = "DvFel"
    }
}
