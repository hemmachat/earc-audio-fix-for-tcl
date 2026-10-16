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

    /** Last TV input the TV opened; the caller checks it is an HDMI input before using it. */
    var lastInput: String? = null
        private set

    /** Feed one line of `logcat -v time,uid`. Returns true when the audio needs fixing now. */
    fun onLine(line: String): Boolean {
        val now = timeOf(line) ?: return false
        // judge an overdue format before this line can replace it
        val due = tick(now)
        // any app can write to the log; only trust lines written by system users
        if (!fromSystem(line)) return due
        TX_FORMAT.find(line)?.let {
            txChannels = it.groupValues[1].toIntOrNull() ?: return due
            txRateKHz = it.groupValues[2].toIntOrNull() ?: return due
        }
        TX_MODE.find(line)?.let { txBitstream = it.groupValues[1] == "NONPCM" }
        INPUT_FORMAT.find(line)?.let {
            val type = it.groupValues[1]
            // "audio/none" = no sound yet (player loading), nothing to check
            pending = if (type == "audio/none") null
            else Input(now, type != "audio/raw", it.groupValues[2].toIntOrNull() ?: return due, it.groupValues[3].toIntOrNull() ?: return due)
        }
        SESSION.find(line)?.let { lastInput = it.groupValues[1] }
        return due
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
        /** TV input id, e.g. ".../TvPassThroughService/HW16" (HDMI port) or ".../HDMI200004" (CEC device); no shell characters. */
        const val INPUT_ID = """[\w.]+/[\w.]+/\w+"""

        private val TIME = Regex("""^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d\d\d)""")
        private val TX_FORMAT = Regex("""TxOutputSetSequence.*ChCount = (\d+), Freq = (\d+)""")
        private val TX_MODE = Regex("""TxOutputSetSequence.*u8eARC_mode == SPDIF_OUT_(\w+)""")
        private val INPUT_FORMAT = Regex("""preCheck: Config update !!! outMediaType = ([^,]+), sampleRateInfo = (\d+), channelCountInfo = (\d+)""")
        private val SESSION = Regex("""createSessionInternalLocked\(inputId=($INPUT_ID)[,)]""")
        private val WRITER = Regex("""^\S+ \S+ \w/[^(]*\(\s*([\w-]+):\s*\d+\)""")
        private val APP_USER = Regex("""u\d+_[ai]\d+|shell""")

        /** Written by a system user (root, mediacodec, 1000…), not an app or the adb shell. */
        fun fromSystem(line: String): Boolean {
            val user = WRITER.find(line)?.groupValues?.get(1) ?: return false
            // system_server shows as "1000"; app uids start at 10000, the adb shell is 2000
            user.toIntOrNull()?.let { return it < 10_000 && it != 2000 }
            return !APP_USER.matches(user)
        }

        /** logcat filter matching every line the detector reads. */
        const val LOG_FILTER = "TxOutputSetSequence|preCheck: Config update|createSessionInternalLocked"

        // shortcut: ms of day, so a format change right at midnight is missed; fine for a TV
        fun timeOf(line: String): Long? = TIME.find(line)?.destructured?.let { (h, m, s, ms) ->
            ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong()
        }
    }
}
