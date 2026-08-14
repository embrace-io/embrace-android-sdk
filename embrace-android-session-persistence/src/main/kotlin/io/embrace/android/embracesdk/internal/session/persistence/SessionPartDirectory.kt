package io.embrace.android.embracesdk.internal.session.persistence

/**
 * Metadata about a session part that stores telemetry in a directory
 */
data class SessionPartDirectory(
    val timestamp: Long,
    val uuid: String,
    val userSessionId: String = "",
    val sessionPartId: String = "",
) {
    val dirName: String = "${timestamp}_${uuid}_${encodeId(userSessionId)}_${encodeId(sessionPartId)}"

    companion object {
        private const val EMPTY_ID_TOKEN = "none"
        private const val TOKEN_COUNT = 4

        /**
         * Orders directories by timestamp then uuid, which is the order that session parts must
         * be delivered in.
         */
        val comparator: Comparator<SessionPartDirectory> =
            compareBy<SessionPartDirectory> { it.timestamp }.thenBy { it.uuid }

        private fun encodeId(id: String): String = id.ifEmpty { EMPTY_ID_TOKEN }

        private fun decodeId(token: String): String = when (token) {
            EMPTY_ID_TOKEN -> ""
            else -> token
        }

        /**
         * Parses a directory name and constructs a [SessionPartDirectory] object. This returns
         * null if the directory name is invalid.
         */
        fun fromDirName(dirName: String): SessionPartDirectory? {
            val parts = dirName.split("_")
            if (parts.size != TOKEN_COUNT) {
                return null
            }
            val timestamp = parts[0].toLongOrNull() ?: return null
            val uuid = parts[1].ifEmpty { return null }
            return SessionPartDirectory(
                timestamp = timestamp,
                uuid = uuid,
                userSessionId = decodeId(parts[2]),
                sessionPartId = decodeId(parts[3]),
            )
        }
    }
}
