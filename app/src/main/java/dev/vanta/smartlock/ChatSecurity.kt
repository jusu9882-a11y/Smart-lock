package dev.vanta.smartlock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * VANTA's first application-layer security primitive.
 *
 * A long-lived P-256 identity key is wrapped by an Android Keystore AES key.
 * Peers derive a shared AES-256 key with ECDH; each message uses AES-GCM.
 * Exchange public keys only over an authenticated bonded Bluetooth session
 * and compare the displayed fingerprints before trusting a new peer.
 */
class ChatSecurity(context: Context) {
    private val prefs = context.getSharedPreferences("vanta_identity", Context.MODE_PRIVATE)
    private val alias = "vanta_local_wrap_key"
    private val random = SecureRandom()
    private val aad = "VANTA-MESH-v1".toByteArray(Charsets.UTF_8)

    private fun wrappingKey(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun localCrypt(mode: Int, input: ByteArray): ByteArray {
        val key = wrappingKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(mode, key)
            cipher.updateAAD("VANTA-LOCAL-v1".toByteArray(Charsets.UTF_8))
            return cipher.iv + cipher.doFinal(input)
        }
        require(input.size > 12) { "Invalid encrypted local data" }
        cipher.init(mode, key, GCMParameterSpec(128, input.copyOfRange(0, 12)))
        cipher.updateAAD("VANTA-LOCAL-v1".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(input.copyOfRange(12, input.size))
    }

    fun encryptAtRest(plain: ByteArray): ByteArray = localCrypt(Cipher.ENCRYPT_MODE, plain)
    fun decryptAtRest(ciphertext: ByteArray): ByteArray = localCrypt(Cipher.DECRYPT_MODE, ciphertext)

    @Synchronized fun identity(): KeyPair {
        val pub = prefs.getString("public", null)
        val wrapped = prefs.getString("private_wrapped", null)
        if (pub != null && wrapped != null) {
            val publicBytes = Base64.decode(pub, Base64.NO_WRAP)
            val privateBytes = localCrypt(Cipher.DECRYPT_MODE, Base64.decode(wrapped, Base64.NO_WRAP))
            val factory = KeyFactory.getInstance("EC")
            return KeyPair(factory.generatePublic(X509EncodedKeySpec(publicBytes)),
                factory.generatePrivate(PKCS8EncodedKeySpec(privateBytes)))
        }
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        val encryptedPrivate = localCrypt(Cipher.ENCRYPT_MODE, pair.private.encoded)
        prefs.edit()
            .putString("public", Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .putString("private_wrapped", Base64.encodeToString(encryptedPrivate, Base64.NO_WRAP))
            .apply()
        return pair
    }

    fun publicIdentity(): ByteArray = identity().public.encoded

    fun fingerprint(encodedPublicKey: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
            .take(16).joinToString(":") { "%02X".format(it.toInt() and 0xff) }

    fun deriveSessionKey(peerPublicEncoded: ByteArray): ByteArray {
        val own = identity()
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPublicEncoded))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own.private)
        agreement.doPhase(peer, true)
        val shared = agreement.generateSecret()
        val first = listOf(own.public.encoded, peerPublicEncoded).sortedWith { a, b ->
            for (i in 0 until minOf(a.size, b.size)) {
                val cmp = (a[i].toInt() and 255).compareTo(b[i].toInt() and 255)
                if (cmp != 0) return@sortedWith cmp
            }
            a.size.compareTo(b.size)
        }
        val salt = MessageDigest.getInstance("SHA-256").digest(first[0] + first[1])
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(shared)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        expand.update("VANTA-MESH-E2EE-v1".toByteArray(Charsets.UTF_8))
        expand.update(1.toByte())
        return expand.doFinal().copyOf(32)
    }

    fun encryptMessage(sessionKey: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    fun decryptMessage(sessionKey: ByteArray, encrypted: ByteArray): ByteArray {
        require(encrypted.size > 28) { "Invalid encrypted message" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"),
            GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
    }
}
