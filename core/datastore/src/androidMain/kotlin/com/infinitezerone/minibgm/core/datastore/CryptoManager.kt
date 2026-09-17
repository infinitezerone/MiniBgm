package com.infinitezerone.minibgm.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 加解密抽象接口，隔离底层实现以支持 JVM/Host 单元测试 */
interface CryptoManager {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AndroidKeyStore 硬件密钥 + AES-256-GCM。
 *
 * 密钥不可导出、不随备份迁移，因此即使密文文件被恢复到别的设备也无法解密，
 * 泄漏面收敛为"本机 + 本 app 沙箱"。
 * 输出格式：IV(12B) || ciphertext+tag。
 */
class AndroidCryptoManager : CryptoManager {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val ciphertext = cipher.doFinal(plain)
        return cipher.iv + ciphertext
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE_BYTES) { "加密数据长度非法" }
        val iv = blob.copyOf(IV_SIZE_BYTES)
        val ciphertext = blob.copyOfRange(IV_SIZE_BYTES, blob.size)
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: createKey()

    private fun createKey(): SecretKey =
        KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .build(),
                )
            }.generateKey()

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "minibgm_auth_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE_BYTES = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
