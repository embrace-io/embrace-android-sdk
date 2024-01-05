package io.embrace.android.embracesdk.samples

import android.app.Activity
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.io.File
import java.io.FileNotFoundException

internal class AutomaticVerificationChecker {
    private val fileName = "emb_marker_file.txt"
    private val verificationResult = VerificationResult()
    private lateinit var file: File
    private val serializer = EmbraceSerializer()

    /**
     * Returns true if the file was created, false if it already existed
     */
    fun createFile(activity: Activity): Boolean {
        val directory = activity.cacheDir.absolutePath
        file = File("$directory/$fileName")

        return generateMarkerFile()
    }

    /**
     * Verifies if the marker file exists. It is used to determine whether to run the verification or no.
     * If marker file does not exist, then we have to run the automatic verification,
     * on the other hand, if the marker file exists,
     * it means that the verification was executed before and it shouldn't run again
     *
     * @return true if marker file does not exist, otherwise returns false
     */
    private fun generateMarkerFile(): Boolean {
        var result = false
        if (!file.exists()) {
            result = file.createNewFile()
        }

        return result
    }

    fun deleteFile() {
        if (file.exists() && !file.isDirectory) {
            file.delete()
        }
    }

    /**
     * The verification is correct if the file doesn't have any exception written.
     * This could be called before the file is initialized, in that case it returns null.
     */
    fun isVerificationCorrect(): Boolean? {
        try {
            if (::file.isInitialized) { // we should rethink this flow to avoid having this verification
                val fileContent = file.readText()
                return if (fileContent.isEmpty()) {
                    true
                } else {
                    serializer.fromJson(fileContent, VerificationResult::class.java).exceptions.isEmpty()
                }
            }
        } catch (e: FileNotFoundException) {
            InternalStaticEmbraceLogger.logger.logError("cannot open file", e)
        }
        return null
    }

    fun addException(e: Throwable) {
        verificationResult.exceptions.add(e)
        file.writeText(serializer.toJson(verificationResult).toString())
    }

    fun getExceptions(): List<Throwable> {
        val fileContent = file.readText()
        return if (fileContent.isBlank()) {
            emptyList()
        } else {
            serializer.fromJson(fileContent, VerificationResult::class.java).exceptions
        }
    }
}
