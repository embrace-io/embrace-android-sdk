package io.embrace.android.embracesdk.internal.config.instrumented

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig

internal fun OtelLimitsConfig.isNameValid(str: String): Boolean =
    str.isNotBlank() && str.length <= getMaxNameLength()

internal fun OtelLimitsConfig.isAttributeValid(key: String, value: String) =
    key.length <= getMaxAttributeKeyLength() && value.length <= getMaxAttributeValueLength()
