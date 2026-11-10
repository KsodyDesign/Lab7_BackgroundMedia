package com.example.lab7_backgroundmedia

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Keep controls clear of system bars, cutouts and the on-screen keyboard. */
open class BaseActivity : AppCompatActivity() {
    override fun setContentView(layoutResID: Int) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.setContentView(layoutResID)
        val lightBackground = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = lightBackground
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = lightBackground
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }
}
