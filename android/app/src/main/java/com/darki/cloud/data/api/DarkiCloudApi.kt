package com.darki.cloud.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DarkiCloudApi(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val baseUrl = baseUrl.trimEnd('/').toHttpUrl()

    suspend fun getMe(token: String): JSONObject = get("/api/v1/me", token)

    suspend fun getRootFolder(token: String): JSONObject = get("/api/v1/folders/root", token)

    suspend fun registerDevice(token: String, deviceName: String, platform: String): JSONObject =
        postJson("/api/v1/devices", token, JSONObject().apply {
            put("deviceName", deviceName)
            put("platform", platform)
        })

    suspend fun pullChanges(token: String, deviceId: String, cursor: String, limit: Int = 100): JSONObject =
        get("/api/v1/sync/pull", token, mapOf(
            "deviceId" to deviceId,
            "cursor" to cursor,
            "limit" to limit.toString(),
        ))

    suspend fun getFolder(token: String, folderId: String): JSONObject =
        get("/api/v1/folders/$folderId", token)

    private suspend fun get(path: String, token: String, query: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val urlBuilder = baseUrl.newBuilder().addPathSegments(path.removePrefix("/"))
            query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
            execute(Request.Builder().url(urlBuilder.build()).get().bearer(token).build())
        }

    private suspend fun postJson(path: String, token: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val requestBody = body.toString().toRequestBody("application/json".toMediaType())
            execute(Request.Builder().url(baseUrl.newBuilder().addPathSegments(path.removePrefix("/")).build())
                .post(requestBody).bearer(token).build())
        }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw DarkiCloudApiException(response.code, text)
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun Request.Builder.bearer(token: String): Request.Builder =
        header("Authorization", "Bearer $token")
}

class DarkiCloudApiException(val statusCode: Int, responseBody: String) :
    IllegalStateException("DARKI Cloud request failed ($statusCode): $responseBody")
