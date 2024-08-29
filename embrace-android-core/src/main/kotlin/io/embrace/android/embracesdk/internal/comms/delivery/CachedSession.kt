package io.embrace.android.embracesdk.internal.comms.delivery

/**
 * Represents a session that is cached on disk. The cached session encodes information in its
 * filename such as sessionId, timestamp, and payload version. This allows the SDK to process
 * cached sessions without deserializing the JSON.
 */
public class CachedSession private constructor(
    public val sessionId: String,
    public val timestampMs: Long,
    public val filename: String,
    public val v2Payload: Boolean
) {

    public companion object {
        private const val V2_PREFIX = "v2"

        /**
         * Creates a [CachedSession] from a session id and timestamp.
         */
        public fun create(sessionId: String, timestampMs: Long, v2Payload: Boolean = true): CachedSession {
            val filename = when {
                v2Payload -> getV2FileNameForSession(
                    sessionId,
                    timestampMs
                )
                else -> getV1FileNameForSession(sessionId, timestampMs)
            }
            return CachedSession(
                sessionId,
                timestampMs,
                filename,
                v2Payload
            )
        }

        /**
         * Creates a [CachedSession] from a filename.
         */
        public fun fromFilename(filename: String): CachedSession? {
            val values = filename.split('.')
            if (values.size == 4 || values.size == 5) {
                val v2Payload = isV2Payload(filename)
                val encodedInfo = when {
                    v2Payload -> values.take(4)
                    else -> values
                }

                val timestamp = encodedInfo[1].toLongOrNull()
                timestamp?.also { ts ->
                    val sessionId = encodedInfo[2]
                    return create(sessionId, ts, v2Payload)
                }
            }
            return null
        }

        private fun getV1FileNameForSession(
            sessionId: String,
            timestampMs: Long
        ): String = "${EmbraceCacheService.SESSION_FILE_PREFIX}.$timestampMs.$sessionId.json"

        private fun getV2FileNameForSession(
            sessionId: String,
            timestampMs: Long
        ): String = "${EmbraceCacheService.SESSION_FILE_PREFIX}.$timestampMs.$sessionId.$V2_PREFIX.json"

        private fun isV2Payload(filename: String): Boolean = filename.endsWith("$V2_PREFIX.json")
    }
}
