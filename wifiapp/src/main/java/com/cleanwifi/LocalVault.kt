package com.cleanwifi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local-only encrypted Wi-Fi credential vault.
 * No network calls or automatic sharing are performed by this class.
 */
class LocalVault(context: Context) {
    private val prefs = context.getSharedPreferences("cleanwifi_private_vault", Context.MODE_PRIVATE)
    private val alias = "cleanwifi_local_vault_v1"

    data class Entry(val ssid: String, val password: String)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > 12) { "Invalid vault data" }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    @Synchronized
    fun list(): List<Entry> {
        val stored = prefs.getString("records", null) ?: return emptyList()
        val array = JSONArray(decrypt(stored))
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            Entry(item.optString("ssid"), item.optString("password"))
        }
    }

    @Synchronized
    fun save(ssid: String, password: String) {
        require(ssid.isNotBlank()) { "Wi-Fi 名称不能为空" }
        val entries = list().filterNot { it.ssid == ssid } + Entry(ssid.trim(), password)
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().put("ssid", it.ssid).put("password", it.password))
        }
        prefs.edit().putString("records", encrypt(array.toString())).apply()
    }

    @Synchronized
    fun delete(ssid: String) {
        val array = JSONArray()
        list().filterNot { it.ssid == ssid }.forEach {
            array.put(JSONObject().put("ssid", it.ssid).put("password", it.password))
        }
        if (array.length() == 0) prefs.edit().remove("records").apply()
        else prefs.edit().putString("records", encrypt(array.toString())).apply()
    }
}
