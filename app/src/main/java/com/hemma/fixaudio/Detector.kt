package com.hemma.fixaudio

/**
 * Spots the TCL C7K bug: the HDMI input switches to a new audio format (e.g. TrueHD)
 * but the eARC output is never set up for it, so the soundbar gets silence.
 * Fed with TV log lines; times are milliseconds of the day (TV local time).
 */
class Detector(
    private val graceMs: Long = 2500,
    private val cooldownMs: Long = 20_000,
    private val maxFixesPer5Min: Int = 2,
) {
    private data class Input(val atMs: Long, val bitstream: Boolean, val rate: Int, val channels: Int)

    private var txChannels = 0
    private var txRateKHz = 0
    private var txBitstream = false
    private var pending: Input? = null
    private val fixTimes = ArrayDeque<Long>()

    /** Last HDMI input the TV opened, e.g. "com.tcl.tvinput/.TvPassThroughService/HDMI200004". */
    var lastInput: String? = null
        private set

    /** Returns true when the audio needs fixing now. */
    fun onLine(line: String): Boolean {
        val now = timeOf(line) ?: return false
        TX_FORMAT.find(line)?.let { txChannels = it.groupValues[1].toInt(); txRateKHz = it.groupValues[2].toInt() }
        TX_MODE.find(line)?.let { txBitstream = it.groupValues[1] == "NONPCM" }
        INPUT_FORMAT.find(line)?.let {
            val type = it.groupValues[1]
            // "audio/none" = no sound yet (player loading), nothing to check
            pending = if (type == "audio/none") null
            else Input(now, type != "audio/raw", it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        SESSION.find(line)?.let { lastInput = it.groupValues[1] }
        return tick(now)
    }

    /** Call regularly (about once a second) so a missing eARC setup is noticed even if the log goes quiet. */
    fun tick(nowMs: Long): Boolean {
        val p = pending ?: return false
        if (nowMs - p.atMs < graceMs) return false
        pending = null
        if (outputMatches(p)) return false
        while (fixTimes.isNotEmpty() && nowMs - fixTimes.first() > 5 * 60_000) fixTimes.removeFirst()
        if (fixTimes.isNotEmpty() && nowMs - fixTimes.last() < cooldownMs) return false
        if (fixTimes.size >= maxFixesPer5Min) return false // fixing isn't helping; stop flipping inputs
        fixTimes.addLast(nowMs)
        return true
    }

    private fun outputMatches(p: Input): Boolean =
        if (p.bitstream) txBitstream && txRateKHz * 1000 >= p.rate // TrueHD/DTS-HD need 192kHz
        // shortcut: PCM is resampled by the TV, so only the channel count is checked; revisit if 7.1 PCM stays silent
        else !txBitstream && txChannels >= p.channels

    companion object {
        private val TIME = Regex("""^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d\d\d)""")
        private val TX_FORMAT = Regex("""TxOutputSetSequence.*ChCount = (\d+), Freq = (\d+)""")
        private val TX_MODE = Regex("""TxOutputSetSequence.*u8eARC_mode == SPDIF_OUT_(\w+)""")
        private val INPUT_FORMAT = Regex("""preCheck: Config update !!! outMediaType = ([^,]+), sampleRateInfo = (\d+), channelCountInfo = (\d+)""")
        private val SESSION = Regex("""createSessionInternalLocked\(inputId=([^,]*HDMI\w*)""")

        /** logcat filter matching every line the detector reads. */
        const val LOG_FILTER = "TxOutputSetSequence|preCheck: Config update|createSessionInternalLocked"

        // shortcut: ms of day, so a format change right at midnight is missed; fine for a TV
        fun timeOf(line: String): Long? = TIME.find(line)?.destructured?.let { (h, m, s, ms) ->
            ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong()
        }
    }
}
