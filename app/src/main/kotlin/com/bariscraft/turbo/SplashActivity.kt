package com.bariscraft.turbo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs: SharedPreferences = getSharedPreferences("turbo_prefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", null)
        val permsDone = prefs.getBoolean("perms_done", false)

        // Animasyon
        val robot = findViewById<ImageView>(R.id.robotIcon)
        robot.animate().scaleX(1.05f).scaleY(1.05f).setDuration(800)
            .withEndAction { robot.animate().scaleX(1f).scaleY(1f).setDuration(800).withEndAction { animateRobot(robot) }.start() }.start()

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            when {
                !permsDone -> startActivity(Intent(this, PermissionActivity::class.java))
                userName == null -> startActivity(Intent(this, WelcomeActivity::class.java))
                else -> startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }

    private fun animateRobot(view: ImageView) {
        view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(800)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(800).withEndAction { animateRobot(view) }.start() }.start()
    }
}
