package com.hemma.fixaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Automatic mode: watches the TV's log through its own adb port (Network debugging must stay on)
 * and does the Home → reselect-input fix when the eARC output isn't set up for a new audio format.
 */
class WatcherService : Service() {
    private val detector = Detector()
    private val ticker = Executors.newSingleThreadScheduledExecutor()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watch", "Audio watcher", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "watch")
            .setContentTitle("Watching for silent eARC audio")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .build())
        // one watcher per process, even if Android recreates the service
        if (!watching) {
            watching = true
            thread(name = "watch") { watchLoop() }
            ticker.scheduleAtFixedRate({
                // an uncaught exception would silently cancel all future ticks
                try {
                    val now = LocalTime.now().toNanoOfDay() / 1_000_000
                    if (synchronized(detector) { detector.tick(now) }) fix()
                } catch (e: Throwable) {
                    Log.e(TAG, "tick failed", e)
                }
            }, 1, 1, TimeUnit.SECONDS)
        }
        return START_STICKY
    }

    private fun watchLoop() {
        while (watching) {
            try {
                val key = keyPair()
                // first run shows the TV's "Allow USB debugging?" prompt; give up after 60 s so a missed prompt is asked again
                waitingForAllow = true
                Dadb.create("127.0.0.1", 5555, key, 2000, 60_000).use {
                    it.shell("echo ok")
                    // TCL blocks apps from starting on boot/update unless they have its AUTO_START app op;
                    // grant it to ourselves only (fails harmlessly on TVs without this op)
                    it.shell("appops set $packageName AUTO_START allow")
                    // after boot Android only lets battery-optimisation-exempt apps start a foreground service
                    // from the background (no battery on a TV, so this costs nothing)
                    it.shell("cmd deviceidle whitelist +$packageName")
                }
                waitingForAllow = false
                File(filesDir, ALLOWED_FILE).createNewFile()
                Dadb.create("127.0.0.1", 5555, key).use { dadb ->
                    connection = dadb
                    Log.i(TAG, "connected to TV adb, key ${keyFingerprint(this)}")
                    // -T 1: only new lines, so old log entries never trigger a fix; uid shows who wrote each line
                    dadb.open("shell:logcat -b all -v time,uid -T 1 -e '${Detector.LOG_FILTER}'").use { stream ->
                        while (true) {
                            val line = stream.source.readUtf8Line() ?: break
                            val (needsFix, input) = synchronized(detector) { detector.onLine(line) to detector.lastInput }
                            input?.let { MainActivity.saveInput(this, it) }
                            if (needsFix) fix()
                        }
                    }
                }
            } catch (e: Exception) {
                // adb off, not yet allowed on the TV, or the TV went to standby: retry
                Log.w(TAG, "adb connection lost: $e")
            }
            connection = null
            waitingForAllow = false
            if (watching) Thread.sleep(5000)
        }
    }

    private fun fix() {
        val input = MainActivity.savedInput(this)
        Log.i(TAG, "eARC output not set up for new audio format: reselecting $input")
        if (input == null) return
        // own connection with a 15 s timeout: a reply that never comes can't block later fixes
        thread(name = "fix") {
            try {
                Dadb.create("127.0.0.1", 5555, keyPair(), 2000, 15_000).use {
                    it.shell("input keyevent KEYCODE_HOME; sleep 3; am start -a android.intent.action.VIEW -d '${TvContract.buildChannelUriForPassthroughInput(input)}'")
                }
                Log.i(TAG, "fix done")
            } catch (e: Exception) {
                Log.w(TAG, "fix failed: $e")
            }
        }
    }

    private fun keyPair(): AdbKeyPair {
        val priv = File(filesDir, "adbkey")
        val pub = File(filesDir, "adbkey.pub")
        if (!priv.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    override fun onDestroy() {
        watching = false
        ticker.shutdownNow()
        // unblocks the log read so the watch thread ends
        connection?.close()
    }

    companion object {
        const val TAG = "FixAudio"
        const val ALLOWED_FILE = "adb-allowed"
        @Volatile var waitingForAllow = false
        @Volatile private var watching = false
        @Volatile private var connection: Dadb? = null

        /** Same format the TV shows in its "Allow USB debugging?" prompt: MD5 of the public key, AA:BB:… */
        fun keyFingerprint(c: android.content.Context): String? = try {
            val key = File(c.filesDir, "adbkey.pub").readText().trim().split(Regex("\\s+"))[0]
            java.security.MessageDigest.getInstance("MD5").digest(android.util.Base64.decode(key, android.util.Base64.DEFAULT))
                .joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
