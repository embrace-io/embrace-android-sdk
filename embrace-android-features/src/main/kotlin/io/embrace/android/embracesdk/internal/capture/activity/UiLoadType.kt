package io.embrace.android.embracesdk.internal.capture.activity

/**
 * The type of UI load being traced
 */
enum class UiLoadType(val typeName: String) {
    /**
     * Load where the Activity instance has to be created
     */
    COLD("cold"),

    /**
     * Load where the instance has already been created and just needs to be started and resumed (e.g. foregrounding)
     */
    HOT("hot")
}
