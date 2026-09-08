package com.bizard.homesmokeremote

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore on API23+, explicit plaintext fallback for Android 5.0/5.1. */
internal class SecretStore(c: Context?) {
    private val p: SharedPreferences?
    val isEncrypted: Boolean
        get() {
            return Build.VERSION.SDK_INT >= 23
        }

    init {
        p = c!!.getSharedPreferences("homesmoke_remote", Context.MODE_PRIVATE)
        migrate()
    }

    fun put(value: String?) {
        var value = value
        value = if (value == null) "" else value
        if (Build.VERSION.SDK_INT < 23) {
            p!!.edit()!!.putString(PLAIN, value)!!.apply()
            return
        }
        try {
            val c: Cipher? = Cipher.getInstance("AES/GCM/NoPadding")
            c!!.init(Cipher.ENCRYPT_MODE, key())
            val packed: String? =
                Base64.encodeToString(c!!.getIV(), Base64.NO_WRAP) +
                    ":" +
                    Base64.encodeToString(
                        c!!.doFinal(value!!.toByteArray(StandardCharsets.UTF_8)),
                        Base64.NO_WRAP,
                    )
            p!!.edit()!!.putString(ENC, packed)!!.remove(PLAIN)!!.remove("pass")!!.apply()
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    fun get(): String? {
        if (Build.VERSION.SDK_INT < 23) return p!!.getString(PLAIN, p!!.getString("pass", ""))
        val packed: String? = p!!.getString(ENC, "")
        if (packed!!.isEmpty()) return ""
        try {
            val x: Array<String>? = packed!!.split((":").toRegex(), 2).toTypedArray()
            val c: Cipher? = Cipher.getInstance("AES/GCM/NoPadding")
            c!!.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(x!![0], Base64.NO_WRAP)),
            )
            return String(
                c!!.doFinal(Base64.decode(x!![1], Base64.NO_WRAP)),
                StandardCharsets.UTF_8,
            )
        } catch (e: Exception) {
            return ""
        }
    }

    private fun migrate() {
        val old: String? = p!!.getString("pass", "")
        if (old != null && !old!!.isEmpty()) {
            try {
                put(old)
            } catch (ignored: Exception) {}
        }
    }

    @Throws(Exception::class)
    private fun key(): SecretKey? {
        val ks: KeyStore? = KeyStore.getInstance("AndroidKeyStore")
        ks!!.load(null)
        val e: KeyStore.Entry? = ks!!.getEntry(ALIAS, null)
        if (e is KeyStore.SecretKeyEntry) return (e as KeyStore.SecretKeyEntry).getSecretKey()
        val g: KeyGenerator? =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g!!.init(
            KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)!!
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)!!
                .build()
        )
        return g!!.generateKey()
    }

    companion object {
        private val ALIAS: String = "HomeSmokeRemoteMqttV1"
        private val ENC: String = "mqtt_pass_enc"
        private val PLAIN: String = "mqtt_pass_legacy"
    }
}
