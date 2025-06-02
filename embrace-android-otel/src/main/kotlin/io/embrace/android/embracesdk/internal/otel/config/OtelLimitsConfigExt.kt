package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig

fun OtelLimitsConfig.getMaxTotalAttributeCount() = getMaxSystemAttributeCount() + getMaxCustomAttributeCount()

fun OtelLimitsConfig.getMaxTotalEventCount() = getMaxSystemEventCount() + getMaxCustomEventCount()

fun OtelLimitsConfig.getMaxTotalLinkCount() = getMaxSystemLinkCount() + getMaxCustomLinkCount()
