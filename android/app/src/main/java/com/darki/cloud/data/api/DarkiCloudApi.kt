package com.darki.cloud.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class DarkiCloudApi(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val baseUrl = baseUrl.trimEnd('/').toHttpUrl()

    fun telegramLoginUrl(): String = baseUrl.newBuilder().addPathSegments("api/v1/auth/telegram/start").build().toString()
    suspend fun exchangeTelegramLogin(code: String): JSONObject = postJson("/api/v1/auth/telegram/exchange", null, JSONObject().put("code", code))
    suspend fun logout(token: String) { postJson("/api/v1/auth/logout", token, JSONObject()) }
    suspend fun getMe(token: String): JSONObject = get("/api/v1/me", token)
    suspend fun getRootFolder(token: String): JSONObject = get("/api/v1/folders/root", token)
    suspend fun registerDevice(token: String, deviceName: String, platform: String): JSONObject =
        postJson("/api/v1/devices", token, JSONObject().apply { put("deviceName", deviceName); put("platform", platform) })
    suspend fun pullChanges(token: String, deviceId: String, cursor: String, limit: Int = 100): JSONObject =
        get("/api/v1/sync/pull", token, mapOf("deviceId" to deviceId, "cursor" to cursor, "limit" to limit.toString()))
    suspend fun acknowledgeSync(token: String, deviceId: String, cursor: String) {
        postJson("/api/v1/sync/ack", token, JSONObject().apply { put("deviceId", deviceId); put("cursor", cursor) })
    }
    suspend fun getFolder(token: String, folderId: String): JSONObject = get("/api/v1/folders/$folderId", token)

    suspend fun createFolder(token: String, parentId: String, name: String, deviceId: String): JSONObject =
        postJson("/api/v1/folders", token, JSONObject().apply { put("parentId", parentId); put("name", name); put("deviceId", deviceId) })

    suspend fun renameFolder(token: String, folderId: String, name: String, deviceId: String): JSONObject =
        patchJson("/api/v1/folders/$folderId", token, JSONObject().apply { put("name", name); put("deviceId", deviceId) })

    suspend fun moveFolder(token: String, folderId: String, parentId: String, deviceId: String): JSONObject =
        patchJson("/api/v1/folders/$folderId", token, JSONObject().apply { put("parentId", parentId); put("deviceId", deviceId) })

    suspend fun renameFile(token: String, fileId: String, name: String, deviceId: String): JSONObject =
        patchJson("/api/v1/files/$fileId", token, JSONObject().apply { put("name", name); put("deviceId", deviceId) })

    suspend fun moveFile(token: String, fileId: String, folderId: String, deviceId: String): JSONObject =
        patchJson("/api/v1/files/$fileId", token, JSONObject().apply { put("folderId", folderId); put("deviceId", deviceId) })

    suspend fun deleteFile(token: String, fileId: String, deviceId: String): JSONObject =
        delete("/api/v1/files/$fileId", token, mapOf("deviceId" to deviceId))

    suspend fun restoreFile(token: String, fileId: String, deviceId: String): JSONObject =
        postJson("/api/v1/files/$fileId/restore", token, JSONObject().put("deviceId", deviceId))

    suspend fun uploadFile(token: String, folderId: String, name: String, mimeType: String?, sizeBytes: Long, deviceId: String, input: InputStream): JSONObject = withContext(Dispatchers.IO) {
        val body = object : RequestBody() {
            override fun contentType() = (mimeType ?: "application/octet-stream").toMediaType()
            override fun contentLength() = sizeBytes
            override fun writeTo(sink: okio.BufferedSink) {
                input.use { source -> source.copyTo(sink.outputStream()) }
            }
        }
        val url = baseUrl.newBuilder().addPathSegments("api/v1/files").apply {
            addQueryParameter("folderId", folderId)
            addQueryParameter("name", name)
        }.build()
        execute(Request.Builder().url(url).post(body).header("X-Device-Id", deviceId).bearer(token).build())
    }

    suspend fun downloadFile(token: String, fileId: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.newBuilder().addPathSegments("api/v1/files/$fileId/content").build()).get().bearer(token).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DarkiCloudApiException(response.code, response.body?.string().orEmpty())
            response.body?.byteStream()?.use { input -> input.copyTo(output) } ?: error("Empty file response")
        }
    }

    private suspend fun get(path: String, token: String, query: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val urlBuilder = baseUrl.newBuilder().addPathSegments(path.removePrefix("/"))
        query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        execute(Request.Builder().url(urlBuilder.build()).get().apply { bearer(token) }.build())
    }

    private suspend fun postJson(path: String, token: String?, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val requestBody = body.toString().toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url(baseUrl.newBuilder().addPathSegments(path.removePrefix("/")).build()).post(requestBody)
        if (token != null) builder.bearer(token)
        execute(builder.build())
    }

    private suspend fun patchJson(path: String, token: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val requestBody = body.toString().toRequestBody("application/json".toMediaType())
        execute(Request.Builder().url(baseUrl.newBuilder().addPathSegments(path.removePrefix("/")).build()).patch(requestBody).bearer(token).build())
    }

    private suspend fun delete(path: String, token: String, query: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val urlBuilder = baseUrl.newBuilder().addPathSegments(path.removePrefix("/"))
        query.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        execute(Request.Builder().url(urlBuilder.build()).delete().bearer(token).build())
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw DarkiCloudApiException(response.code, text)
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun Request.Builder.bearer(token: String): Request.Builder = header("Authorization", "Bearer $token")
}

class DarkiCloudApiException(val statusCode: Int, responseBody: String) : IllegalStateException("DARKI Cloud request failed ($statusCode): $responseBody")
