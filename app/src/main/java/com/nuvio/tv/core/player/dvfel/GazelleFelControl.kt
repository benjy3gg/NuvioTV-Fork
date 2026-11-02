package com.nuvio.tv.core.player.dvfel

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The FEL panel's enhancement layer switch and its root reads (the dvfel kernel module's parameters and
 * debugfs counters). Root goes through one long-lived `su` shell, so Magisk asks once per app start.
 *
 * Base layer only = libdvfelplay stops handing EL pictures and base layer hints to the kernel, plus
 * dvfel's coherent_bl_fallback, which resets the Dolby composer to base layer mode for frames without
 * an EL (without it the picture turns green). coherent_bl_fallback stays off while the EL is on: there
 * it rewrites the composer every frame and the picture flickers.
 */
internal object GazelleFelControl {
    private const val TAG = "DvFelControl"
    private const val PARAMS = "/sys/module/dvfel/parameters"
    private const val DEBUGFS_STATS = "/sys/kernel/debug/dvfel/stats"

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "dvfel-control").apply { isDaemon = true } }
    @Volatile private var fallbackSet = false
    @Volatile var lastError: String? = null
        private set

    /** Switches the enhancement layer; returns at once. */
    fun setEl(on: Boolean) {
        worker.execute {
            if (on) {
                GazelleFelState.elRequested = true
                // let the EL pictures reach the kernel before the per-frame reset stops
                Thread.sleep(150)
                writeFallback(false)
            } else {
                writeFallback(true)
                GazelleFelState.elRequested = false
            }
        }
    }

    /** The session ended: the next one starts with the EL on and the kernel as configured. */
    fun onSessionClosed() {
        GazelleFelState.elRequested = true
        if (fallbackSet) worker.execute { writeFallback(false) }
    }

    /** dvfel's debugfs counters as "<line's first word>.<key>" -> value (bl.paired, el.dropped, ...); blocking. */
    fun readKernelStats(): Map<String, Long>? {
        val text = RootShell.run("cat $DEBUGFS_STATS") ?: return null
        val out = HashMap<String, Long>()
        for (line in text.lineSequence()) {
            val t = line.trim().split(Regex("\\s+"))
            if (t.size < 3) continue
            var i = 1
            while (i + 1 < t.size) {
                t[i + 1].toLongOrNull()?.let { out["${t[0]}.${t[i]}"] = it }
                i += 2
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun writeFallback(on: Boolean) {
        val v = if (on) 1 else 0
        if (RootShell.run("echo $v > $PARAMS/coherent_bl_fallback && cat $PARAMS/coherent_bl_fallback") == null) {
            lastError = "no root"
            Log.w(TAG, "coherent_bl_fallback=$v failed")
            return
        }
        lastError = null
        fallbackSet = on
        Log.i(TAG, "coherent_bl_fallback=$v")
    }

    /** One `su` shell for every root command; a command waits at most a few seconds. */
    private object RootShell {
        private const val END = "__nuvio_dvfel_end__"
        private var process: Process? = null
        private var stdin: OutputStreamWriter? = null
        private var stdout: BufferedReader? = null
        private val reader = Executors.newSingleThreadExecutor { r -> Thread(r, "dvfel-su").apply { isDaemon = true } }

        @Synchronized fun run(cmd: String): String? {
            if (!ensure()) return null
            return try {
                stdin!!.apply { write("$cmd 2>/dev/null; echo $END\n"); flush() }
                val result = reader.submit<String?> {
                    val sb = StringBuilder()
                    while (true) {
                        val line = stdout!!.readLine() ?: return@submit null
                        if (line == END) break
                        sb.append(line).append('\n')
                    }
                    sb.toString()
                }.get(4, TimeUnit.SECONDS)
                result ?: run { close(); null }
            } catch (e: Exception) {
                Log.w(TAG, "su: ${e.message}")
                close()
                null
            }
        }

        private fun ensure(): Boolean {
            // Process.isAlive needs API 26 (minSdk 24)
            process?.let { p -> if (runCatching { p.exitValue() }.isFailure) return true }
            close()
            return try {
                val p = ProcessBuilder("su").redirectErrorStream(true).start()
                process = p
                stdin = OutputStreamWriter(p.outputStream)
                stdout = BufferedReader(InputStreamReader(p.inputStream))
                true
            } catch (e: Exception) {
                Log.w(TAG, "no su: ${e.message}")
                close()
                false
            }
        }

        private fun close() {
            runCatching { process?.destroy() }
            process = null; stdin = null; stdout = null
        }
    }
}
