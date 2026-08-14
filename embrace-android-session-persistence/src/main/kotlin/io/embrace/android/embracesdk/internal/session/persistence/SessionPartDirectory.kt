package io.embrace.android.embracesdk.internal.session.persistence

/**
 * Metadata about a session part that stores telemetry in a directory
 */
data class SessionPartDirectory(
    val timestamp: Long,
    val uuid: String,
    val userSessionId: String = "",
    val sessionPartId: String = "",
    val version: SessionPartDirectoryVersion = SessionPartDirectoryVersion.V1,
) {
    val dirName: String =
        "${version.token}_${timestamp}_${uuid}_${encodeId(userSessionId)}_${encodeId(sessionPartId)}"

    companion object {
        private const val EMPTY_ID_TOKEN = "none"
        private const val TOKEN_COUNT = 5

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
            val version = SessionPartDirectoryVersion.fromToken(parts[0]) ?: return null
            val timestamp = parts[1].toLongOrNull() ?: return null
            val uuid = parts[2].ifEmpty { return null }
            return SessionPartDirectory(
                timestamp = timestamp,
                uuid = uuid,
                userSessionId = decodeId(parts[3]),
                sessionPartId = decodeId(parts[4]),
                version = version,
            )
        }
    }
}

/**
 * Version of the encoding used for a session part directory name.
 */
enum class SessionPartDirectoryVersion(val token: String) {

    /**
     * Initial encoding: `v1_<timestampMs>_<uuid>_<userSessionId>_<sessionPartId>`.
     */
    V1("v1"),
    ;

    internal companion object {

        /**
         * Returns the version that the given token identifies, or null if unrecognised.
         */
        fun fromToken(token: String): SessionPartDirectoryVersion? =
            entries.firstOrNull { it.token == token }
    }
}
