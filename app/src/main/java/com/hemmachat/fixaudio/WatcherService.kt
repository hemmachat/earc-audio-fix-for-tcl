package com.hemmachat.fixaudio

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
 * Automatic mode: watches the TV's log through its own adb port (USB debugging must stay on; on TCL TVs it also opens adb on port 5555)
 * and reopens the HDMI input (through its other id, or Home → reselect) when the eARC output isn't set up for a new audio format.
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
        val open = { id: String -> "am start -a android.intent.action.VIEW -d '${TvContract.buildChannelUriForPassthroughInput(id)}'" }
        // switching to the port's other id skips the Home screen; without one, leave the input and come back
        val cmd = MainActivity.samePortInput(this, input)?.let(open) ?: "input keyevent KEYCODE_HOME; sleep 3; ${open(input)}"
        val ctx = this
        // own connection with a 30 s timeout (audio fix + MEMC toggle)
        thread(name = "fix") {
            try {
                Dadb.create("127.0.0.1", 5555, keyPair(), 2000, 30_000).use {
                    it.shell(cmd)
                    Thread.sleep(2000) // let PQ pipeline settle after input switch
                    ensureMemcDex(ctx, it)
                    val memcOut = it.shell(MEMC_TOGGLE_CMD).output.trim()
                    Log.i(TAG, "MEMC toggle: $memcOut")
                }
                Log.i(TAG, "fix done")
            } catch (e: Exception) {
                Log.w(TAG, "fix failed: $e")
            }
        }
    }

    private fun keyPair() = keyPairFor(this)

    override fun onDestroy() {
        watching = false
        ticker.shutdownNow()
        // unblocks the log read so the watch thread ends
        connection?.close()
    }

    companion object {
        const val TAG = "FixAudio"
        const val ALLOWED_FILE = "adb-allowed"
        const val MEMC_TOGGLE_CMD = "CLASSPATH=/data/local/tmp/memc.dex:/system_ext/framework/com.tcl.tv.display.jar app_process / MemcToggle toggle"
        @Volatile var waitingForAllow = false
        @Volatile private var watching = false
        @Volatile private var connection: Dadb? = null

        fun keyPairFor(c: android.content.Context): AdbKeyPair {
            val priv = File(c.filesDir, "adbkey")
            val pub = File(c.filesDir, "adbkey.pub")
            if (!priv.exists()) AdbKeyPair.generate(priv, pub)
            return AdbKeyPair.read(priv, pub)
        }

        fun ensureMemcDex(c: android.content.Context, dadb: Dadb) {
            val check = dadb.shell("[ -f /data/local/tmp/memc.dex ] && echo y").output.trim()
            if (check == "y") return
            val tmp = File(c.cacheDir, "memc.dex")
            c.assets.open("memc.dex").use { src -> tmp.outputStream().use { src.copyTo(it) } }
            dadb.push(tmp, "/data/local/tmp/memc.dex", 420, tmp.lastModified())
            tmp.delete()
            Log.i(TAG, "pushed memc.dex to TV")
        }

        /** Toggle MEMC off and back on to fix audio/video sync drift. Safe to call from any context with adb access. */
        fun fixSync(c: android.content.Context, settleDelayMs: Long = 0) {
            thread(name = "fix-sync") {
                try {
                    if (settleDelayMs > 0) Thread.sleep(settleDelayMs)
                    Dadb.create("127.0.0.1", 5555, keyPairFor(c), 2000, 30_000).use {
                        ensureMemcDex(c, it)
                        val memcOut = it.shell(MEMC_TOGGLE_CMD).output.trim()
                        Log.i(TAG, "MEMC toggle: $memcOut")
                    }
                    Log.i(TAG, "sync fix done")
                } catch (e: Exception) {
                    Log.w(TAG, "sync fix failed: $e")
                }
            }
        }

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
