package io.embrace.android.gradle.plugin.hash

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Calculates a SHA-1 from a file.
 */
fun calculateSha1ForFile(file: File): String {
    val bytes = MessageDigest
        .getInstance("SHA-1")
        .digest(file.readBytes())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Calculates a MD5 from a file.
 */
fun calculateMD5ForFile(file: File): String {
    val hasFile: String
    val md = MessageDigest.getInstance("MD5")
    val fileHashed = md.digest(file.readBytes())
    val sb = StringBuilder()
    for (b in fileHashed) {
        sb.append(String.format(Locale.getDefault(), "%02x", b.toInt() and 0xff))
    }
    hasFile = sb.toString().uppercase(Locale.getDefault())
    return hasFile
}
