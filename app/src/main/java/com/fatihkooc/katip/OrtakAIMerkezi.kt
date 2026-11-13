package com.fatihkooc.katip

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import org.json.JSONArray
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
    private val seenMessageIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var conversationRunning = false
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
                val current = speechEngine
                if (current != null) {
                    val language = current.setLanguage(java.util.Locale("tr", "TR"))
                    if (language == TextToSpeech.LANG_MISSING_DATA ||
                        language == TextToSpeech.LANG_NOT_SUPPORTED) {
                        deliver(callback, Result(false, "Türkçe ses paketi cihazda bulunamadı"))
                    } else {
                        current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ortak-ai-" + System.currentTimeMillis())
                        deliver(callback, Result(true, "Sesli okuma başlatıldı"))
                    }
                } else {
                    val created = TextToSpeech(context.applicationContext) { status ->
                        mainHandler.post {
                            if (status != TextToSpeech.SUCCESS) {
                                deliver(callback, Result(false, "Ses motoru başlatılamadı"))
                            } else {
                                val language = speechEngine?.setLanguage(java.util.Locale("tr", "TR"))
                                if (language == TextToSpeech.LANG_MISSING_DATA ||
                                    language == TextToSpeech.LANG_NOT_SUPPORTED || speechEngine == null) {
                                    deliver(callback, Result(false, "Türkçe ses paketi cihazda bulunamadı"))
                                } else {
                                    speechEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ortak-ai-" + System.currentTimeMillis())
                                    deliver(callback, Result(true, "Sesli okuma başlatıldı"))
                                }
                            }
                        }
                    }
                    speechEngine = created
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

    /** Gelen mesajları düzenli kontrol eder, AI ile cevap üretir ve cevabı karşı uygulamaya yollar. */
    fun startConversation(context: Context, intervalMs: Long = 12000L) {
        if (conversationRunning) return
        conversationRunning = true
        val interval = intervalMs.coerceAtLeast(5000L)
        fun scheduleNext() {
            if (conversationRunning) mainHandler.postDelayed({ poll() }, interval)
        }
        fun poll() {
            if (!conversationRunning) return
            getMessages { result ->
                if (result.ok) {
                    try {
                        val root = JSONObject(result.body)
                        val messages = when {
                            root.optJSONArray("messages") != null -> root.optJSONArray("messages")!!
                            root.optJSONArray("items") != null -> root.optJSONArray("items")!!
                            root.optJSONArray("data") != null -> root.optJSONArray("data")!!
                            else -> JSONArray()
                        }
                        for (i in 0 until messages.length()) {
                            val item = messages.optJSONObject(i) ?: continue
                            val from = item.optString("from", item.optString("sender", item.optString("project")))
                            val to = item.optString("to")
                            val content = item.optString("content", item.optString("message", item.optString("text"))).trim()
                            val id = item.optString("id", item.optString("messageId", "$from|$content|$i"))
                            if (content.isBlank() || from == PROJECT || (to.isNotBlank() && to != PROJECT) ||
                                !seenMessageIds.add(id)) continue
                            speak(context, "$from diyor ki: $content")
                            val prompt = "Sen KATIP uygulamasının yapay zekâ asistanısın. " +
                                "$from uygulamasından şu mesaj geldi: $content. Kısa, doğal ve Türkçe cevap ver. Yalnızca cevabı yaz."
                            chat(prompt) { answer ->
                                if (answer.ok) {
                                    try {
                                        val json = JSONObject(answer.body)
                                        val reply = json.optString("response", json.optString("reply", json.optString("text"))).trim()
                                        if (reply.isNotBlank()) {
                                            sendMessage(from, reply) { sent ->
                                                if (sent.ok) speak(context, "Yanıtım: $reply")
                                            }
                                        }
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
                scheduleNext()
            }
        }
        poll()
    }

    fun stopConversation() {
        conversationRunning = false
    }

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
