package io.embrace.android.embracesdk.internal

/**
 * Provides access to internal Embrace SDK APIs. This is intended for use by Embrace's SDKs only and is subject
 * to breaking changes without warning.
 */
class EmbraceInternalApi private constructor() {

    companion object {
        private val instance = EmbraceInternalApi()

        @JvmStatic
        fun getInstance(): EmbraceInternalApi = instance
    }
}
