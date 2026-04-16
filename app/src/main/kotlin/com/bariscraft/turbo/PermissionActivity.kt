package com.bariscraft.turbo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionActivity : AppCompatActivity() {

    data class PermStep(
        val icon: String,
        val title: String,
        val desc: String,
        val permissions: Array<String>?,  // null = accessibility
        val isAccessibility: Boolean = false
    )

    private val steps = listOf(
        PermStep("🎙️", "Mikrofon İzni",
            "Turbo seni duyabilmek için mikrofon iznine ihtiyaç duyuyor.",
            arrayOf(Manifest.permission.RECORD_AUDIO)),
        PermStep("📍", "Konum İzni",
            "Nerede olduğunu söyleyebilmek için konum iznine ihtiyaç duyuyor.",
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        PermStep("📞", "Arama & SMS",
            "Kişileri arayabilmek ve mesaj atabilmek için izin gerekli.",
            arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)),
        PermStep("📖", "Rehber",
            "Kişileri isimle bulabilmek için rehbere erişim gerekli.",
            arrayOf(Manifest.permission.READ_CONTACTS)),
        PermStep("📷", "Kamera",
            "Flaş ve kamera kontrolü için gerekli.",
            arrayOf(Manifest.permission.CAMERA)),
        PermStep("♿", "Erişilebilirlik Hizmeti",
            "Uygulamalar arası geçiş, sistem kontrolü ve otomasyonu için Turbo'ya erişilebilirlik hizmetini etkinleştirmen gerekiyor.",
            null, isAccessibility = true)
    )

    private var currentStep = 0
    private lateinit var permIcon: TextView
    private lateinit var permTitle: TextView
    private lateinit var permDesc: TextView
    private lateinit var permStatus: TextView
    private lateinit var nextBtn: Button
    private lateinit var skipBtn: TextView
    private lateinit var dotContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        permIcon = findViewById(R.id.permIcon)
        permTitle = findViewById(R.id.permTitle)
        permDesc = findViewById(R.id.permDesc)
        permStatus = findViewById(R.id.permStatus)
        nextBtn = findViewById(R.id.nextBtn)
        skipBtn = findViewById(R.id.skipBtn)
        dotContainer = findViewById(R.id.dotContainer)

        buildDots()
        showStep(0)

        nextBtn.setOnClickListener { onNextClicked() }
        skipBtn.setOnClickListener { advanceStep() }
    }

    private fun buildDots() {
        dotContainer.removeAllViews()
        steps.forEachIndexed { i, _ ->
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(if (i == currentStep) 24 else 8, 8).apply {
                    setMargins(4, 0, 4, 0)
                }
                background = ContextCompat.getDrawable(this@PermissionActivity,
                    if (i == currentStep) R.drawable.bg_button_purple else R.drawable.bg_dot_inactive)
                tag = "dot_$i"
            }
            dotContainer.addView(dot)
        }
    }

    private fun showStep(index: Int) {
        currentStep = index
        val step = steps[index]
        permIcon.text = step.icon
        permTitle.text = step.title
        permDesc.text = step.desc
        permStatus.visibility = View.GONE

        val granted = isStepGranted(step)
        nextBtn.text = when {
            granted -> "Sonraki →"
            step.isAccessibility -> "Ayarlara Git"
            else -> "İzin Ver"
        }

        buildDots()
    }

    private fun onNextClicked() {
        val step = steps[currentStep]
        if (isStepGranted(step)) {
            advanceStep()
            return
        }
        if (step.isAccessibility) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            permStatus.text = "✓ Verildikten sonra Sonraki'ye bas"
            permStatus.visibility = View.VISIBLE
            nextBtn.text = "Sonraki →"
        } else {
            step.permissions?.let {
                ActivityCompat.requestPermissions(this, it, currentStep + 100)
            }
        }
    }

    private fun isStepGranted(step: PermStep): Boolean {
        if (step.isAccessibility) return isAccessibilityEnabled()
        return step.permissions?.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        } ?: true
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.contains(packageName)
    }

    private fun advanceStep() {
        if (currentStep < steps.size - 1) {
            showStep(currentStep + 1)
        } else {
            getSharedPreferences("turbo_prefs", MODE_PRIVATE).edit()
                .putBoolean("perms_done", true).apply()
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val step = steps[currentStep]
        if (isStepGranted(step)) {
            permStatus.text = "✓ İzin verildi!"
            permStatus.visibility = View.VISIBLE
            nextBtn.text = "Sonraki →"
        }
    }

    override fun onResume() {
        super.onResume()
        // Accessibility settings'den döndüğünde kontrol et
        val step = steps[currentStep]
        if (step.isAccessibility && isAccessibilityEnabled()) {
            permStatus.text = "✓ Erişilebilirlik aktif!"
            permStatus.visibility = View.VISIBLE
            nextBtn.text = "Sonraki →"
        }
    }
}
