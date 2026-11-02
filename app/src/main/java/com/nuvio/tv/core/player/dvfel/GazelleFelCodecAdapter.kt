package com.nuvio.tv.core.player.dvfel

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter

/**
 * Hooks the base-layer codec's flush and release for libdvfelplay, in the order VLC uses:
 * dvfp_flush after the MediaCodec flush, dvfp_close after the codec is released.
 * (MediaCodecRenderer.resetCodecStateForFlush also runs on release, so the flush hook lives here.)
 */
@UnstableApi
internal class GazelleFelCodecAdapter(
    private val d: MediaCodecAdapter,
    private val s: GazelleFelSession,
) : MediaCodecAdapter by d {
    override fun flush() {
        d.flush()
        s.flush()
    }

    override fun release() {
        try {
            d.release()
        } finally {
            s.close()
        }
    }

    // Java default method: Kotlin delegation does not forward it, so delegate explicitly.
    override fun registerOnBufferAvailableListener(listener: MediaCodecAdapter.OnBufferAvailableListener): Boolean =
        d.registerOnBufferAvailableListener(listener)
}

@UnstableApi
internal class GazelleFelCodecAdapterFactory(
    private val d: MediaCodecAdapter.Factory,
    private val s: GazelleFelSession,
) : MediaCodecAdapter.Factory {
    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter =
        GazelleFelCodecAdapter(d.createAdapter(configuration), s)
}
