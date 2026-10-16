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
        val input = savedInput(this)
        if (input == null) showInputPicker() else fixAudio(input)
    }

    private fun fixAudio(input: String) {
        val label = message("Fixing audio…")
        setContentView(label)
        // the TV needs a moment to close the HDMI input before it is reopened (3 s worked over adb)
        Handler(Looper.getMainLooper()).postDelayed({
            if (WatcherService.waitingForAllow && !File(filesDir, WatcherService.ALLOWED_FILE).exists()) {
                // switching input would close the prompt, so stay here this once
                val fingerprint = WatcherService.keyFingerprint(this)?.let { "\n\nThe prompt must show this key:\n$it\nIf it doesn't match, press Deny." } ?: ""
                label.text = "First-time setup for automatic mode:\n\nOn the \"Allow USB debugging?\" prompt,\ntick Always allow, then press Allow.$fingerprint\n\nThen press Back."
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
            addView(message(if (inputs.isEmpty()) "No HDMI inputs found." else "Which input is your Blu-ray player on?"))
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

        /** Only ids that look like an HDMI input and that the TV actually has; they end up in a shell command. */
        private fun isValidInput(c: Context, id: String) =
            INPUT_ID.matches(id) && hdmiInputs(c).any { it.id == id }

        fun savedInput(c: Context): String? = prefs(c).getString("input", null)?.takeIf { isValidInput(c, it) }
        fun saveInput(c: Context, input: String) {
            if (prefs(c).getString("input", null) != input && isValidInput(c, input)) prefs(c).edit().putString("input", input).apply()
        }
    }
}
