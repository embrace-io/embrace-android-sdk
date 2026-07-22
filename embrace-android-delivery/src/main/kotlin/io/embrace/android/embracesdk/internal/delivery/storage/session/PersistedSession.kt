package io.embrace.android.embracesdk.internal.delivery.storage.session

import java.io.File

/**
 * Identifies a single session part and encodes the values that make up its directory name:
 * timestamp (delivery ordering), uuid (disambiguation), user session id, session part id.
 */
data class PersistedSession(
    val timestamp: Long,
    val uuid: String,
    val userSessionId: String,
    val sessionPartId: String,
) {
    val dirName: String = "${timestamp}_${uuid}_${encode(userSessionId)}_${encode(sessionPartId)}"

    fun directory(root: File): SessionPartDirectory = SessionPartDirectory(File(root, dirName))

    companion object {
        private const val EMPTY_TOKEN = "none"
        private fun encode(id: String): String = id.ifEmpty { EMPTY_TOKEN }
        private fun decode(token: String): String = if (token == EMPTY_TOKEN) "" else token

        fun fromDirName(dirName: String): PersistedSession? {
            val parts = dirName.split("_")
            if (parts.size != 4) return null
            val timestamp = parts[0].toLongOrNull() ?: return null
            return PersistedSession(timestamp, parts[1], decode(parts[2]), decode(parts[3]))
        }
    }
}
