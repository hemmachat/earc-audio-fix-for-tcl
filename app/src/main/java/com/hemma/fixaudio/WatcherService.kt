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

/**
 * Automatic mode: watches the TV's log through its own adb port (Network debugging must stay on)
 * and does the Home → reselect-input fix when the eARC output isn't set up for a new audio format.
 */
class WatcherService : Service() {
    private val detector = Detector()
    private val worker = Executors.newScheduledThreadPool(2)
    @Volatile private var adb: Dadb? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("watch", "Audio watcher", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, "watch")
            .setContentTitle("Watching for silent eARC audio")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .build())
        if (!running) {
            running = true
            worker.execute(::watchLoop)
            worker.scheduleAtFixedRate({
                val now = LocalTime.now().toNanoOfDay() / 1_000_000
                if (synchronized(detector) { detector.tick(now) }) fix()
            }, 1, 1, TimeUnit.SECONDS)
        }
        return START_STICKY
    }

    private fun watchLoop() {
        while (running) {
            try {
                val key = keyPair()
                // first run shows the TV's "Allow USB debugging?" prompt; give up after 60 s so a missed prompt is asked again
                waitingForAllow = true
                Dadb.create("127.0.0.1", 5555, key, 2000, 60_000).use { it.shell("echo ok") }
                waitingForAllow = false
                File(filesDir, ALLOWED_FILE).createNewFile()
                Dadb.create("127.0.0.1", 5555, key).use { dadb ->
                    adb = dadb
                    Log.i(TAG, "connected to TV adb")
                    // -T 1: only new lines, so old log entries never trigger a fix
                    dadb.open("shell:logcat -b all -v time -T 1 -e '${Detector.LOG_FILTER}'").use { stream ->
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
            waitingForAllow = false
            adb = null
            Thread.sleep(5000)
        }
    }

    private fun fix() {
        val dadb = adb ?: return
        val input = MainActivity.savedInput(this) ?: return
        Log.i(TAG, "eARC output not set up for new audio format: reselecting $input")
        worker.execute {
            try {
                dadb.shell("input keyevent KEYCODE_HOME; sleep 3; am start -a android.intent.action.VIEW -d '${TvContract.buildChannelUriForPassthroughInput(input)}'")
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
        running = false
        worker.shutdownNow()
        adb?.close()
    }

    companion object {
        const val TAG = "FixAudio"
        const val ALLOWED_FILE = "adb-allowed"
        @Volatile var waitingForAllow = false
    }
}
