package io.embrace.android.embracesdk.internal.instrumentation.network

import android.util.Base64
import java.io.IOException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

/**
 * API to encrypt/decrypt data
 */
class NetworkCaptureEncryptionManager {

    private val transformation = "RSA/ECB/PKCS1Padding"

    /**
     * @return encrypted data in Base64 String or null if any error occur.
     */
    fun encrypt(data: String, keyText: String): String? {
        return try {
            val publicKey = getKeyFromText(keyText)
            if (publicKey != null) {
                encrypt(data, publicKey)
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * @return encrypted data in Base64 String or null if any error occur.
     */
    private fun encrypt(data: String, key: Key): String? {
        var result: String? = ""
        try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val plainData = data.toByteArray(charset(UTF_8))
            val decodedData = decodeWithBuffer(cipher, plainData, ENCRYPT_BLOCK_SIZE)

            val encodedString = Base64.encodeToString(decodedData, Base64.DEFAULT)
            result += encodedString
        } catch (ignored: NoSuchAlgorithmException) {
        } catch (ignored: NoSuchPaddingException) {
        } catch (ignored: InvalidKeyException) {
        } catch (ignored: BadPaddingException) {
        } catch (ignored: IllegalBlockSizeException) {
        } catch (ignored: IOException) {
        }
        return result
    }

    /**
     * @param data Base64 encrypted data.
     * @return decrypted data or null if any error occur
     */
    fun decrypt(data: String, key: Key): String? {
        var result: String? = null
        try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, key)

            val decodedData: ByteArray?
            val encryptedData = Base64.decode(data, Base64.DEFAULT)
            decodedData = decodeWithBuffer(cipher, encryptedData, DECRYPT_BLOCK_SIZE)

            result = String(decodedData, charset(UTF_8))
        } catch (ignored: NoSuchAlgorithmException) {
        } catch (ignored: NoSuchPaddingException) {
        } catch (ignored: InvalidKeyException) {
        } catch (ignored: BadPaddingException) {
        } catch (ignored: IllegalBlockSizeException) {
        } catch (ignored: IOException) {
        }
        return result
    }

    private fun decodeWithBuffer(
        cipher: Cipher,
        plainData: ByteArray,
        bufferLength: Int,
    ): ByteArray {
        // string initialize 2 buffers.
        // scrambled will hold intermediate results
        var scrambled: ByteArray

        // toReturn will hold the total result
        var toReturn = ByteArray(0)

        // holds the bytes that have to be modified in one step
        var buffer =
            ByteArray((if (plainData.size > bufferLength) bufferLength else plainData.size))

        for (i in plainData.indices) {
            if ((i > 0) && (i % bufferLength == 0)) {
                // execute the operation
                scrambled = cipher.doFinal(buffer)
                // add the result to our total result.
                toReturn = append(toReturn, scrambled)
                // here we calculate the bufferLength of the next buffer required
                var newLength = bufferLength

                // if newLength would be longer than remaining bytes in the bytes array we shorten it.
                if (i + bufferLength > plainData.size) {
                    newLength = plainData.size - i
                }
                // clean the buffer array
                buffer = ByteArray(newLength)
            }
            // copy byte into our buffer.
            buffer[i % bufferLength] = plainData[i]
        }

        // this step is needed if we had a trailing buffer. should only happen when encrypting.
        // example: we encrypt 110 bytes. 100 bytes per run means we "forgot" the last 10 bytes. they are in the buffer array
        scrambled = cipher.doFinal(buffer)

        // final step before we can return the modified data.
        toReturn = append(toReturn, scrambled)
        return toReturn
    }

    private fun append(prefix: ByteArray, suffix: ByteArray): ByteArray {
        val toReturn = ByteArray(prefix.size + suffix.size)
        for (i in prefix.indices) {
            toReturn[i] = prefix[i]
        }
        for (i in suffix.indices) {
            toReturn[i + prefix.size] = suffix[i]
        }
        return toReturn
    }

    private fun getKeyFromText(keyText: String?): Key? {
        try {
            val encodedKeySpec = X509EncodedKeySpec(Base64.decode(keyText, Base64.DEFAULT))
            return KeyFactory.getInstance("RSA").generatePublic(encodedKeySpec)
        } catch (ignored: InvalidKeySpecException) {
        } catch (ignored: NoSuchAlgorithmException) {
        }

        return null
    }

    companion object {
        private const val UTF_8 = "UTF-8"
        private const val ENCRYPT_BLOCK_SIZE = 245
        private const val DECRYPT_BLOCK_SIZE = 256
    }
}
