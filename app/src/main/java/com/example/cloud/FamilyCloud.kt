package com.example.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Что делает этот телефон: носит браслет или получает тревоги близкого. */
enum class PhoneRole { UNSET, WEARER, CAREGIVER }

/** Как телефон с браслетом оповещает родственников. */
enum class AlertDelivery { APP_THEN_SMS, APP, SMS, BOTH }

data class FamilyState(
    val role: PhoneRole = PhoneRole.UNSET,
    val serverUrl: String = "",
    val myName: String = "",
    val registered: Boolean = false,
    val delivery: AlertDelivery = AlertDelivery.APP_THEN_SMS,
    val ackTimeoutSeconds: Int = 120,
    val links: List<FamilyLink> = emptyList(),
    val online: Boolean? = null,
    val pushAvailable: Boolean = false,
    val inbox: List<RemoteAlert> = emptyList()
)

/** Тревога, отправленная с этого телефона через сервер, пока ждём ответа. */
data class LiveAlert(val id: String, val linked: Int, val ackedByName: String? = null)

/**
 * Связь с сервером Қорған (server/korgan_server.py): регистрация телефона,
 * связка «носит браслет ↔ родственник» по коду, отправка тревоги и ответ «Я еду».
 */
class FamilyCloud(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("family_cloud", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    private val _liveAlert = MutableStateFlow<LiveAlert?>(null)
    val liveAlert: StateFlow<LiveAlert?> = _liveAlert.asStateFlow()

    /** Нужен при регистрации телефона с браслетом: имя берётся из профиля. */
    var wearerNameProvider: () -> String = { "" }

    private fun load(): FamilyState {
        val storedRole = prefs.getString(KEY_ROLE, null)
        return FamilyState(
            role = storedRole?.let { runCatching { PhoneRole.valueOf(it) }.getOrNull() } ?: PhoneRole.UNSET,
            serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
            myName = prefs.getString(KEY_MY_NAME, "").orEmpty(),
            registered = !prefs.getString(KEY_TOKEN, null).isNullOrBlank(),
            delivery = prefs.getString(KEY_DELIVERY, null)
                ?.let { runCatching { AlertDelivery.valueOf(it) }.getOrNull() }
                ?: AlertDelivery.APP_THEN_SMS,
            ackTimeoutSeconds = prefs.getInt(KEY_ACK_TIMEOUT, 120),
            pushAvailable = isFirebaseConfigured()
        )
    }

    private val token: String? get() = prefs.getString(KEY_TOKEN, null)

    private fun api(): CloudApi? {
        val url = _state.value.serverUrl
        return if (url.isBlank()) null else CloudApi(url, token)
    }

    /** У старых установок роли нет: если профиль уже заполнен, это телефон с браслетом. */
    fun migrateRole(profileCompleted: Boolean) {
        if (_state.value.role == PhoneRole.UNSET && profileCompleted) setRole(PhoneRole.WEARER)
    }

    fun setRole(role: PhoneRole) {
        if (role == _state.value.role) return
        // Регистрация на сервере привязана к роли, поэтому при смене роли начинаем заново.
        prefs.edit().putString(KEY_ROLE, role.name).remove(KEY_TOKEN).apply()
        _state.value = _state.value.copy(role = role, registered = false, links = emptyList(), inbox = emptyList())
    }

    fun updateDelivery(delivery: AlertDelivery) {
        prefs.edit().putString(KEY_DELIVERY, delivery.name).apply()
        _state.value = _state.value.copy(delivery = delivery)
    }

    fun updateAckTimeout(seconds: Int) {
        prefs.edit().putInt(KEY_ACK_TIMEOUT, seconds).apply()
        _state.value = _state.value.copy(ackTimeoutSeconds = seconds)
    }

    /**
     * Сохраняет адрес сервера и регистрирует телефон. Новый адрес — новая
     * регистрация (на другом сервере этого телефона нет).
     */
    suspend fun connect(serverUrl: String, myName: String? = null): Result<Unit> = runCatching {
        val url = normalizeUrl(serverUrl)
        val current = _state.value
        val name = (myName ?: current.myName).trim()
        if (url != current.serverUrl) {
            prefs.edit().putString(KEY_SERVER_URL, url).remove(KEY_TOKEN).apply()
        }
        if (myName != null) prefs.edit().putString(KEY_MY_NAME, name).apply()
        _state.value = current.copy(serverUrl = url, myName = name, registered = !token.isNullOrBlank())

        CloudApi(url, null).health()
        ensureRegistered()
        try {
            if (myName != null) requireApi().updateMe(name = name)
            _state.value = _state.value.copy(links = requireApi().links(), online = true)
        } catch (e: CloudException) {
            if (e.code != 401) throw e
            // Сервер не знает этот телефон (например, базу удалили): регистрируемся заново.
            prefs.edit().remove(KEY_TOKEN).apply()
            ensureRegistered()
            _state.value = _state.value.copy(links = requireApi().links(), online = true)
        }
    }.onFailure {
        _state.value = _state.value.copy(online = false)
        Log.w(TAG, "connect failed", it)
    }

    private suspend fun ensureRegistered() {
        if (!token.isNullOrBlank()) return
        val s = _state.value
        val role = when (s.role) {
            PhoneRole.CAREGIVER -> "caregiver"
            PhoneRole.WEARER -> "wearer"
            PhoneRole.UNSET -> throw IllegalStateException("role is not chosen")
        }
        val name = (if (s.role == PhoneRole.WEARER) wearerNameProvider() else s.myName).trim().ifBlank { "Korgan" }
        val newToken = CloudApi(s.serverUrl, null).register(role, name, fetchPushToken())
        prefs.edit().putString(KEY_TOKEN, newToken).apply()
        _state.value = _state.value.copy(registered = true)
    }

    suspend fun refreshLinks(): Result<List<FamilyLink>> = runCatching {
        val links = requireApi().links()
        _state.value = _state.value.copy(links = links, online = true)
        links
    }.onFailure { markOffline(it) }

    suspend fun requestPairCode(): Result<String> = runCatching { requireApi().newPairCode() }
        .onFailure { markOffline(it) }

    suspend fun joinWithCode(code: String): Result<FamilyLink> = runCatching {
        val link = requireApi().joinPair(code.trim())
        refreshLinks()
        link
    }

    suspend fun removeLink(deviceId: String): Result<Unit> = runCatching {
        requireApi().removeLink(deviceId)
        refreshLinks()
        Unit
    }

    /** Отправляет тревогу родственникам. null — сервер недоступен или телефон не связан. */
    suspend fun sendAlert(
        kind: String,
        title: String,
        body: String,
        latitude: Double?,
        longitude: Double?,
        accuracy: Float?,
        address: String,
        battery: Int?
    ): SentAlert? {
        val s = _state.value
        if (s.role != PhoneRole.WEARER || !s.registered || s.serverUrl.isBlank()) return null
        return runCatching {
            requireApi().sendAlert(kind, title, body, latitude, longitude, accuracy, address, battery)
        }.onSuccess { sent ->
            _liveAlert.value = LiveAlert(sent.id, sent.linked)
            _state.value = _state.value.copy(online = true)
        }.onFailure { markOffline(it) }.getOrNull()
    }

    /**
     * Ждёт ответа «Я еду». Опрашивает сервер раз в 3 с; push с ответом
     * приходит в [onAckPush] и завершает ожидание сразу.
     */
    suspend fun waitForAck(alertId: String, timeoutMs: Long): String? = withTimeoutOrNull(timeoutMs) {
        var name: String? = null
        while (name == null) {
            name = _liveAlert.value?.takeIf { it.id == alertId }?.ackedByName
                ?: runCatching { requireApi().alert(alertId) }.getOrNull()?.ackedByName
            if (name == null) {
                withTimeoutOrNull(3_000) {
                    _liveAlert.first { it?.id == alertId && it.ackedByName != null }
                }
            }
        }
        onAckPush(alertId, name)
        name
    }

    fun clearLiveAlert() {
        _liveAlert.value = null
    }

    fun onAckPush(alertId: String, name: String) {
        val live = _liveAlert.value
        if (live != null && live.id == alertId) _liveAlert.value = live.copy(ackedByName = name)
    }

    /** Родственник: забирает последние тревоги (нужно и без push, пока приложение открыто). */
    suspend fun refreshInbox(): Result<List<RemoteAlert>> = runCatching {
        val inbox = requireApi().alerts()
        _state.value = _state.value.copy(inbox = inbox, online = true)
        inbox
    }.onFailure { markOffline(it) }

    suspend fun ack(alertId: String): Result<RemoteAlert> = runCatching {
        val acked = requireApi().ack(alertId)
        _state.value = _state.value.copy(
            inbox = _state.value.inbox.map { if (it.id == alertId) acked else it }
        )
        acked
    }

    /** Новый FCM-токен: сообщаем серверу, если телефон уже зарегистрирован. */
    fun onNewPushToken(pushToken: String) {
        if (token.isNullOrBlank()) return
        scope.launch { runCatching { api()?.updateMe(pushToken = pushToken) } }
    }

    /** При старте приложения: обновить токен push и список связей. */
    fun syncInBackground() {
        val s = _state.value
        if (!s.registered || s.serverUrl.isBlank()) return
        scope.launch {
            fetchPushToken()?.let { runCatching { api()?.updateMe(pushToken = it) } }
            if (s.role == PhoneRole.WEARER) {
                runCatching { api()?.updateMe(name = wearerNameProvider().trim().ifBlank { null }) }
            }
            refreshLinks()
        }
    }

    private fun requireApi(): CloudApi =
        api()?.takeIf { !token.isNullOrBlank() } ?: throw CloudException(0, "not connected")

    private fun markOffline(error: Throwable) {
        if (error !is CloudException || error.code == 0) {
            _state.value = _state.value.copy(online = false)
        }
        Log.w(TAG, "cloud call failed: ${error.message}")
    }

    private fun isFirebaseConfigured(): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private suspend fun fetchPushToken(): String? {
        if (!isFirebaseConfigured()) return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener {
                        Log.w(TAG, "FCM token unavailable", it)
                        cont.resume(null)
                    }
            }
        }
    }

    companion object {
        private const val TAG = "FamilyCloud"
        private const val KEY_ROLE = "role"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_MY_NAME = "my_name"
        private const val KEY_TOKEN = "token"
        private const val KEY_DELIVERY = "delivery"
        private const val KEY_ACK_TIMEOUT = "ack_timeout_s"

        fun normalizeUrl(raw: String): String {
            val url = raw.trim().trimEnd('/')
            if (url.isEmpty()) return url
            return if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
        }
    }
}
