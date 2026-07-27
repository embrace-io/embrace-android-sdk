package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelLimitsRemoteConfig

/**
 * Applies the remote overrides for the OTel capture limits on top of the limits declared locally. A
 * remote value can only lower the limit declared by [local], never raise it, and any value that is
 * not overridden keeps the value supplied by [local]. The locally declared limits therefore hold
 * whenever no remote config is present.
 */
class RemoteOtelLimitsConfig(
    private val local: OtelLimitsConfig,
    private val remote: OtelLimitsRemoteConfig?,
) : OtelLimitsConfig {

    override fun getMaxInternalNameLength(): Int =
        remote?.maxInternalNameLength.cappedBy(local::getMaxInternalNameLength)

    override fun getMaxNameLength(): Int =
        remote?.maxNameLength.cappedBy(local::getMaxNameLength)

    override fun getMaxCustomEventCount(): Int =
        remote?.maxCustomEventCount.cappedBy(local::getMaxCustomEventCount)

    override fun getMaxSystemEventCount(): Int =
        remote?.maxSystemEventCount.cappedBy(local::getMaxSystemEventCount)

    override fun getMaxCustomAttributeCount(): Int =
        remote?.maxCustomAttributeCount.cappedBy(local::getMaxCustomAttributeCount)

    override fun getMaxSystemAttributeCount(): Int =
        remote?.maxSystemAttributeCount.cappedBy(local::getMaxSystemAttributeCount)

    override fun getMaxEventAttributeCount(): Int =
        remote?.maxEventAttributeCount.cappedBy(local::getMaxEventAttributeCount)

    override fun getMaxCustomLinkCount(): Int =
        remote?.maxCustomLinkCount.cappedBy(local::getMaxCustomLinkCount)

    override fun getMaxSystemLinkCount(): Int =
        remote?.maxSystemLinkCount.cappedBy(local::getMaxSystemLinkCount)

    override fun getMaxInternalAttributeKeyLength(): Int =
        remote?.maxInternalAttributeKeyLength.cappedBy(local::getMaxInternalAttributeKeyLength)

    override fun getMaxInternalAttributeValueLength(): Int =
        remote?.maxInternalAttributeValueLength.cappedBy(local::getMaxInternalAttributeValueLength)

    override fun getMaxCustomAttributeKeyLength(): Int =
        remote?.maxCustomAttributeKeyLength.cappedBy(local::getMaxCustomAttributeKeyLength)

    override fun getMaxCustomAttributeValueLength(): Int =
        remote?.maxCustomAttributeValueLength.cappedBy(local::getMaxCustomAttributeValueLength)

    override fun getExceptionEventName(): String =
        remote?.exceptionEventName?.takeIf(String::isNotBlank) ?: local.getExceptionEventName()

    /**
     * Clamps a remote limit to the local one so it can only ever be lowered. A non-positive limit would
     * silently disable telemetry capture entirely, so it is treated as unset.
     */
    private fun Int?.cappedBy(localValue: () -> Int): Int {
        val local = localValue()
        return this?.takeIf { it > 0 }?.coerceAtMost(local) ?: local
    }
}
