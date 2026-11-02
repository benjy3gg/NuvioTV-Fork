package com.nuvio.tv.core.player

import java.nio.ByteBuffer

/**
 * The HUD's Dolby Vision rows for a native FEL renderer (the conversion path records them in its extractor
 * hooks, which the native paths do not run). Used on the renderer's playback thread only.
 */
internal class DvRendererRpuStats {
    private val stats = DolbyVisionConversionStats.session
    private var scratch = ByteArray(1024)

    /** "DV HDR" row (MaxCLL/MaxFALL/MDL) and the EL type, from the RPU at [rpuOffset] of [sample]. */
    fun recordFirstRpu(sample: ByteArray, rpuOffset: Int, rpuSize: Int, elTypeCode: Int) {
        stats.recordElType(elTypeCode)
        stats.recordRpuMetadata(DoviBridge.getRpuStaticMetadata(sample, rpuOffset, rpuSize))
    }

    /**
     * HUD live rows: every ~0.5 s of media, the RPU of the sample being fed is summarised (L1 scene light,
     * L5 active area, CM version, levels) and stored by media time; the HUD shows the entry at the playback
     * position. The RPU is the last NAL of a P7 access unit, so it is searched backwards from the end (at most
     * [SCAN_MAX_BYTES]); only the RPU bytes are copied. [data] is Annex-B, position..limit.
     */
    fun sampleLiveDm(data: ByteBuffer, mediaTimeUs: Long) {
        if (!DoviBridge.isAvailable() || !stats.shouldSampleLiveDm(WRITER, mediaTimeUs)) return
        val start = data.position()
        val end = data.limit()
        val floor = maxOf(start, end - SCAN_MAX_BYTES)
        var nalBegin = -1
        var i = end - 5
        while (i >= floor) {
            if (data.get(i + 3).toInt() == 0x7C && data.get(i + 2).toInt() == 1 &&
                data.get(i + 1).toInt() == 0 && data.get(i).toInt() == 0
            ) {
                nalBegin = i + 3
                break
            }
            i--
        }
        if (nalBegin < 0) {
            stats.recordLiveDm(WRITER, mediaTimeUs, null)
            return
        }
        var nalEnd = end
        var j = nalBegin + 2
        while (j + 2 < end) {
            if (data.get(j).toInt() == 0 && data.get(j + 1).toInt() == 0 && data.get(j + 2).toInt() == 1) {
                nalEnd = j
                break
            }
            j++
        }
        while (nalEnd > nalBegin && data.get(nalEnd - 1).toInt() == 0) nalEnd-- // next start code's leading zero
        val length = nalEnd - nalBegin
        if (length <= 2 || length > 65_536) {
            stats.recordLiveDm(WRITER, mediaTimeUs, null)
            return
        }
        if (scratch.size < length) scratch = ByteArray(length)
        val view = data.duplicate()
        view.position(nalBegin)
        view.get(scratch, 0, length)
        stats.recordLiveDm(WRITER, mediaTimeUs, DoviBridge.getRpuDmInfo(scratch, 0, length))
    }

    private companion object {
        const val SCAN_MAX_BYTES = 256 * 1024
        const val WRITER = DolbyVisionConversionStats.LIVE_DM_WRITER_RENDERER
    }
}
