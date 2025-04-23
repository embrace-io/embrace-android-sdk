package io.embrace.android.embracesdk.internal.config.instrumented.schema

import androidx.annotation.Keep

/**
 * A Base64 encoded string of the shared object files that are used to symbolicate crashes.
 */
@Keep
interface Base64SharedObjectFilesMap {

    /**
     * A Base64 encoded string of the shared object files that are used to symbolicate crashes.
     *
     * Once decoded, this string should be a JSON object with the following structure:
     * {
     *   "symbols": {
     *      "arm64-v8a": {
     *          "libemb-donuts.so":"11e1c3cc39fc506d06fcdb75bb4d005345aba95e",
     *          "libemb-crisps.so":"ace3755e21c6961e9c3e329216eeb7333840074b"
     *      }
     *      ...
     * }
     */
    fun getBase64SharedObjectFilesMap(): String? = null
}
