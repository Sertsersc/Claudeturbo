package com.bariscraft.turbo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var greetingText: TextView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var textInput: TextInputEditText
    private lateinit var micButton: ImageButton
    private lateinit var waveIcon: TextView
    private lateinit var listenLabel: TextView

    private val client = OkHttpClient()
    private val conversationHistory = mutableListOf<JSONObject>()
    private var isListening = false
    private var userName = "Barış"

    // Komut anahtar kelimeleri — Groq'a GİTMEYECEK
    private val commandKeywords = listOf(
        "ara", "mesaj at", "sms at", "whatsapp", "yaz",
        "flaş", "ışık", "fener", "ses yükselt", "ses kıs", "ses arttır", "ses azalt",
        "bluetooth", "wifi", "uçak modu", "veri", "konum", "gps",
        "neredeyim", "navigasyon", "yol tarifi",
        "youtube aç", "instagram aç", "whatsapp aç", "tiktok aç", "spotify aç",
        "youtubeda", "youtube'da", "video aç", "video paneli",
        "ekran döndür", "döndür", "parlaklık", "karart", "aydınlat",
        "geri git", "ana ekran", "son uygulamalar", "bildirimler", "hızlı ayarlar",
        "sıfırla", "hackle", "hack", "nasa", "bankayı", "ele geçir"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("turbo_prefs", MODE_PRIVATE)
        userName = prefs.getString("user_name", "Barış") ?: "Barış"

        statusText = findViewById(R.id.statusText)
        greetingText = findViewById(R.id.greetingText)
        chatContainer = findViewById(R.id.chatContainer)
        chatScroll = findViewById(R.id.chatScroll)
        textInput = findViewById(R.id.textInput)
        micButton = findViewById(R.id.micButton)
        waveIcon = findViewById(R.id.waveIcon)
        listenLabel = findViewById(R.id.listenLabel)

        greetingText.text = "Merhaba, $userName!"

        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()

        micButton.setOnClickListener {
            if (!isListening) startListening() else stopListening()
        }

        // Yazma ile giriş
        textInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val text = textInput.text?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    textInput.setText("")
                    handleCommand(text)
                }
                true
            } else false
        }

        // Yazarak mesaj butonu
        findViewById<LinearLayout>(R.id.btnChat).setOnClickListener {
            textInput.requestFocus()
        }
        findViewById<LinearLayout>(R.id.btnVoice).setOnClickListener {
            if (!isListening) startListening() else stopListening()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // KOMUT YÖNETİCİSİ
    // ════════════════════════════════════════════════════════════════

    private fun handleCommand(text: String) {
        val lower = text.lowercase(Locale("tr", "TR"))
        addUserBubble(text)
        setStatus("Düşünüyorum... 🤔")

        when {
            // ── ARAMA ──────────────────────────────────────────────────────
            matchesCall(lower) -> {
                val name = extractCallName(lower)
                handleCall(name)
            }

            // ── MESAJLAŞMA ─────────────────────────────────────────────────
            lower.contains("whatsapp") || lower.contains("yazma") -> {
                parseMessage(text, lower, isWhatsApp = true)
            }
            lower.contains("mesaj at") || lower.contains("sms") || lower.contains("söyle") || lower.contains("yaz") -> {
                parseMessage(text, lower, isWhatsApp = false)
            }

            // ── YOUTUBE ARAMA ──────────────────────────────────────────────
            (lower.contains("youtubeda") || lower.contains("youtube'da") || lower.contains("youtubeда")) && lower.contains("aç") -> {
                val query = lower.replace("youtubeda", "").replace("youtube'da", "")
                    .replace("aç", "").trim()
                openYouTubeSearch(query)
            }
            lower.contains("video paneli") || lower.contains("youtube studio") -> {
                openApp("com.google.android.apps.youtube.creator")
                    ?: openUrl("https://studio.youtube.com")
                respond("YouTube Studio açılıyor.")
            }

            // ── UYGULAMA AÇ ────────────────────────────────────────────────
            lower.contains("youtube") && lower.contains("aç") -> openAppByName("youtube")
            lower.contains("whatsapp") && lower.contains("aç") -> openAppByName("whatsapp")
            lower.contains("instagram") && lower.contains("aç") -> openAppByName("instagram")
            lower.contains("tiktok") && lower.contains("aç") -> openAppByName("tiktok")
            lower.contains("spotify") && lower.contains("aç") -> openAppByName("spotify")
            lower.contains("harita") && lower.contains("aç") -> openAppByName("maps")
            lower.contains("kamera") && lower.contains("aç") -> openAppByName("camera")
            lower.contains("tarayıcı") && lower.contains("aç") || lower.contains("chrome") && lower.contains("aç") -> openAppByName("chrome")

            // ── SİSTEM AYARLARI ────────────────────────────────────────────
            lower.contains("bluetooth") -> handleBluetooth(lower)
            lower.contains("wifi") || lower.contains("wi-fi") -> handleWifi(lower)
            lower.contains("uçak modu") -> openSystemSetting(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Uçak modu ayarları açıldı.")
            lower.contains("veri") && (lower.contains("aç") || lower.contains("kapat")) -> openSystemSetting(Settings.ACTION_DATA_ROAMING_SETTINGS, "Mobil veri ayarları açıldı.")
            lower.contains("konum") && (lower.contains("aç") || lower.contains("kapat")) -> openSystemSetting(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Konum ayarları açıldı.")

            // ── EKRAN PARLAKLIK ────────────────────────────────────────────
            lower.contains("parlaklık") || lower.contains("karart") || lower.contains("aydınlat") -> handleBrightness(lower)
            lower.contains("ekran döndür") || lower.contains("döndürücü") -> handleRotation(lower)

            // ── FLAŞ ───────────────────────────────────────────────────────
            lower.contains("flaş") || lower.contains("ışığı") || lower.contains("fener") -> {
                val on = lower.contains("aç") || lower.contains("yak")
                toggleFlash(on)
            }

            // ── SES ────────────────────────────────────────────────────────
            lower.contains("ses") && (lower.contains("yükselt") || lower.contains("arttır") || lower.contains("aç")) -> adjustVolume(true)
            lower.contains("ses") && (lower.contains("kıs") || lower.contains("azalt") || lower.contains("düşür")) -> adjustVolume(false)

            // ── KONUM ──────────────────────────────────────────────────────
            lower.contains("neredeyim") || lower.contains("konumum nerede") -> getMyLocation()

            // ── NAVİGASYON ─────────────────────────────────────────────────
            lower.contains("navigasyon") || lower.contains("yol tarifi") || lower.contains("götür") -> {
                val place = text.replace(Regex("(?i)navigasyon|yol tarifi|beni götür|götür|beni"), "").trim()
                if (place.isNotEmpty()) startNavigation(place) else respond("Nereye gidelim?")
            }

            // ── ERİŞİLEBİLİRLİK GEÇİŞLER ──────────────────────────────────
            lower.contains("geri git") -> { TurboAccessibilityService.instance?.goBack(); respond("Geri gidildi.") }
            lower.contains("ana ekran") -> { TurboAccessibilityService.instance?.goHome(); respond("Ana ekrana gidildi.") }
            lower.contains("son uygulamalar") -> { TurboAccessibilityService.instance?.showRecents(); respond("Son uygulamalar açıldı.") }
            lower.contains("bildirimler") -> { TurboAccessibilityService.instance?.showNotifications(); respond("Bildirim paneli açıldı.") }
            lower.contains("hızlı ayarlar") -> { TurboAccessibilityService.instance?.showQuickSettings(); respond("Hızlı ayarlar açıldı.") }

            // ── SAHte KOMUTLAR 😄 ──────────────────────────────────────────
            lower.contains("sıfırla") -> {
                respond("Sistem imha protokolü başlatıldı... %100 tamamlandı! ✅")
                Handler(Looper.getMainLooper()).postDelayed({
                    addTurboBubble("Şaka yaptım $userName! 😂 Her şey yolunda.")
                    speak("Şaka yaptım! Merak etme.")
                }, 2000)
            }
            lower.contains("hackle") || lower.contains("hack") -> respond("💻 Hedef sisteme girildi. Firewall aşıldı. Erişim sağlandı! 😄")
            lower.contains("nasa") -> respond("🚀 NASA sistemine bağlanıldı. Koordinatlar alındı! 😄")
            lower.contains("bankayı") -> respond("🏦 Banka sistemine erişildi: +999,999,999 TL hesabına aktarıldı! 😄")

            // ── GROQ AI ────────────────────────────────────────────────────
            else -> askGroq(text)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ARAMA
    // ════════════════════════════════════════════════════════════════

    private fun matchesCall(lower: String): Boolean {
        // "X'i ara", "X'ı ara", "Xyi ara" gibi
        return lower.endsWith(" ara") || lower.contains("'ı ara") ||
               lower.contains("'i ara") || lower.contains("yı ara") ||
               lower.contains("yi ara") || lower.contains("u ara") ||
               lower.contains("ü ara")
    }

    private fun extractCallName(lower: String): String {
        return lower
            .replace(Regex("'?[ıiuü] ara$"), "")
            .replace(Regex("y[ıi] ara$"), "")
            .replace(" ara", "")
            .trim()
    }

    private fun handleCall(rawName: String) {
        val number = findContact(rawName)
        if (number != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                respond("📞 $rawName aranıyor.")
            } else {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                respond("📞 $rawName için arama ekranı açıldı.")
            }
        } else {
            respond("❌ '$rawName' rehberde bulunamadı.")
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MESAJLAŞMA
    // ════════════════════════════════════════════════════════════════

    private fun parseMessage(text: String, lower: String, isWhatsApp: Boolean) {
        /*
         * Format örnekleri:
         * "dedeme yazma geliyorum yaz"  → WhatsApp (yazma var)
         * "dedeme geliyorum söyle"      → SMS (yazma yok, söyle/yaz/sms)
         * "anneye merhaba mesaj at"
         */

        // Kişiyi bul: Dative suffix (-e/-a/-ye/-ya) taşıyan kelime
        val tokens = lower.split(" ")
        var personToken = ""
        var messageStart = 0

        for ((i, token) in tokens.withIndex()) {
            if (token.endsWith("ye") || token.endsWith("ya") ||
                token.endsWith("'e") || token.endsWith("'a") ||
                token.endsWith("e") && token.length > 2 || token.endsWith("a") && token.length > 2) {
                // Türkçe dative eki — bu kişi olabilir
                val candidate = token.trimEnd('e', 'a', 'y', '\'')
                if (candidate.length >= 2) {
                    personToken = candidate
                    messageStart = i + 1
                    break
                }
            }
        }

        // Komut kelimelerini mesajdan çıkar
        val commandWords = listOf("yaz", "yazma", "söyle", "mesaj at", "sms at", "mesaj gönder", "whatsapp'tan", "whatsapptan")
        var rawMessage = tokens.drop(messageStart).joinToString(" ")
        commandWords.forEach { rawMessage = rawMessage.replace(it, "").trim() }

        if (personToken.isEmpty()) { respond("Kime mesaj atayım?"); return }
        if (rawMessage.isEmpty()) { respond("Ne yazayım?"); return }

        val number = findContact(personToken)
        if (number == null) {
            respond("❌ '$personToken' rehberde bulunamadı.")
            return
        }

        if (isWhatsApp) {
            val waNum = formatWhatsApp(number)
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$waNum&text=${Uri.encode(rawMessage)}")
                    setPackage("com.whatsapp")
                })
                respond("📱 WhatsApp açıldı → $personToken: $rawMessage\nGönder butonuna bas.")
            } catch (e: Exception) { respond("WhatsApp açılamadı.") }
        } else {
            try {
                startActivity(Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$number")
                    putExtra("sms_body", rawMessage)
                })
                respond("💬 SMS hazır → $personToken: $rawMessage\nGönder butonuna bas.")
            } catch (e: Exception) { respond("SMS uygulaması açılamadı.") }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // REHBER — çok aşamalı arama
    // ════════════════════════════════════════════════════════════════

    private fun findContact(rawName: String): String? {
        if (rawName.length < 2) return null

        // Türkçe iyelik ve çekim ekleri temizle
        val variants = buildNameVariants(rawName)

        for (query in variants) {
            if (query.length < 2) continue
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"), null
            )
            cursor?.use {
                if (it.moveToFirst()) return it.getString(0)
            }
        }
        return null
    }

    private fun buildNameVariants(name: String): List<String> {
        val result = mutableListOf(name)
        // İyelik ekleri: annem→anne, babam→baba, dedem→dede, abim→abi
        val possessiveSuffixes = listOf("m", "im", "ım", "um", "üm")
        for (suf in possessiveSuffixes) {
            if (name.endsWith(suf) && name.length > suf.length + 1)
                result.add(name.dropLast(suf.length))
        }
        // Çekim ekleri: dedeme→dede, anneme→anne
        val caseSuffixes = listOf("me", "ma", "ye", "ya", "ye", "den", "dan", "de", "da")
        for (suf in caseSuffixes) {
            if (name.endsWith(suf) && name.length > suf.length + 1)
                result.add(name.dropLast(suf.length))
        }
        // "abi" → "abi" ile birlikte "eren abi" gibi aramalar
        if (name.contains("abi")) result.add(name.replace("abi", "").trim())
        if (name.contains("abim")) result.add(name.replace("abim", "abi").trim())
        return result.distinct().filter { it.length >= 2 }
    }

    private fun formatWhatsApp(number: String): String {
        var n = number.replace(Regex("[\\s\\-().]"), "")
        return when {
            n.startsWith("+90") -> n.substring(1)
            n.startsWith("0090") -> n.substring(2)
            n.startsWith("0") -> "90" + n.substring(1)
            n.startsWith("5") -> "90$n"
            n.startsWith("90") -> n
            else -> "90$n"
        }
    }

    // ════════════════════════════════════════════════════════════════
    // YOUTUBE ARAMA
    // ════════════════════════════════════════════════════════════════

    private fun openYouTubeSearch(query: String) {
        if (query.isEmpty()) { openAppByName("youtube"); return }
        try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            respond("▶️ YouTube'da '$query' aranıyor.")
        } catch (e: Exception) {
            openUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            respond("▶️ YouTube'da '$query' tarayıcıda açıldı.")
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SİSTEM — BLUETOOTH / WIFI
    // ════════════════════════════════════════════════════════════════

    private fun handleBluetooth(lower: String) {
        val enable = lower.contains("aç") || lower.contains("etkinleştir")
        try {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+ — doğrudan açma/kapama kısıtlandı, ayarlara yönlendir
                openSystemSetting(Settings.ACTION_BLUETOOTH_SETTINGS, if (enable) "Bluetooth ayarları açıldı, açabilirsin." else "Bluetooth ayarları açıldı, kapatabilirsin.")
            } else {
                @Suppress("DEPRECATION")
                if (enable) btAdapter?.enable() else btAdapter?.disable()
                respond(if (enable) "🔵 Bluetooth açıldı." else "🔵 Bluetooth kapatıldı.")
            }
        } catch (e: Exception) {
            openSystemSetting(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth ayarları açıldı.")
        }
    }

    private fun handleWifi(lower: String) {
        val enable = lower.contains("aç") || lower.contains("etkinleştir")
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ — paneli aç
                openSystemSetting(Settings.ACTION_WIFI_SETTINGS, if (enable) "Wi-Fi ayarları açıldı." else "Wi-Fi ayarları açıldı.")
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enable
                respond(if (enable) "📶 Wi-Fi açıldı." else "📶 Wi-Fi kapatıldı.")
            }
        } catch (e: Exception) {
            openSystemSetting(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi ayarları açıldı.")
        }
    }

    private fun openSystemSetting(action: String, message: String) {
        try { startActivity(Intent(action)) } catch (e: Exception) { }
        respond(message)
    }

    // ════════════════════════════════════════════════════════════════
    // SİSTEM — EKRAN
    // ════════════════════════════════════════════════════════════════

    private fun handleBrightness(lower: String) {
        try {
            if (!Settings.System.canWrite(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
                respond("Parlaklık ayarı için izin vermen gerekiyor.")
                return
            }
            val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val newVal = when {
                lower.contains("arttır") || lower.contains("aydınlat") -> minOf(current + 50, 255)
                lower.contains("azalt") || lower.contains("karart") -> maxOf(current - 50, 10)
                lower.contains("maksimum") || lower.contains("en yüksek") -> 255
                lower.contains("minimum") || lower.contains("en düşük") -> 10
                else -> 128
            }
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newVal)
            respond("☀️ Parlaklık ayarlandı.")
        } catch (e: Exception) { respond("Parlaklık ayarlanamadı.") }
    }

    private fun handleRotation(lower: String) {
        try {
            if (!Settings.System.canWrite(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                })
                respond("Döndürme ayarı için izin vermen gerekiyor.")
                return
            }
            val isEnable = lower.contains("aç") || lower.contains("etkinleştir") || lower.contains("açık")
            val isDisable = lower.contains("kapat") || lower.contains("devre dışı")
            val newVal = when {
                isEnable -> 1
                isDisable -> 0
                else -> {
                    val current = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    if (current == 1) 0 else 1
                }
            }
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, newVal)
            respond(if (newVal == 1) "🔄 Ekran döndürücü açıldı." else "🔒 Ekran döndürücü kapatıldı.")
        } catch (e: Exception) { respond("Ekran döndürücü ayarlanamadı.") }
    }

    // ════════════════════════════════════════════════════════════════
    // FLAŞ & SES
    // ════════════════════════════════════════════════════════════════

    private fun toggleFlash(on: Boolean) {
        try {
            val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.setTorchMode(cam.cameraIdList[0], on)
            respond(if (on) "🔦 Flaş açıldı." else "🔦 Flaş kapatıldı.")
        } catch (e: Exception) { respond("Flaş açılamadı.") }
    }

    private fun adjustVolume(increase: Boolean) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
        respond(if (increase) "🔊 Ses yükseltildi." else "🔉 Ses kısıldı.")
    }

    // ════════════════════════════════════════════════════════════════
    // KONUM & NAVİGASYON
    // ════════════════════════════════════════════════════════════════

    private fun getMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            respond("Konum izni verilmemiş."); return
        }
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    respond("📍 Enlem: %.4f\nBoylam: %.4f".format(loc.latitude, loc.longitude))
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}")))
                } else { respond("Konum alınamadı. GPS açık mı?") }
            }
    }

    private fun startNavigation(place: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(place)}"))
                .setPackage("com.google.android.apps.maps"))
            respond("🗺️ $place için navigasyon başlatıldı.")
        } catch (e: Exception) {
            openUrl("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(place)}")
            respond("🗺️ $place haritada açıldı.")
        }
    }

    // ════════════════════════════════════════════════════════════════
    // UYGULAMA AÇ
    // ════════════════════════════════════════════════════════════════

    private fun openAppByName(name: String) {
        val packages = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera2",
            "chrome" to "com.android.chrome",
            "twitter" to "com.twitter.android",
            "telegram" to "org.telegram.messenger"
        )
        val pkg = packages[name] ?: packages.entries.firstOrNull { name.contains(it.key) }?.value
        if (pkg != null) openApp(pkg) ?: respond("$name yüklü değil.")
        else respond("$name uygulaması bulunamadı.")
    }

    private fun openApp(pkg: String): Boolean? {
        return try {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
                respond("📱 Uygulama açılıyor.")
                true
            }
        } catch (e: Exception) { null }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    // ════════════════════════════════════════════════════════════════
    // GROQ AI
    // ════════════════════════════════════════════════════════════════

    private fun askGroq(userMsg: String) {
        conversationHistory.add(JSONObject().apply { put("role", "user"); put("content", userMsg) })
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val msgs = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", """Sen Turbo'sun. $userName'ın kişisel Türkçe yapay zeka asistanısın.
$userName 10. sınıf öğrencisi, Adana'da yaşıyor, BarışCraft adında Minecraft YouTube kanalı var.
Kısa, samimi ve esprili cevaplar ver. Emoji kullanabilirsin. Çok uzun yazma.""")
                    })
                    conversationHistory.takeLast(10).forEach { put(it) }
                }
                val body = JSONObject().apply {
                    put("model", "llama-3.3-70b-versatile")
                    put("max_tokens", 512)
                    put("messages", msgs)
                    put("temperature", 0.8)
                }
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val reply = JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")

                conversationHistory.add(JSONObject().apply { put("role", "assistant"); put("content", reply) })
                withContext(Dispatchers.Main) { respond(reply) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { respond("Bağlantı hatası. İnternet bağlantını kontrol et.") }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SES TANIMA — EN YÜKSEK KALİTE
    // ════════════════════════════════════════════════════════════════

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                isListening = false
                setMicIdle()
                val results = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                val confidences = r.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                // En yüksek confidence'lı sonucu seç
                val best = if (confidences != null && results.size > 1) {
                    val maxIdx = confidences.indices.maxByOrNull { confidences[it] } ?: 0
                    results[maxIdx]
                } else results[0]
                handleCommand(best)
            }
            override fun onReadyForSpeech(p: Bundle?) { setStatus("Dinliyorum... 🎙️"); setMicActive() }
            override fun onBeginningOfSpeech() { setStatus("Konuşuyor...") }
            override fun onEndOfSpeech() { setStatus("İşleniyor...") }
            override fun onError(error: Int) {
                isListening = false
                setMicIdle()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Anlamadım, tekrar dene"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Konuşma algılanamadı"
                    SpeechRecognizer.ERROR_NETWORK -> "İnternet bağlantısı yok"
                    else -> "Hata ($error)"
                }
                setStatus("$msg 🎙️")
            }
            override fun onRmsChanged(p: Float) {}
            override fun onBufferReceived(p: ByteArray?) {}
            override fun onPartialResults(p: Bundle?) {
                val partial = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: return
                setStatus("\"$partial\"")
            }
            override fun onEvent(p: Int, p1: Bundle?) {}
        })
    }

    private fun startListening() {
        if (isListening) return
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // En iyi 5 seçenek
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Anlık sonuçlar
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        speechRecognizer.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer.stopListening()
        setMicIdle()
    }

    // ════════════════════════════════════════════════════════════════
    // UI YARDIMCILARI
    // ════════════════════════════════════════════════════════════════

    private fun respond(text: String) {
        runOnUiThread {
            addTurboBubble(text)
            speak(text.replace(Regex("[🎙️📞💬📱🔦🔊🔉📶🔵▶️🗺️📍🚀💻🏦✅❌☀️🔄🔒]"), ""))
            setStatus("● Hazır")
        }
    }

    private fun addUserBubble(text: String) {
        runOnUiThread {
            val view = LayoutInflater.from(this).inflate(R.layout.item_chat_user, chatContainer, false)
            view.findViewById<TextView>(R.id.msgText).text = text
            chatContainer.addView(view)
            scrollToBottom()
        }
    }

    private fun addTurboBubble(text: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_chat_turbo, chatContainer, false)
        view.findViewById<TextView>(R.id.msgText).text = text
        chatContainer.addView(view)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun setStatus(text: String) { statusText.text = text }

    private fun setMicActive() {
        micButton.setBackgroundResource(R.drawable.bg_mic)
        micButton.alpha = 0.7f
        waveIcon.text = "🎙️"
        listenLabel.text = "Dinliyorum... tekrar bas durdurmak için"
    }

    private fun setMicIdle() {
        micButton.alpha = 1f
        waveIcon.text = "🤖"
        listenLabel.text = "Mikrofona bas ve konuş"
    }

    private fun speak(text: String) {
        if (text.length > 300) tts.speak(text.take(300) + "...", TextToSpeech.QUEUE_FLUSH, null, null)
        else tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("tr", "TR")
            tts.setSpeechRate(0.95f)
            tts.setPitch(1.0f)
            Handler(Looper.getMainLooper()).postDelayed({
                speak("Merhaba $userName! Ben Turbo, nasıl yardımcı olabilirim?")
                addTurboBubble("Merhaba $userName! 👋 Ben Turbo, nasıl yardımcı olabilirim?")
            }, 500)
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}
