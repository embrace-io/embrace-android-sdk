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
import java.io.DataInput

internal class BinaryRemoteConfigDecoder {

    /**
     * Reads a [RemoteConfig] previously written by [BinaryRemoteConfigEncoder]. The format version
     * is validated first; a mismatch returns null, which the store treats as a cache miss and falls
     * back to the JSON file.
     *
     * After the version, the header (cached deviceId + threshold) is read and handed to [onHeader].
     * If [onHeader] returns `true` the SDK is disabled for this device, so decoding stops and a
     * minimal [RemoteConfig] carrying only the threshold is returned — enough for the authoritative
     * downstream `SdkModeBehavior` gate to fire, without paying to decode the rest of the payload.
     * [onHeader] must not throw: this read also runs on the background config-refresh poll, where an
     * uncaught exception would suppress all future poll executions.
     *
     * When [onHeader] returns `false` the remaining fields are read in declaration order, mirroring
     * the encoder. Each type is built via its complete primary constructor with positional arguments
     * (no named args, no reliance on defaults): adding, removing, retyping, or reordering a property
     * fails to compile, forcing the codec to be updated in lockstep. Kotlin evaluates call arguments
     * left-to-right, so the inline reads below execute in the exact order the encoder wrote them.
     */
    fun DataInput.readRemoteConfig(onHeader: (Header) -> Boolean): RemoteConfig? {
        val version = readInt()
        if (version != BinaryRemoteConfigFormat.VERSION) {
            return null
        }
        val deviceId = readUTF()
        val threshold = readNullableInt()
        if (onHeader(Header(version, threshold, deviceId))) {
            return RemoteConfig(threshold = threshold)
        }
        return readConfig(threshold)
    }

    /**
     * Reads only the cached deviceId from the header (version + deviceId), stopping before the rest
     * of the payload. Returns null on a version mismatch or any non-binary content, mirroring
     * [readRemoteConfig]'s miss semantics, so the caller falls back to its canonical deviceId source.
     */
    fun DataInput.readDeviceId(): String? {
        val version = readInt()
        if (version != BinaryRemoteConfigFormat.VERSION) {
            return null
        }
        return readUTF()
    }

    private fun DataInput.readConfig(threshold: Int?): RemoteConfig = RemoteConfig(
        threshold, // threshold (already read as part of the header)
        readStringSet(), // disabledEventAndLogPatterns
        readStringSet(), // disabledUrlPatterns
        readCollection(::LinkedHashSet) { readNetworkCaptureRule() }, // networkCaptureRules
        readNullable { readUiConfig() }, // uiConfig
        readNullable { readNetworkConfig() }, // networkConfig
        readNullable { readSessionConfig() }, // sessionConfig
        readNullable { readLogConfig() }, // logConfig
        readNullable { readThreadBlockageConfig() }, // threadBlockageRemoteConfig
        readNullable { readDataConfig() }, // dataConfig
        readNullable { readKillSwitchConfig() }, // killSwitchConfig
        readNullableBoolean(), // internalExceptionCaptureEnabled
        readNullable { readAppExitInfoConfig() }, // appExitInfoConfig
        readNullable { readBackgroundActivityConfig() }, // backgroundActivityConfig
        readNullableInt(), // maxUserSessionProperties
        readNullable { readNetworkSpanForwardingConfig() }, // networkSpanForwardingRemoteConfig
        readNullableBoolean(), // uiLoadInstrumentationEnabled
        readNullable { readOtelKotlinSdkConfig() }, // otelKotlinSdkConfig
        readNullableFloat(), // pctStateCaptureEnabledV2
        readNullableFloat(), // pctNetworkCallbackConnectivityServiceEnabled
        readNullableFloat(), // pctNavigationStateCaptureEnabled
        readNullable { readUserSessionConfig() }, // userSession
    )

    private fun DataInput.readNetworkCaptureRule() = NetworkCaptureRuleRemoteConfig(
        readUTF(), // id
        readNullableLong(), // duration
        readUTF(), // method
        readUTF(), // urlRegex
        readLong(), // expiresIn
        readLong(), // maxSize
        readInt(), // maxCount
        readCollection(::LinkedHashSet) { readInt() }.orEmpty(), // statusCodes
    )

    private fun DataInput.readUiConfig() = UiRemoteConfig(
        readNullableInt(), // breadcrumbs
        readNullableInt(), // taps
        readNullableInt(), // webViews
        readNullableInt(), // fragments
    )

    private fun DataInput.readNetworkConfig() = NetworkRemoteConfig(
        readNullableInt(), // defaultCaptureLimit
        readStringIntMap(), // domainLimits
    )

    private fun DataInput.readSessionConfig() = SessionRemoteConfig(
        readNullableBoolean(), // isEnabled
    )

    private fun DataInput.readLogConfig() = LogRemoteConfig(
        readNullableInt(), // logMessageMaximumAllowedLength
        readNullableInt(), // logInfoLimit
        readNullableInt(), // logWarnLimit
        readNullableInt(), // logErrorLimit
    )

    private fun DataInput.readThreadBlockageConfig() = ThreadBlockageRemoteConfig(
        readNullableInt(), // pctEnabled
        readNullableLong(), // sampleIntervalMs
        readNullableInt(), // maxStacktracesPerInterval
        readNullableInt(), // stacktraceFrameLimit
        readNullableInt(), // intervalsPerSession
        readNullableInt(), // minDuration
        readNullableInt(), // monitorThreadPriority
    )

    private fun DataInput.readDataConfig() = DataRemoteConfig(
        readNullableFloat(), // pctThermalStatusEnabled
    )

    private fun DataInput.readKillSwitchConfig() = KillSwitchRemoteConfig(
        readNullableBoolean(), // sigHandlerDetection
        readNullableBoolean(), // jetpackCompose
    )

    private fun DataInput.readAppExitInfoConfig() = AppExitInfoConfig(
        readNullableInt(), // appExitInfoTracesLimit
        readNullableFloat(), // pctAeiCaptureEnabled
        readNullableInt(), // aeiMaxNum
    )

    private fun DataInput.readBackgroundActivityConfig() = BackgroundActivityRemoteConfig(
        readNullableFloat(), // threshold
    )

    private fun DataInput.readNetworkSpanForwardingConfig() = NetworkSpanForwardingRemoteConfig(
        readNullableFloat(), // pctEnabled
    )

    private fun DataInput.readOtelKotlinSdkConfig() = OtelKotlinSdkConfig(
        readNullableFloat(), // pctEnabled
    )

    private fun DataInput.readUserSessionConfig() = UserSessionRemoteConfig(
        readNullableInt(), // maxDurationSeconds
        readNullableInt(), // inactivityTimeoutSeconds
    )

    fun DataInput.readStringSet(): Set<String>? = readCollection(::LinkedHashSet) { readUTF() }

    inline fun <E, C : MutableCollection<E>> DataInput.readCollection(factory: () -> C, readItem: DataInput.() -> E): C? {
        val count = readInt()
        if (count == -1) {
            return null
        }

        val collection = factory()
        repeat(count) {
            collection.add(readItem())
        }
        return collection
    }

    fun DataInput.readStringIntMap(): Map<String, Int>? {
        val count = readInt()
        if (count == -1) {
            return null
        }

        val map = LinkedHashMap<String, Int>(count)
        repeat(count) {
            map[readUTF()] = readInt()
        }
        return map
    }

    inline fun <T> DataInput.readNullable(reader: DataInput.() -> T): T? =
        if (readBoolean()) reader() else null

    fun DataInput.readNullableInt(): Int? = readNullable { readInt() }

    fun DataInput.readNullableLong(): Long? = readNullable { readLong() }

    fun DataInput.readNullableFloat(): Float? = readNullable { readFloat() }

    fun DataInput.readNullableBoolean(): Boolean? = readNullable { readBoolean() }

    data class Header(
        val version: Int,
        val threshold: Int?,
        val deviceId: String,
    )
}
