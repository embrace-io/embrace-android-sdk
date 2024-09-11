package io.embrace.android.embracesdk.internal.capture.activity

/**
 * The type of activity opening being traced
 */
enum class OpenType(val typeName: String) {
    /**
     * Activity opening where the instance has to be created
     */
    COLD("cold"),

    /**
     * Activity opening where the instance has already been created and just needs to be started and resumed (e.g. app foregrounding)
     */
    HOT("hot")
}
