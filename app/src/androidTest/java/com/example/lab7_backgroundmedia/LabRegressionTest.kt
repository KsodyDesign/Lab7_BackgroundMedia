package com.example.lab7_backgroundmedia

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class LabRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var activity: Activity
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun launch(type: Class<out Activity>) {
        activity = instrumentation.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        main { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        Thread.sleep(1200)
        instrumentation.waitForIdleSync()
        current()
    }
    private fun enter(id: Int, text: String) = main { activity.findViewById<EditText>(id).setText(text) }
    private fun click(id: Int) {
        main { activity.findViewById<android.view.View>(id).performClick() }
        instrumentation.waitForIdleSync()
        Thread.sleep(350)
    }
    private fun text(id: Int): String {
        var result = ""
        main { result = activity.findViewById<TextView>(id).text.toString() }
        return result
    }
    private fun current() {
        main { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first() }
    }
    private fun rotate() {
        val old = activity
        main { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        Thread.sleep(1800)
        instrumentation.waitForIdleSync()
        current()
        assertNotSame("Activity must recreate on rotation", old, activity)
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(1000)
        val file = File(context.getExternalFilesDir(null), "audit/$name.png")
        file.parentFile!!.mkdirs()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun finish() {
        main { activity.finish() }
        instrumentation.waitForIdleSync()
    }
    private fun field(name: String): Any? {
        val field = activity.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(activity)
    }

    @Test fun foregroundPlaylistAndControllers() {
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        launch(MainActivity::class.java)
        click(R.id.btnPause)
        Thread.sleep(5500)
        assertTrue(text(R.id.tvStatus).contains("Остановлено"))
        screenshot("01-pause-before-play")
        click(R.id.btnPlay)
        assertTrue(text(R.id.tvStatus).contains("Воспроизводится"))
        screenshot("02-playing")
        click(R.id.btnNext)
        assertTrue(text(R.id.tvTrackTitle).contains("2"))
        click(R.id.btnPause)
        screenshot("03-next-paused")
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(700)
        screenshot("03-notification")
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        Thread.sleep(500)
        finish()
        launch(MainActivity::class.java)
        assertTrue(text(R.id.tvTrackTitle).contains("2"))
        assertTrue(text(R.id.tvStatus).contains("паузе"))
        screenshot("04-restored-state")
        // Use the system's active MediaSession, as a headset/controller would.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent 88")
        ).use { it.readBytes() }
        Thread.sleep(600)
        assertTrue(text(R.id.tvTrackTitle).contains("1"))
        screenshot("05-controller-previous")
        Thread.sleep(8500)
        assertTrue(text(R.id.tvTrackTitle).contains("2"))
        screenshot("06-auto-next")
        click(R.id.btnStop)
        assertTrue(text(R.id.tvStatus).contains("Остановлено"))
        screenshot("07-stopped")
        finish()
    }
}
