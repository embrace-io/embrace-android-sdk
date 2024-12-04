package io.embrace.android.embracesdk.internal.config.instrumented

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig

internal fun OtelLimitsConfig.isNameValid(str: String, internal: Boolean): Boolean =
    str.isNotBlank() && ((internal && str.length <= getMaxInternalNameLength()) || str.length <= getMaxNameLength())

internal fun OtelLimitsConfig.isAttributeValid(key: String, value: String, internal: Boolean) =
    ((internal && key.length <= getMaxInternalAttributeKeyLength()) || key.length <= getMaxCustomAttributeKeyLength()) &&
        ((internal && value.length <= getMaxInternalAttributeValueLength()) || value.length <= getMaxCustomAttributeValueLength())
