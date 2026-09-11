package io.embrace.android.embracesdk.internal.utils

/**
 * Maximum length `android.os.Trace` accepts for a section name.
 */
const val MAX_TRACE_NAME_LENGTH: Int = 127

/**
 * Prefixes [name] with "emb-" and truncates the result to [MAX_TRACE_NAME_LENGTH], which is the
 * longest name `android.os.Trace` accepts.
 *
 * Only pays for the extra substring allocation when the name actually exceeds that limit.
 */
fun prefixedTraceName(name: String): String {
    val prefixed = "emb-$name"
    return when {
        prefixed.length > MAX_TRACE_NAME_LENGTH -> prefixed.substring(0, MAX_TRACE_NAME_LENGTH)
        else -> prefixed
    }
}

/**
 * Records sections into whatever tracing mechanism the platform provides.
 */
interface SectionRecorder {

    /**
     * Opens a section named [sectionName].
     *
     * @return true if a section was actually opened, and so must be closed with [endSection].
     */
    fun beginSection(sectionName: String): Boolean

    /**
     * Closes the section most recently opened by [beginSection] on this thread.
     */
    fun endSection()
}

/**
 * Adds sections to system traces from modules that cannot depend on the Android SDK.
 */
object SystemTrace {

    @Volatile
    var recorder: SectionRecorder? = null

    /**
     * Create a trace section around [code] and return its result. The name of the section will be
     * [sectionName] prefixed by "emb-" and truncated to [MAX_TRACE_NAME_LENGTH].
     */
    inline fun <T> trace(sectionName: String, code: Provider<T>): T {
        val recorder = recorder ?: return code()
        val opened = recorder.beginSection(sectionName)
        try {
            return code()
        } finally {
            if (opened) {
                recorder.endSection()
            }
        }
    }
}
