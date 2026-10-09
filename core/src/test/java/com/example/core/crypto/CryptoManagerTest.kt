package com.example.core.crypto

import android.util.Base64
import com.example.core.model.VaultError
import io.mockk.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class CryptoManagerTest {
    private lateinit var crypto: CryptoManager
    private var removedProviders = emptyList<Pair<java.security.Provider, Int>>()
    private val key = ByteArray(32) { it.toByte() }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    @Before
    fun setup() {
        // Robolectric installs Android crypto providers in the shared test JVM.
        // Host-JVM AES tests use the JVM providers and restore test process state afterwards.
        removedProviders =
            java.security.Security.getProviders().mapIndexedNotNull { index, provider ->
                if (provider.name == "Conscrypt" || provider.name == "BC") provider to (index + 1)
                else null
            }
        removedProviders.forEach { java.security.Security.removeProvider(it.first.name) }
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), Base64.NO_WRAP) } answers
            {
                java.util.Base64.getEncoder().encodeToString(firstArg())
            }
        every { Base64.decode(any<String>(), Base64.NO_WRAP) } answers
            {
                java.util.Base64.getDecoder().decode(firstArg<String>())
            }
        val store = mockk<KeyStore>(relaxed = true)
        every { store.containsAlias("VaultMasterKey") } returns true
        every { store.getEntry("VaultMasterKey", null) } returns
            KeyStore.SecretKeyEntry(SecretKeySpec(key, "AES"))
        mockkStatic(KeyStore::class)
        every { KeyStore.getInstance("AndroidKeyStore") } returns store
        crypto = CryptoManager()
    }

    @After
    fun cleanup() {
        unmockkAll()
        removedProviders.forEach { (provider, position) ->
            java.security.Security.insertProviderAt(provider, position)
        }
    }

    @Test
    fun fieldEncryptionRoundTripsUtf8EmptyAndLargeInputs() = runBlocking {
        listOf("", "abc", "שלום 🔐 日本語", "a".repeat(100000)).forEach { input ->
            val bytes = input.toByteArray()
            val (encrypted, iv) = crypto.encryptWithKey(bytes, key)
            assertArrayEquals(bytes, crypto.decryptWithKey(encrypted, iv, key))
            assertArrayEquals(input.toByteArray(), bytes)
            assertFalse(encrypted.contains('\n'))
            assertFalse(iv.contains('\n'))
        }
    }

    @Test
    fun arbitraryBinaryValuesRoundTrip() = runBlocking {
        val bytes = ByteArray(256) { it.toByte() }
        val (c, i) = crypto.encryptWithKey(bytes, key)
        assertArrayEquals(bytes, crypto.decryptWithKey(c, i, key))
    }

    @Test
    fun ciphertextIncludes128BitAuthenticationTag() = runBlocking {
        val (c, i) = crypto.encryptWithKey(byteArrayOf(1, 2, 3), key)
        assertEquals(19, Base64.decode(c, Base64.NO_WRAP).size)
        assertEquals(12, Base64.decode(i, Base64.NO_WRAP).size)
    }

    @Test
    fun identicalPlaintextGetsFreshIvAndCiphertext() = runBlocking {
        val a = crypto.encryptWithKey("same".toByteArray(), key)
        val b = crypto.encryptWithKey("same".toByteArray(), key)
        assertNotEquals(a.first, b.first)
        assertNotEquals(a.second, b.second)
    }

    @Test
    fun validAesKeySizesAreSupported() = runBlocking {
        for (n in listOf(16, 24, 32)) {
            val k = ByteArray(n) { 1 }
            val (c, i) = crypto.encryptWithKey(byteArrayOf(9), k)
            assertArrayEquals(byteArrayOf(9), crypto.decryptWithKey(c, i, k))
        }
    }

    @Test
    fun invalidKeySizesFailWithEncryptionError() = runBlocking {
        for (n in listOf(0, 1, 15, 17, 31, 33)) {
            val e =
                try {
                    crypto.encryptWithKey(byteArrayOf(9), ByteArray(n))
                    null
                } catch (e: VaultError.EncryptionError) {
                    e
                }
            assertNotNull("size=$n", e)
            assertNotNull(e!!.cause)
        }
    }

    private suspend fun decryptionFails(c: String, i: String, k: ByteArray = key) {
        val e =
            try {
                crypto.decryptWithKey(c, i, k)
                null
            } catch (e: VaultError.DecryptionError) {
                e
            }
        assertNotNull(e)
        assertNotNull(e!!.cause)
    }

    @Test
    fun wrongKeyCannotDecrypt() = runBlocking {
        val (c, i) = crypto.encryptWithKey(byteArrayOf(9), key)
        decryptionFails(c, i, ByteArray(32) { 99 })
    }

    @Test
    fun modifiedCiphertextIsRejected() = runBlocking {
        val (c, i) = crypto.encryptWithKey(byteArrayOf(9), key)
        val bytes = Base64.decode(c, Base64.NO_WRAP)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        decryptionFails(b64(bytes), i)
    }

    @Test
    fun modifiedTagIsRejected() = runBlocking {
        val (c, i) = crypto.encryptWithKey(byteArrayOf(9), key)
        val bytes = Base64.decode(c, Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        decryptionFails(b64(bytes), i)
    }

    @Test
    fun differentIvIsRejected() = runBlocking {
        val (c, _) = crypto.encryptWithKey(byteArrayOf(9), key)
        decryptionFails(c, b64(ByteArray(12)))
    }

    @Test
    fun truncatedCiphertextIsRejected() = runBlocking {
        val (_, i) = crypto.encryptWithKey(byteArrayOf(9), key)
        decryptionFails(b64(byteArrayOf(1, 2, 3)), i)
    }

    @Test
    fun malformedBase64AndEmptyIvAreRejected() = runBlocking {
        val (c, i) = crypto.encryptWithKey(byteArrayOf(9), key)
        decryptionFails("%%!!", i)
        decryptionFails(c, "")
        decryptionFails(c, "%%!!")
    }

    @Test
    fun encryptionAndDecryptionNeverMutateCallerKey() = runBlocking {
        val copy = key.copyOf()
        val (c, i) = crypto.encryptWithKey(byteArrayOf(9), key)
        crypto.decryptWithKey(c, i, key)
        assertArrayEquals(copy, key)
    }

    @Test
    fun wrappedKeyCanBeUnwrappedWithAuthenticatedCipher() = runBlocking {
        val encrypt =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            }
        val plain = ByteArray(32) { 8 }
        val (c, i) = crypto.wrapKey(plain, encrypt)
        val decrypt =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(128, Base64.decode(i, Base64.NO_WRAP)),
                )
            }
        assertArrayEquals(plain, crypto.unwrapKey(c, decrypt))
    }

    @Test
    fun wrapRejectsUninitializedCipher() = runBlocking {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        val e =
            try {
                crypto.wrapKey(byteArrayOf(1), c)
                null
            } catch (e: VaultError.EncryptionError) {
                e
            }
        assertNotNull(e)
    }

    @Test
    fun unwrapRejectsUninitializedCipher() = runBlocking {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        val e =
            try {
                crypto.unwrapKey(b64(byteArrayOf(1)), c)
                null
            } catch (e: VaultError.DecryptionError) {
                e
            }
        assertNotNull(e)
    }

    @Test
    fun masterEncryptionAndDecryptionCiphersRoundTripWithSuppliedTestKey() {
        val c = crypto.getMasterEncryptionCipher()
        val data = byteArrayOf(3, 4)
        val encrypted = c.doFinal(data)
        assertArrayEquals(data, crypto.getMasterDecryptionCipher(c.iv).doFinal(encrypted))
    }
}
