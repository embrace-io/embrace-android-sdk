package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import java.io.DataOutput

internal class BinaryRemoteConfigEncoder {

    /**
     * Writes [config] to [this] in declaration order. The decoder must read fields back in the
     * exact same order. A header is written first — the format version, the cached [deviceId], and
     * the SDK-enablement [RemoteConfig.threshold] — so the decoder can evaluate the startup gate
     * before decoding the rest. Nullable scalars are prefixed with a boolean presence flag; nullable
     * collections write a length of -1 when absent.
     */
    fun DataOutput.write(deviceId: String, config: RemoteConfig) {
        writeInt(BinaryRemoteConfigFormat.VERSION)
        writeUTF(deviceId)
        writeNullableInt(config.threshold)
        writeStrings(config.disabledEventAndLogPatterns)
        writeStrings(config.disabledUrlPatterns)
        writeCollection(config.networkCaptureRules) { writeNetworkCaptureRule(it) }
        writeNullable(config.uiConfig) { writeUiConfig(it) }
        writeNullable(config.networkConfig) { writeNetworkConfig(it) }
        writeNullable(config.sessionConfig) { writeSessionConfig(it) }
        writeNullable(config.logConfig) { writeLogConfig(it) }
        writeNullable(config.threadBlockageRemoteConfig) { writeThreadBlockageConfig(it) }
        writeNullable(config.dataConfig) { writeDataConfig(it) }
        writeNullable(config.killSwitchConfig) { writeKillSwitchConfig(it) }
        writeNullableBoolean(config.internalExceptionCaptureEnabled)
        writeNullable(config.appExitInfoConfig) { writeAppExitInfoConfig(it) }
        writeNullable(config.backgroundActivityConfig) { writeBackgroundActivityConfig(it) }
        writeNullableInt(config.maxUserSessionProperties)
        writeNullable(config.networkSpanForwardingRemoteConfig) { writeNetworkSpanForwardingConfig(it) }
        writeNullableBoolean(config.uiLoadInstrumentationEnabled)
        writeNullable(config.otelKotlinSdkConfig) { writeOtelKotlinSdkConfig(it) }
        writeNullableFloat(config.pctStateCaptureEnabledV2)
        writeNullableFloat(config.pctNetworkCallbackConnectivityServiceEnabled)
        writeNullableFloat(config.pctNavigationStateCaptureEnabled)
        writeNullable(config.userSession) { writeUserSessionConfig(it) }
    }

    private fun DataOutput.writeNetworkCaptureRule(rule: NetworkCaptureRuleRemoteConfig) {
        writeUTF(rule.id)
        writeNullableLong(rule.duration)
        writeUTF(rule.method)
        writeUTF(rule.urlRegex)
        writeLong(rule.expiresIn)
        writeLong(rule.maxSize)
        writeInt(rule.maxCount)
        writeCollection(rule.statusCodes) { writeInt(it) }
    }

    private fun DataOutput.writeUiConfig(config: UiRemoteConfig) {
        writeNullableInt(config.breadcrumbs)
        writeNullableInt(config.taps)
        writeNullableInt(config.webViews)
        writeNullableInt(config.fragments)
    }

    private fun DataOutput.writeNetworkConfig(config: NetworkRemoteConfig) {
        writeNullableInt(config.defaultCaptureLimit)
        writeStringIntMap(config.domainLimits)
    }

    private fun DataOutput.writeSessionConfig(config: SessionRemoteConfig) {
        writeNullableBoolean(config.isEnabled)
    }

    private fun DataOutput.writeLogConfig(config: LogRemoteConfig) {
        writeNullableInt(config.logMessageMaximumAllowedLength)
        writeNullableInt(config.logInfoLimit)
        writeNullableInt(config.logWarnLimit)
        writeNullableInt(config.logErrorLimit)
    }

    private fun DataOutput.writeThreadBlockageConfig(config: ThreadBlockageRemoteConfig) {
        writeNullableInt(config.pctEnabled)
        writeNullableLong(config.sampleIntervalMs)
        writeNullableInt(config.maxStacktracesPerInterval)
        writeNullableInt(config.stacktraceFrameLimit)
        writeNullableInt(config.intervalsPerSession)
        writeNullableInt(config.minDuration)
        writeNullableInt(config.monitorThreadPriority)
    }

    private fun DataOutput.writeDataConfig(config: DataRemoteConfig) {
        writeNullableFloat(config.pctThermalStatusEnabled)
    }

    private fun DataOutput.writeKillSwitchConfig(config: KillSwitchRemoteConfig) {
        writeNullableBoolean(config.sigHandlerDetection)
        writeNullableBoolean(config.jetpackCompose)
    }

    private fun DataOutput.writeAppExitInfoConfig(config: AppExitInfoConfig) {
        writeNullableInt(config.appExitInfoTracesLimit)
        writeNullableFloat(config.pctAeiCaptureEnabled)
        writeNullableInt(config.aeiMaxNum)
    }

    private fun DataOutput.writeBackgroundActivityConfig(config: BackgroundActivityRemoteConfig) {
        writeNullableFloat(config.threshold)
    }

    private fun DataOutput.writeNetworkSpanForwardingConfig(config: NetworkSpanForwardingRemoteConfig) {
        writeNullableFloat(config.pctEnabled)
    }

    private fun DataOutput.writeOtelKotlinSdkConfig(config: OtelKotlinSdkConfig) {
        writeNullableFloat(config.pctEnabled)
    }

    private fun DataOutput.writeUserSessionConfig(config: UserSessionRemoteConfig) {
        writeNullableInt(config.maxDurationSeconds)
        writeNullableInt(config.inactivityTimeoutSeconds)
    }

    fun DataOutput.writeStrings(strings: Collection<String>?) {
        writeCollection(strings) { writeUTF(it) }
    }

    inline fun <T> DataOutput.writeCollection(collection: Collection<T>?, encoder: DataOutput.(T) -> Unit) {
        if (collection == null) {
            writeInt(-1)
            return
        }

        val count = collection.size
        writeInt(count)

        // we break out the iteration to ensure the number of elements written exactly matches the count provided
        val iterator = collection.iterator()
        repeat(count) {
            encoder(iterator.next())
        }
    }

    fun DataOutput.writeStringIntMap(map: Map<String, Int>?) {
        writeCollection(map?.entries) { (key, value) ->
            writeUTF(key)
            writeInt(value)
        }
    }

    inline fun <T> DataOutput.writeNullable(value: T?, encoder: DataOutput.(T) -> Unit) {
        if (value != null) {
            writeBoolean(true)
            encoder(value)
        } else {
            writeBoolean(false)
        }
    }

    fun DataOutput.writeNullableInt(value: Int?) = writeNullable(value) { writeInt(it) }

    fun DataOutput.writeNullableLong(value: Long?) = writeNullable(value) { writeLong(it) }

    fun DataOutput.writeNullableFloat(value: Float?) = writeNullable(value) { writeFloat(it) }

    fun DataOutput.writeNullableBoolean(value: Boolean?) = writeNullable(value) { writeBoolean(it) }
}
