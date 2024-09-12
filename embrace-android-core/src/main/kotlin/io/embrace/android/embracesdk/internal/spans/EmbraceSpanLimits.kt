package io.embrace.android.embracesdk.internal.spans

object EmbraceSpanLimits {
    const val MAX_INTERNAL_NAME_LENGTH: Int = 2000
    const val MAX_NAME_LENGTH: Int = 50
    const val MAX_CUSTOM_EVENT_COUNT: Int = 10
    const val MAX_TOTAL_EVENT_COUNT: Int = 11000
    const val MAX_CUSTOM_ATTRIBUTE_COUNT: Int = 50
    const val MAX_TOTAL_ATTRIBUTE_COUNT: Int = 300
    const val MAX_INTERNAL_ATTRIBUTE_KEY_LENGTH: Int = 1000
    const val MAX_INTERNAL_ATTRIBUTE_VALUE_LENGTH: Int = 2000
    const val MAX_CUSTOM_ATTRIBUTE_KEY_LENGTH: Int = 50
    const val MAX_CUSTOM_ATTRIBUTE_VALUE_LENGTH: Int = 500
    const val EXCEPTION_EVENT_NAME: String = "exception"

    internal fun String.isNameValid(internal: Boolean): Boolean =
        isNotBlank() && ((internal && length <= MAX_INTERNAL_NAME_LENGTH) || length <= MAX_NAME_LENGTH)

    internal fun isAttributeValid(key: String, value: String, internal: Boolean) =
        ((internal && key.length <= MAX_INTERNAL_ATTRIBUTE_KEY_LENGTH) || key.length <= MAX_CUSTOM_ATTRIBUTE_KEY_LENGTH) &&
            ((internal && value.length <= MAX_INTERNAL_ATTRIBUTE_VALUE_LENGTH) || value.length <= MAX_CUSTOM_ATTRIBUTE_VALUE_LENGTH)
}
