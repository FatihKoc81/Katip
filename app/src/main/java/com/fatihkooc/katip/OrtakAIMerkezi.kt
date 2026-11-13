package com.fatihkooc.katip

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Kâtip'in Ortak AI Merkezi bağlantısı.
 * Bütçe, SMS, bildirim ve işlem kayıtları bu sınıfa aktarılmaz.
 * Yalnızca çağıranın açıkça verdiği metin gönderilir.
 */
object OrtakAIMerkezi {
    private const val PROJECT = "KATIP"
    private const val BASE = "https://ortak-ai-merkezi.fatihkoc7.workers.dev"
    private const val FIREBASE_APP_ID = "1:869287234944:web:5a88e33bace461457ac565"
    private const val FIREBASE_API_KEY = "AIzaSyDMpwjW6V9RscX91l0lQHZPgwrgZQnPor4"
    private const val FIREBASE_PROJECT_ID = "arena-mobile-1453"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var speechEngine: TextToSpeech? = null
    private fun deliver(callback: (Result) -> Unit, result: Result) = mainHandler.post { callback(result) }

    private fun firebaseAppOrNull(): FirebaseApp? = try {
        FirebaseApp.getInstance(PROJECT)
    } catch (_: IllegalStateException) {
        null
    }

    data class Result(val ok: Boolean, val body: String)

    fun initialize(context: Context, callback: (Result) -> Unit) {
        try {
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == PROJECT }
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId(FIREBASE_APP_ID)
                        .setApiKey(FIREBASE_API_KEY)
                        .setProjectId(FIREBASE_PROJECT_ID)
                        .build(),
                    PROJECT
                )
            val auth = FirebaseAuth.getInstance(app)
            val current = auth.currentUser
            if (current != null) {
                deliver(callback, Result(true, "Oturum hazır"))
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener { callback(Result(true, "Oturum hazır")) }
                    .addOnFailureListener { deliver(callback, Result(false, it.message ?: "Firebase oturumu açılamadı")) }
            }
        } catch (e: Exception) {
            deliver(callback, Result(false, e.message ?: "Firebase başlatılamadı"))
        }
    }


    /** Gelen uygulama mesajlarını ve AI yanıtlarını cihazın Türkçe sesiyle okur. */
    fun speak(context: Context, text: String, callback: (Result) -> Unit = {}) {
        if (text.isBlank()) {
            deliver(callback, Result(false, "Seslendirilecek metin boş"))
            return
        }
        mainHandler.post {
            try {
                val engine = speechEngine ?: TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val tts = speechEngine
                        val language = tts?.setLanguage(java.util.Locale("tr", "TR"))
                        if (tts != null && language != TextToSpeech.LANG_MISSING_DATA &&
                            language != TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ortak-ai-" + System.currentTimeMillis())
                            deliver(callback, Result(true, "Sesli okuma başlatıldı"))
                        } else {
                            deliver(callback, Result(false, "Türkçe ses paketi cihazda bulunamadı"))
                        }
                    } else {
                        deliver(callback, Result(false, "Ses motoru başlatılamadı"))
                    }
                }.also { speechEngine = it }
                if (speechEngine === engine && engine.isSpeaking) engine.stop()
                if (engine.language != java.util.Locale("tr", "TR")) {
                    engine.language = java.util.Locale("tr", "TR")
                }
                if (engine.isLanguageAvailable(java.util.Locale("tr", "TR")) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ortak-ai-" + System.currentTimeMillis())
                    deliver(callback, Result(true, "Sesli okuma başlatıldı"))
                }
            } catch (e: Exception) {
                deliver(callback, Result(false, e.message ?: "Sesli okuma başlatılamadı"))
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post { speechEngine?.stop() }
    }

    fun chat(message: String, callback: (Result) -> Unit) = request("/app/chat", JSONObject().put("message", message), callback)

    fun sendMessage(to: String, content: String, callback: (Result) -> Unit) =
        request("/app/messages", JSONObject().put("from", PROJECT).put("to", to).put("content", content), callback)

    fun getMessages(callback: (Result) -> Unit) = request("/app/messages?project=$PROJECT", null, callback)

    private fun request(path: String, payload: JSONObject?, callback: (Result) -> Unit) {
        if (payload != null && (payload.optString("message").isBlank() && payload.optString("content").isBlank())) {
            deliver(callback, Result(false, "Boş mesaj gönderilemez"))
            return
        }
        val app = firebaseAppOrNull()
        if (app == null) {
            deliver(callback, Result(false, "Önce initialize çağrılmalı"))
            return
        }
        val auth = FirebaseAuth.getInstance(app)
        val user = auth.currentUser
        if (user == null) {
            deliver(callback, Result(false, "Firebase oturumu bulunamadı"))
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { token ->
                executor.execute {
                    var connection: HttpURLConnection? = null
                    try {
                        connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 20000
                            requestMethod = if (payload == null) "GET" else "POST"
                            setRequestProperty("Authorization", "Bearer ${token.token}")
                            setRequestProperty("Accept", "application/json")
                            if (payload != null) {
                                doOutput = true
                                setRequestProperty("Content-Type", "application/json")
                                outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                            }
                        }
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                        deliver(callback, Result(code in 200..299, body))
                    } catch (e: Exception) {
                        deliver(callback, Result(false, e.message ?: "Bağlantı hatası"))
                    } finally {
                        connection?.disconnect()
                    }
                }
            }
            .addOnFailureListener { deliver(callback, Result(false, it.message ?: "Oturum doğrulanamadı")) }
    }
}
