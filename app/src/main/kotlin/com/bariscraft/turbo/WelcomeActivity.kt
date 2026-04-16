package com.bariscraft.turbo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val nameInput = findViewById<TextInputEditText>(R.id.nameInput)
        val continueBtn = findViewById<Button>(R.id.continueBtn)

        continueBtn.setOnClickListener {
            val name = nameInput.text?.toString()?.trim()
            if (!name.isNullOrEmpty()) {
                getSharedPreferences("turbo_prefs", MODE_PRIVATE).edit()
                    .putString("user_name", name).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                nameInput.error = "İsmini yaz!"
            }
        }
    }
}
