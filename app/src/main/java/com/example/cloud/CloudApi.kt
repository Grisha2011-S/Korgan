package com.example.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudException(val code: Int, message: String) : Exception(message)

data class FamilyLink(val deviceId: String, val name: String, val hasPush: Boolean)

data class RemoteAlert(
    val id: String,
    val wearerName: String,
    val kind: String,
    val title: String,
    val body: String,
    val latitude: Double?,
    val longitude: Double?,
    val address: String,
    val battery: Int?,
    val createdMs: Long,
    val ackedByName: String?
)

data class SentAlert(val id: String, val linked: Int, val pushed: Int)

class CloudApi(private val baseUrl: String, private val token: String?) {

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun health(): Boolean = call("GET", "/health", auth = false).let {
        (it as JSONObject).optBoolean("ok")
    }

    suspend fun register(role: String, name: String, pushToken: String?): String {
        val body = JSONObject()
            .put("role", role)
            .put("name", name)
            .put("platform", "android")
        if (pushToken != null) body.put("push_token", pushToken)
        return (call("POST", "/api/register", body, auth = false) as JSONObject).getString("token")
    }

    suspend fun updateMe(name: String? = null, pushToken: String? = null) {
        val body = JSONObject()
        if (name != null) body.put("name", name)
        if (pushToken != null) body.put("push_token", pushToken)
        call("PATCH", "/api/me", body)
    }

    suspend fun newPairCode(): String =
        (call("POST", "/api/pair/code", JSONObject()) as JSONObject).getString("code")

    suspend fun joinPair(code: String): FamilyLink =
        parseLink(call("POST", "/api/pair/join", JSONObject().put("code", code)) as JSONObject)

    suspend fun links(): List<FamilyLink> {
        val array = call("GET", "/api/links") as JSONArray
        return (0 until array.length()).map { parseLink(array.getJSONObject(it)) }
    }

    suspend fun removeLink(deviceId: String) {
        call("DELETE", "/api/links/$deviceId")
    }

    suspend fun sendAlert(
        kind: String,
        title: String,
        body: String,
        latitude: Double?,
        longitude: Double?,
        accuracy: Float?,
        address: String,
        battery: Int?
    ): SentAlert {
        val payload = JSONObject()
            .put("kind", kind)
            .put("title", title.take(120))
            .put("body", body.take(1000))
            .put("address", address.take(300))
        if (latitude != null && longitude != null) {
            payload.put("latitude", latitude).put("longitude", longitude)
        }
        if (accuracy != null) payload.put("accuracy", accuracy.toDouble())
        if (battery != null && battery >= 0) payload.put("battery", battery)
        val result = call("POST", "/api/alerts", payload) as JSONObject
        return SentAlert(
            id = result.getString("alert_id"),
            linked = result.optInt("linked"),
            pushed = result.optInt("pushed")
        )
    }

    suspend fun alerts(sinceSeconds: Double = 0.0): List<RemoteAlert> {
        val array = call("GET", "/api/alerts?since=$sinceSeconds") as JSONArray
        return (0 until array.length()).map { parseAlert(array.getJSONObject(it)) }
    }

    suspend fun alert(id: String): RemoteAlert = parseAlert(call("GET", "/api/alerts/$id") as JSONObject)

    suspend fun ack(id: String): RemoteAlert =
        parseAlert(call("POST", "/api/alerts/$id/ack", JSONObject()) as JSONObject)

    private suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true
    ): Any = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + path)
        if (auth) {
            if (token.isNullOrBlank()) throw CloudException(401, "not registered")
            builder.header("Authorization", "Bearer $token")
        }
        val requestBody = body?.toString()?.toRequestBody(json)
        builder.method(method, requestBody ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(json))

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw CloudException(response.code, detail?.ifBlank { null } ?: "HTTP ${response.code}")
            }
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text.ifBlank { "{}" })
        }
    }

    private fun parseLink(o: JSONObject) = FamilyLink(
        deviceId = o.getString("device_id"),
        name = o.optString("name"),
        hasPush = o.optBoolean("has_push")
    )

    private fun parseAlert(o: JSONObject) = RemoteAlert(
        id = o.getString("alert_id"),
        wearerName = o.optString("wearer_name"),
        kind = o.optString("kind"),
        title = o.optString("title"),
        body = o.optString("body"),
        latitude = o.optDoubleOrNull("latitude"),
        longitude = o.optDoubleOrNull("longitude"),
        address = o.optString("address"),
        battery = if (o.isNull("battery")) null else o.optInt("battery"),
        createdMs = (o.optDouble("created") * 1000).toLong(),
        ackedByName = if (o.isNull("acked_by_name")) null else o.optString("acked_by_name")
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
