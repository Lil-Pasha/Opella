package com.example.secondwork

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnWrapper: FrameLayout
    private var minRadius = 0f
    private var maxRadius = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnWrapper = findViewById(R.id.btnWrapper)
        minRadius = 80f * resources.displayMetrics.density
        maxRadius = 180f * resources.displayMetrics.density

        val typeface = ResourcesCompat.getFont(this, R.font.american_typewriter_rus_by_me)

        // TASK MANAGER
        findViewById<Button>(R.id.btnTaskManager).apply {
            text = "TASK MANAGER"
            this.typeface = typeface
            setOnClickListener { animateButton(this) { startActivity(Intent(this@MainActivity, TaskManagerActivity::class.java)) } }
        }

        // EDITOR
        findViewById<Button>(R.id.btnLink).apply {
            text = "EDITOR"
            this.typeface = typeface
            setOnClickListener { animateButton(this) { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) } }
        }

        // SETTINGS
        findViewById<TextView>(R.id.btnSettings).apply {
            this.typeface = typeface
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }

        // TITLE
        findViewById<TextView>(R.id.title).typeface = typeface
    }

    // Анимация кнопок
    private fun animateButton(button: Button, onClick: () -> Unit) {
        button.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                onClick()
            }.start()
        }.start()
    }
}
