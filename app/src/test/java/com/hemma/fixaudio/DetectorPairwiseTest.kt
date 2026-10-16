package com.hemma.fixaudio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pairwise cases for the fix decision, generated from app/src/test/pict/detector.pict with
 * `pict detector.pict /r:1`. Expected results come from the spec, not from running the code:
 * fix = input line from a system writer, format isn't "none", judged at >= 2.5 s, and the eARC output
 * (last trusted TxOutputSetSequence, or nothing) doesn't match the format.
 * NotSeen = no eARC line yet (watcher started mid-film): treated as not matching, so it fixes.
 */
class DetectorPairwiseTest {
    private val cases = listOf(
        // TxMode TxRateKHz TxChannels TxWriter InputType InputRate InputCh InputWriter ElapsedMs JudgedBy Expected
        "NONPCM 48 2 root dts 192000 8 1000 2499 Tick none",
        "PCM 192 8 u0_a123 mlp 48000 2 mediacodec 2500 NextLine fix",
        "PCM 192 2 root ac3 48000 8 2000 6000 NextLine none",
        "NONPCM 48 8 u0_a123 mlp 192000 2 u0_a123 6000 Tick none",
        "PCM 48 8 root dts 48000 8 u0_a123 2500 NextLine none",
        "PCM 192 2 root mlp 48000 2 root 2499 Tick none",
        "NotSeen 48 2 root raw 192000 8 mediacodec 2500 Tick fix",
        "NONPCM 192 2 u0_a123 none 192000 8 mediacodec 2499 NextLine none",
        "PCM 192 8 u0_a123 dts 192000 2 root 6000 NextLine fix",
        "NONPCM 48 8 u0_a123 mlp 192000 2 2000 2499 Tick none",
        "NONPCM 192 8 u0_a123 raw 48000 2 u0_a123 2499 NextLine none",
        "NONPCM 48 8 u0_a123 ac3 192000 2 u0_a123 2500 Tick none",
        "PCM 192 8 u0_a123 ac3 48000 2 1000 6000 NextLine fix",
        "NONPCM 48 8 root ac3 48000 8 root 2499 NextLine none",
        "NONPCM 192 2 root mlp 48000 8 10123 2499 NextLine none",
        "NotSeen 48 2 root dts 48000 2 2000 2500 NextLine none",
        "NotSeen 48 2 root mlp 48000 2 shell 6000 Tick none",
        "NONPCM 192 8 u0_a123 ac3 192000 8 shell 2499 NextLine none",
        "PCM 48 8 root none 48000 2 shell 2500 Tick none",
        "PCM 48 8 root dts 192000 8 shell 2499 Tick none",
        "NONPCM 48 2 u0_a123 dts 192000 8 mediacodec 6000 NextLine fix",
        "PCM 48 8 u0_a123 ac3 192000 2 10123 6000 Tick none",
        "NONPCM 48 8 root none 48000 2 10123 6000 Tick none",
        "NotSeen 48 2 root none 48000 8 2000 2499 Tick none",
        "PCM 48 2 u0_a123 raw 192000 8 2000 6000 NextLine none",
        "NONPCM 48 8 u0_a123 none 48000 2 root 2500 NextLine none",
        "NotSeen 48 2 root raw 192000 2 10123 2500 Tick none",
        "PCM 48 8 u0_a123 raw 48000 2 1000 2500 NextLine fix",
        "NONPCM 192 2 root raw 48000 2 root 2500 NextLine fix",
        "NONPCM 48 8 root none 48000 2 1000 2500 Tick none",
        "NONPCM 192 8 root dts 192000 2 10123 6000 NextLine none",
        "NONPCM 192 2 root raw 48000 2 shell 6000 NextLine none",
        "NONPCM 48 8 root mlp 48000 8 1000 2500 NextLine none",
        "NotSeen 48 2 root ac3 48000 2 1000 2499 Tick none",
        "NotSeen 48 2 root mlp 192000 8 root 2500 Tick fix",
        "NotSeen 48 2 root ac3 48000 8 mediacodec 2499 Tick none",
        "PCM 192 2 u0_a123 none 192000 2 u0_a123 6000 NextLine none",
        "NotSeen 48 2 root raw 48000 8 u0_a123 2500 NextLine none",
    )

    private val types = mapOf("raw" to "audio/raw", "mlp" to "audio/vnd.dolby.mlp", "dts" to "audio/vnd.dts", "ac3" to "audio/ac3", "none" to "audio/none")

    private fun stamp(ms: Long) = "10-16 %02d:%02d:%02d.%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)

    @Test
    fun pairwiseFixDecisions() {
        val failures = cases.filter { case ->
            val (txMode, txKHz, txCh, txWriter, type, rate, ch, writer, elapsed, judgedBy, expected) = case.split(" ")
            val d = Detector()
            val txAt = Detector.timeOf("10-16 21:10:00.000")!!
            if (txMode != "NotSeen") {
                d.onLine("${stamp(txAt)} I/        ( $txWriter:  369): [HAL_AUDIO_eARC_TxOutputSetSequence][9647]DIGITAL_TX eARC ATOP ChCount = $txCh, Freq = $txKHz")
                d.onLine("${stamp(txAt)} I/        ( $txWriter:  369): [HAL_AUDIO_eARC_TxOutputSetSequence][9758]u8eARC_mode == SPDIF_OUT_$txMode")
            }
            val inAt = txAt + 1000
            d.onLine("${stamp(inAt)} I/C2MtkHdmiCap($writer:  469): preCheck: Config update !!! outMediaType = ${types[type]}, sampleRateInfo = $rate, channelCountInfo = $ch")
            val at = inAt + elapsed.toLong()
            val fixed = if (judgedBy == "Tick") d.tick(at) else d.onLine("${stamp(at)} I/Unrelated( 1000:  542): nothing to see")
            fixed != (expected == "fix")
        }
        assertEquals("cases with the wrong result", emptyList<String>(), failures)
    }

    private operator fun <T> List<T>.component6() = this[5]
    private operator fun <T> List<T>.component7() = this[6]
    private operator fun <T> List<T>.component8() = this[7]
    private operator fun <T> List<T>.component9() = this[8]
    private operator fun <T> List<T>.component10() = this[9]
    private operator fun <T> List<T>.component11() = this[10]
}
