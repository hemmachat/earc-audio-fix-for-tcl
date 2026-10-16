package com.hemma.fixaudio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** One-click fix: opening the app leaves the HDMI input, then returns to it, so the TV sets up eARC again. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, WatcherService::class.java))
        val input = savedInput(this) ?: onlyCecPort(this)?.let { saveInput(this, it); savedInput(this) }
        if (input == null) showInputPicker() else fixAudio(input)
    }

    private fun fixAudio(input: String) {
        val label = message("Fixing the sound…\n\nThe picture will come back in a few seconds.")
        setContentView(label)
        // the TV needs a moment to close the HDMI input before it is reopened (3 s worked over adb)
        Handler(Looper.getMainLooper()).postDelayed({
            if (WatcherService.waitingForAllow && !File(filesDir, WatcherService.ALLOWED_FILE).exists()) {
                // switching input would close the prompt, so stay here this once
                val key = WatcherService.keyFingerprint(this)?.let { "1. Check the key on the TV matches this one:\n$it\n   If it doesn't match, select Cancel.\n" } ?: ""
                label.text = "One-time setup for automatic mode\n\n" +
                    "The TV is asking \"Allow USB debugging?\".\nThat's Fix Audio asking to watch for silent films.\n\n" +
                    key +
                    "2. Tick \"Always allow from this computer\".\n" +
                    "3. Select Allow.\n" +
                    "4. Press Back on your remote."
                label.textSize = 22f
                return@postDelayed
            }
            startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input)))
            finish()
        }, 3000)
    }

    private fun showInputPicker() {
        val inputs = hdmiInputs(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(message(if (inputs.isEmpty()) "Fix Audio couldn't find any HDMI inputs on this TV." else "Which HDMI input is your Blu-ray player plugged into?\nYou only need to choose this once."))
        }
        for (info in inputs) {
            list.addView(Button(this).apply {
                text = info.loadLabel(this@MainActivity)
                setOnClickListener {
                    saveInput(this@MainActivity, info.id)
                    fixAudio(info.id)
                }
            })
        }
        setContentView(list)
    }

    private fun message(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        gravity = Gravity.CENTER
    }

    companion object {
        private fun prefs(c: Context) = c.getSharedPreferences("fixaudio", Context.MODE_PRIVATE)
        private val INPUT_ID = Regex(Detector.INPUT_ID)

        fun hdmiInputs(c: Context): List<TvInputInfo> = c.getSystemService(TvInputManager::class.java).tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.isPassthroughInput && it.parentId == null }

        /** The HDMI port of the only HDMI-CEC device (the player); null if there are none or several. The TV makes no CEC input for an eARC soundbar. */
        private fun onlyCecPort(c: Context): String? = c.getSystemService(TvInputManager::class.java).tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId != null }
            .mapNotNull { it.parentId }.distinct().singleOrNull()

        /** Only safe-looking ids the TV lists as HDMI (a port, or a CEC device on a port); they end up in a shell command. */
        private fun isValidInput(c: Context, id: String) = INPUT_ID.matches(id) &&
            c.getSystemService(TvInputManager::class.java).tvInputList.any { it.id == id && it.type == TvInputInfo.TYPE_HDMI }

        fun savedInput(c: Context): String? = prefs(c).getString("input", null)?.takeIf { isValidInput(c, it) }
        fun saveInput(c: Context, input: String) {
            if (prefs(c).getString("input", null) != input && isValidInput(c, input)) prefs(c).edit().putString("input", input).apply()
        }
    }
}
