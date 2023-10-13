package io.embrace.android.embracesdk.network.logging;

import android.util.Base64;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;

/**
 * API to encrypt/decrypt data
 */
class NetworkCaptureEncryptionManager {

    private static final String UTF_8 = "UTF-8";
    private final String transformation = "RSA/ECB/PKCS1Padding";
    private static final int mEncryptionBlockSize = 245;
    private static final int mDecryptionBlockSize = 256;


    /**
     * @return encrypted data in Base64 String or null if any error occur.
     */
    @Nullable
    public String encrypt(@NonNull String data, @NonNull String keyText) {
        try {
            Key publicKey = getKeyFromText(keyText);
            if (publicKey != null) {
                return encrypt(data, publicKey);
            } else {
                InternalStaticEmbraceLogger.logError("wrong public key");
                return null;
            }
        } catch (Exception e) {
            InternalStaticEmbraceLogger.logError("data cannot be encrypted", e);
            return null;
        }
    }

    /**
     * @return encrypted data in Base64 String or null if any error occur.
     */
    @Nullable
    private String encrypt(@NonNull String data, @NonNull Key key) {
        String result = "";
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] plainData = data.getBytes(UTF_8);
            byte[] decodedData = decodeWithBuffer(cipher, plainData, mEncryptionBlockSize);

            String encodedString = Base64.encodeToString(decodedData, Base64.DEFAULT);
            result += encodedString;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException | IOException e) {
            InternalStaticEmbraceLogger.logError("data cannot be encrypted", e);
        }
        return result;
    }

    /**
     * @param data Base64 encrypted data.
     * @return decrypted data or null if any error occur
     */
    @Nullable
    public String decrypt(@NonNull String data, @NonNull Key key) {
        String result = null;
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE, key);

            byte[] decodedData;
            byte[] encryptedData = Base64.decode(data, Base64.DEFAULT);
            decodedData = decodeWithBuffer(cipher, encryptedData, mDecryptionBlockSize);

            result = new String(decodedData, UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 BadPaddingException | IllegalBlockSizeException | IOException e) {
            InternalStaticEmbraceLogger.logError("data cannot be encrypted", e);
        }
        return result;
    }

    private byte[] decodeWithBuffer(@NonNull Cipher cipher, @NonNull byte[] plainData, int bufferLength)
        throws IllegalBlockSizeException, BadPaddingException {
        // string initialize 2 buffers.
        // scrambled will hold intermediate results
        byte[] scrambled;

        // toReturn will hold the total result
        byte[] toReturn = new byte[0];

        // holds the bytes that have to be modified in one step
        byte[] buffer = new byte[(plainData.length > bufferLength ? bufferLength : plainData.length)];

        for (int i = 0; i < plainData.length; i++) {
            if ((i > 0) && (i % bufferLength == 0)) {
                //execute the operation
                scrambled = cipher.doFinal(buffer);
                // add the result to our total result.
                toReturn = append(toReturn, scrambled);
                // here we calculate the bufferLength of the next buffer required
                int newLength = bufferLength;

                // if newLength would be longer than remaining bytes in the bytes array we shorten it.
                if (i + bufferLength > plainData.length) {
                    newLength = plainData.length - i;
                }
                // clean the buffer array
                buffer = new byte[newLength];
            }
            // copy byte into our buffer.
            buffer[i % bufferLength] = plainData[i];
        }

        // this step is needed if we had a trailing buffer. should only happen when encrypting.
        // example: we encrypt 110 bytes. 100 bytes per run means we "forgot" the last 10 bytes. they are in the buffer array
        scrambled = cipher.doFinal(buffer);

        // final step before we can return the modified data.
        toReturn = append(toReturn, scrambled);
        return toReturn;
    }

    private byte[] append(byte[] prefix, byte[] suffix) {
        byte[] toReturn = new byte[prefix.length + suffix.length];
        for (int i = 0; i < prefix.length; i++) {
            toReturn[i] = prefix[i];
        }
        for (int i = 0; i < suffix.length; i++) {
            toReturn[i + prefix.length] = suffix[i];
        }
        return toReturn;
    }

    private Key getKeyFromText(String keyText) {

        try {
            X509EncodedKeySpec encodedKeySpec = new X509EncodedKeySpec(Base64.decode(keyText, Base64.DEFAULT));
            return KeyFactory.getInstance("RSA").generatePublic(encodedKeySpec);
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }


        return null;
    }
}