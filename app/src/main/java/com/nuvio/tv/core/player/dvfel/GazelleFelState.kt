package com.nuvio.tv.core.player.dvfel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide state of the Gazelle FEL session, written on the playback thread and read by the UI. */
internal object GazelleFelState {
    /** [DvFelPlay.MODE_FEL], [DvFelPlay.MODE_CONVERT], or -1 when no session is open. */
    @Volatile var mode: Int = -1
        private set
    @Volatile var elType: Int = DvFelPlay.EL_NONE
        private set
    /**
     * Last snapshot of the dvfp_stats counters: 0 composed, 1 mapped_only, 2 passthrough, 3 no_el_skipped,
     * 4 el_decoded, 5 el_inputs, 6 el_outputs, 7 el_overflow, 8 no_crc_match, 9 stale.
     */
    @Volatile var stats: LongArray? = null

    /**
     * The enhancement layer the player wants (the FEL panel's switch, set through [GazelleFelControl]).
     * The playback thread hands it to libdvfelplay; [elApplied] is what libdvfelplay has.
     */
    @Volatile var elRequested: Boolean = true
    @Volatile var elApplied: Boolean = true
        private set
    /** False when this libdvfelplay can't switch the EL (no dvfp_set_el). */
    @Volatile var elSwitchable: Boolean = true
        private set

    private val _running = MutableStateFlow(false)
    /** A FEL composition runs (the FEL button shows). */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val isFelRunning: Boolean get() = mode == DvFelPlay.MODE_FEL

    fun onOpened(mode: Int, elType: Int) {
        this.mode = mode
        this.elType = elType
        stats = null
        elApplied = true
        elSwitchable = true
        _running.value = mode == DvFelPlay.MODE_FEL
    }

    /** Playback thread, with an open handle: gives libdvfelplay a changed [elRequested]. */
    fun applyEl(handle: Long) {
        val want = elRequested
        if (want == elApplied || !elSwitchable) return
        if (DvFelPlay.setEl(handle, want)) elApplied = want else elSwitchable = false
    }

    fun onClosed() {
        mode = -1
        _running.value = false
        GazelleFelControl.onSessionClosed()
    }
}
