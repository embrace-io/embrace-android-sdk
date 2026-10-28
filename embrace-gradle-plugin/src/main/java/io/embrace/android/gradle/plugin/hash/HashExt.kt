package io.embrace.android.gradle.plugin.hash

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Calculates a SHA-1 from a file.
 */
fun calculateSha1ForFile(file: File): String {
    val bytes = digestFile("SHA-1", file)
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Calculates a MD5 from a file.
 */
fun calculateMD5ForFile(file: File): String {
    val hasFile: String
    val fileHashed = digestFile("MD5", file)
    val sb = StringBuilder()
    for (b in fileHashed) {
        sb.append(String.format(Locale.getDefault(), "%02x", b.toInt() and 0xff))
    }
    hasFile = sb.toString().uppercase(Locale.getDefault())
    return hasFile
}

/**
 * Digests [file] in chunks so memory remains O(1) regardless of file size.
 */
private fun digestFile(algorithm: String, file: File): ByteArray {
    val md = MessageDigest.getInstance(algorithm)
    file.inputStream().use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var n: Int
        while (stream.read(buffer).also { n = it } != -1) {
            md.update(buffer, 0, n)
        }
    }
    return md.digest()
}
