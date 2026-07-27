package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelLimitsRemoteConfig

/**
 * Applies the remote overrides for the OTel capture limits on top of the limits declared locally. Any
 * value that is not overridden by remote config keeps the value supplied by [local], meaning the
 * limits are unchanged when no remote config is present.
 */
class RemoteOtelLimitsConfig(
    private val local: OtelLimitsConfig,
    private val remote: OtelLimitsRemoteConfig?,
) : OtelLimitsConfig {

    override fun getMaxInternalNameLength(): Int =
        remote?.maxInternalNameLength.orLocal(local::getMaxInternalNameLength)

    override fun getMaxNameLength(): Int =
        remote?.maxNameLength.orLocal(local::getMaxNameLength)

    override fun getMaxCustomEventCount(): Int =
        remote?.maxCustomEventCount.orLocal(local::getMaxCustomEventCount)

    override fun getMaxSystemEventCount(): Int =
        remote?.maxSystemEventCount.orLocal(local::getMaxSystemEventCount)

    override fun getMaxCustomAttributeCount(): Int =
        remote?.maxCustomAttributeCount.orLocal(local::getMaxCustomAttributeCount)

    override fun getMaxSystemAttributeCount(): Int =
        remote?.maxSystemAttributeCount.orLocal(local::getMaxSystemAttributeCount)

    override fun getMaxEventAttributeCount(): Int =
        remote?.maxEventAttributeCount.orLocal(local::getMaxEventAttributeCount)

    override fun getMaxCustomLinkCount(): Int =
        remote?.maxCustomLinkCount.orLocal(local::getMaxCustomLinkCount)

    override fun getMaxSystemLinkCount(): Int =
        remote?.maxSystemLinkCount.orLocal(local::getMaxSystemLinkCount)

    override fun getMaxInternalAttributeKeyLength(): Int =
        remote?.maxInternalAttributeKeyLength.orLocal(local::getMaxInternalAttributeKeyLength)

    override fun getMaxInternalAttributeValueLength(): Int =
        remote?.maxInternalAttributeValueLength.orLocal(local::getMaxInternalAttributeValueLength)

    override fun getMaxCustomAttributeKeyLength(): Int =
        remote?.maxCustomAttributeKeyLength.orLocal(local::getMaxCustomAttributeKeyLength)

    override fun getMaxCustomAttributeValueLength(): Int =
        remote?.maxCustomAttributeValueLength.orLocal(local::getMaxCustomAttributeValueLength)

    override fun getExceptionEventName(): String =
        remote?.exceptionEventName?.takeIf(String::isNotBlank) ?: local.getExceptionEventName()

    /**
     * A non-positive limit would silently disable telemetry capture entirely, so treat it as unset.
     */
    private fun Int?.orLocal(localValue: () -> Int): Int = this?.takeIf { it > 0 } ?: localValue()
}
