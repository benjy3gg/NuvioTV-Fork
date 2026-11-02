package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.player.dvfel.DvFelPlay
import com.nuvio.tv.core.player.dvfel.GazelleFelControl
import com.nuvio.tv.core.player.dvfel.GazelleFelState
import com.nuvio.tv.core.player.dvfel.GazelleFelSupport
import java.util.Locale

/**
 * The stats overlay's FEL entry on the Fire TV Cube 3 (under DV, in Video): the enhancement layer state,
 * how many base layer frames got their EL over the last second, libdvfelplay's EL decoder counters, the
 * dvfel kernel counters (root) and the versions. One "FEL" label, the further lines unlabelled.
 */
internal fun gazelleFelStatsRows(): List<StatsRow> {
    val rows = ArrayList<StatsRow>(6)
    fun line(value: String, dot: StatsDot = StatsDot.NONE) {
        rows += if (rows.isEmpty()) StatsRow("FEL", value, dot, marquee = value.length > 44)
        else StatsRow("", value, dot, marquee = value.length > 44, group = StatsGroup.VIDEO)
    }
    if (!GazelleFelState.isFelRunning) {
        line("not running", StatsDot.BAD)
        return rows
    }
    val elOn = GazelleFelState.elApplied && GazelleFelState.elRequested
    val (now, before) = GazelleFelControl.kernelSamples()
    val k = now?.values
    val composer = when (k?.get("fixes.el")) {
        1L -> " · composed" + if (k["fixes.half"] == 1L) " (half-res EL)" else ""
        0L -> " · base layer composer"
        else -> ""
    }
    line(if (elOn) "EL on · RPU + BL + EL$composer" else "EL off · RPU + BL$composer",
        if (elOn) StatsDot.GOOD else StatsDot.NONE)

    // share of base layer frames paired with their EL, from the kernel counters' last change
    if (now != null && before != null) {
        fun delta(key: String): Long? {
            val a = now.values[key] ?: return null
            val b = before.values[key] ?: return null
            return (a - b).coerceAtLeast(0L)
        }
        val paired = delta("bl.paired")
        val noEl = delta("bl.no_el")
        val noHint = delta("bl.no_hint")
        val seconds = (now.atMs - before.atMs) / 1000.0
        if (paired != null && noEl != null && noHint != null && seconds > 0.2) {
            val frames = paired + noEl + noHint
            if (frames > 0) {
                val pct = paired * 100.0 / frames
                line(String.format(Locale.ROOT, "EL paired %.0f%% · %.2f fps", pct, frames / seconds), when {
                    !elOn -> StatsDot.NONE
                    pct >= 95.0 -> StatsDot.GOOD
                    pct >= 50.0 -> StatsDot.WARN
                    else -> StatsDot.BAD
                })
            }
        }
    }
    PlaybackStatsDv.gazelleElRows(GazelleFelState.stats)?.let { (decoded, drops) ->
        line("EL $decoded")
        line("EL drops: $drops")
    }
    if (k != null) {
        line("kernel BL paired ${k["bl.paired"] ?: 0} · no EL ${k["bl.no_el"] ?: 0} · no hint ${k["bl.no_hint"] ?: 0}")
        line("kernel EL dropped ${k["el.dropped"] ?: 0} · wait timeouts ${k["wait.timeouts"] ?: 0}")
    } else {
        line("kernel counters need root")
    }
    line("dvfel ${GazelleFelSupport.moduleVersion() ?: "?"} · lib ${DvFelPlay.libVersion().ifEmpty { "?" }}")
    return rows
}
