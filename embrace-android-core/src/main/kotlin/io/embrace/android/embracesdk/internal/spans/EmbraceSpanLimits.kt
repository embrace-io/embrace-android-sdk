package io.embrace.android.embracesdk.internal.spans

object EmbraceSpanLimits {
    const val MAX_NAME_LENGTH: Int = 50
    const val MAX_CUSTOM_EVENT_COUNT: Int = 10
    const val MAX_TOTAL_EVENT_COUNT: Int = 11000
    const val MAX_CUSTOM_ATTRIBUTE_COUNT: Int = 50
    const val MAX_TOTAL_ATTRIBUTE_COUNT: Int = 300
    const val MAX_CUSTOM_ATTRIBUTE_KEY_LENGTH: Int = 50
    const val MAX_CUSTOM_ATTRIBUTE_VALUE_LENGTH: Int = 500
    const val EXCEPTION_EVENT_NAME: String = "exception"
}
