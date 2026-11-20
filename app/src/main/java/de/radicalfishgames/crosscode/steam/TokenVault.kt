package de.radicalfishgames.crosscode.steam

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import java.util.Base64
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM sealing of short strings. The output is "v1:" + Base64(IV + ciphertext with tag).
 * [key] supplies the key; on the device that is [KeystoreKey], in tests a plain AES key.
 */
class SecretBox(
    private val key: () -> SecretKey,
) {
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // No IV passed: the provider picks a fresh random one (the Keystore insists on that)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + body)
    }

    /** Why [open] gave no plain text. */
    sealed class Opened {
        data class Ok(
            val plain: String,
        ) : Opened()

        /** The value is malformed, or the key can never open it (e.g. a new key): it is useless. */
        object Unusable : Opened()

        /** Opening failed for another reason (e.g. the Keystore was busy); it may work next time. */
        data class Failed(
            val error: Exception,
        ) : Opened()
    }

    /** The plain text, or null if [sealed] can't be opened; see [openResult] for why. */
    fun open(sealed: String): String? = (openResult(sealed) as? Opened.Ok)?.plain

    fun openResult(sealed: String): Opened {
        if (!sealed.startsWith(PREFIX)) return Opened.Unusable
        val bytes =
            try {
                Base64.getDecoder().decode(sealed.removePrefix(PREFIX))
            } catch (e: IllegalArgumentException) {
                return Opened.Unusable
            }
        if (bytes.size <= IV_BYTES) return Opened.Unusable
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            Opened.Ok(String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8))
        } catch (e: Exception) {
            if (isPermanent(e)) Opened.Unusable else Opened.Failed(e)
        }
    }

    /** Errors after which this value can never be opened with this key. */
    private fun isPermanent(e: Exception): Boolean =
        e is AEADBadTagException ||
            e is UnrecoverableKeyException ||
            e is KeyPermanentlyInvalidatedException ||
            e.cause is UnrecoverableKeyException ||
            e.cause is KeyPermanentlyInvalidatedException

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "v1:"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/** An AES key that lives in the AndroidKeyStore and never leaves it. */
object KeystoreKey {
    private const val ALIAS = "crossandroid_steam_token"

    fun getOrCreate(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

/**
 * The Steam refresh token, kept encrypted in [prefs] under a Keystore key. A plaintext token left
 * by an older build is encrypted on first read and the plaintext entry deleted once the sealed one
 * is written. If the stored
 * token can't be decrypted (the key is gone, e.g. after a restore), it counts as signed out.
 * The value is cached, since the JS bridge and the UI ask often. Thread-safe.
 */
class TokenVault(
    private val prefs: SharedPreferences,
    private val box: SecretBox = SecretBox(KeystoreKey::getOrCreate),
) {
    private var loaded = false
    private var cached: String? = null

    @Synchronized
    fun get(): String? {
        if (loaded) return cached
        loaded = true
        val sealed = prefs.getString(KEY_SEALED, null)
        if (sealed != null) {
            when (val opened = box.openResult(sealed)) {
                is SecretBox.Opened.Ok -> {
                    cached = opened.plain
                    prefs.edit { remove(KEY_PLAIN) }
                }
                SecretBox.Opened.Unusable -> {
                    Log.w(TAG, "Saved Steam sign-in can't be decrypted; signing out")
                    cached = null
                    prefs.edit {
                        remove(KEY_SEALED)
                        remove(KEY_PLAIN)
                    }
                }
                is SecretBox.Opened.Failed -> {
                    // Maybe a passing Keystore problem: signed out for this run, the token is kept
                    Log.w(TAG, "Can't decrypt the Steam sign-in now (${opened.error.javaClass.simpleName}); keeping it for next time")
                    cached = null
                }
            }
            return cached
        }
        val plain = prefs.getString(KEY_PLAIN, null) ?: return null
        cached = plain
        store(plain, migrating = true)
        return cached
    }

    @Synchronized
    fun set(token: String) {
        loaded = true
        cached = token
        store(token, migrating = false)
    }

    @Synchronized
    fun clear() {
        loaded = true
        cached = null
        prefs.edit {
            remove(KEY_SEALED)
            remove(KEY_PLAIN)
        }
    }

    /** [migrating]: [token] is an older build's plaintext entry, kept until a sealed copy is written. */
    private fun store(
        token: String,
        migrating: Boolean,
    ) {
        val sealed =
            try {
                box.seal(token)
            } catch (e: Exception) {
                // Never fall back to plaintext; the token then lasts for this run only
                Log.e(TAG, "Can't encrypt the Steam sign-in (${e.javaClass.simpleName}); not saving it")
                null
            }
        if (sealed == null) {
            // A plaintext being migrated stays, so the migration is tried again next run; a new
            // token replaces the old entries, which must not come back on the next run
            if (!migrating) {
                prefs.edit {
                    remove(KEY_SEALED)
                    remove(KEY_PLAIN)
                }
            }
            return
        }
        // The plaintext goes only together with (after) a written sealed value
        prefs.edit(commit = true) {
            putString(KEY_SEALED, sealed)
            remove(KEY_PLAIN)
        }
    }

    companion object {
        private const val TAG = "CrossAndroidSteam"
        const val KEY_PLAIN = "refresh_token"
        const val KEY_SEALED = "refresh_token_sealed"
    }
}
