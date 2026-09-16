package com.darki.cloud.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("darki_cloud_session", Context.MODE_PRIVATE)

    var token: String?
        get() = readEncryptedToken()
        set(value) {
            if (value == null) {
                preferences.edit().remove(KEY_TOKEN).remove(KEY_TOKEN_IV).apply()
            } else {
                writeEncryptedToken(value)
            }
        }

    var userId: String?
        get() = preferences.getString(KEY_USER_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_USER_ID) else putString(KEY_USER_ID, value)
        }.apply()

    var rootFolderId: String?
        get() = preferences.getString(KEY_ROOT_FOLDER_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_ROOT_FOLDER_ID) else putString(KEY_ROOT_FOLDER_ID, value)
        }.apply()

    var deviceId: String?
        get() = preferences.getString(KEY_DEVICE_ID, null)
        set(value) = preferences.edit().apply {
            if (value == null) remove(KEY_DEVICE_ID) else putString(KEY_DEVICE_ID, value)
        }.apply()

    var cursor: String
        get() = preferences.getString(KEY_CURSOR, "0") ?: "0"
        set(value) = preferences.edit().putString(KEY_CURSOR, value).apply()

    fun clear() = preferences.edit().clear().apply()

    private fun readEncryptedToken(): String? {
        val encrypted = preferences.getString(KEY_TOKEN, null) ?: return null
        val iv = preferences.getString(KEY_TOKEN_IV, null)
        if (iv == null) {
            // Migrate the legacy plaintext token immediately into Keystore-backed storage.
            writeEncryptedToken(encrypted)
            return encrypted
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(TAG_LENGTH_BITS, android.util.Base64.decode(iv, android.util.Base64.NO_WRAP)))
            String(cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun writeEncryptedToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val ciphertext = android.util.Base64.encodeToString(cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP)
        val iv = android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP)
        preferences.edit()
            .putString(KEY_TOKEN, ciphertext)
            .putString(KEY_TOKEN_IV, iv)
            .apply()
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "darki_cloud_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_IV = "token_iv"
        const val KEY_USER_ID = "user_id"
        const val KEY_ROOT_FOLDER_ID = "root_folder_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_CURSOR = "sync_cursor"
    }
}
