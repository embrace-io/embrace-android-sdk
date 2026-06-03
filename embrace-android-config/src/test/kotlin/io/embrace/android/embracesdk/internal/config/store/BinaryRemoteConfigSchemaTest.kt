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
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/**
 * Guards the binary codec against silent drift. The binary format is positional, so any change to
 * the fields of [RemoteConfig] or any nested type it encodes alters the wire layout. This test
 * fingerprints those types via reflection and compares against [GOLDEN_SCHEMA].
 *
 * When it fails because the layout legitimately changed:
 *  1. update [BinaryRemoteConfigEncoder] and [BinaryRemoteConfigDecoder] to match,
 *  2. bump [BinaryRemoteConfigFormat.VERSION] (old caches then fall back to JSON), and
 *  3. replace [GOLDEN_SCHEMA] below with the printed actual schema.
 *
 * The schema embeds the version, so a `VERSION` bump and a schema update land in the same diff.
 * Reflection lives only in this test — the production load path stays reflection-free.
 */
internal class BinaryRemoteConfigSchemaTest {

    @Test
    fun `binary schema matches the golden schema`() {
        assertEquals(
            "RemoteConfig's binary layout changed. Update the encoder + decoder, bump " +
                "BinaryRemoteConfigFormat.VERSION (old caches fall back to JSON), and replace " +
                "GOLDEN_SCHEMA with the actual schema shown here.",
            GOLDEN_SCHEMA.trim(),
            actualSchema().trim(),
        )
    }

    private fun actualSchema(): String = buildString {
        append("version: ").append(BinaryRemoteConfigFormat.VERSION)
        CODEC_TYPES.forEach { type ->
            append('\n').append(type.simpleName).append(':')
            type.primaryConstructor!!.parameters.forEach { param ->
                append("\n  ").append(param.name).append(": ").append(render(param.type))
            }
        }
    }

    private fun render(type: KType): String {
        val name = (type.classifier as? KClass<*>)?.simpleName ?: type.toString()
        val args =
            if (type.arguments.isEmpty()) {
                ""
            } else {
                type.arguments.joinToString(separator = ", ", prefix = "<", postfix = ">") {
                    it.type?.let(::render) ?: "*"
                }
            }
        return name + args + if (type.isMarkedNullable) "?" else ""
    }

    private companion object {

        /**
         * Every type the binary codec touches, in encoding order. A nested type added to the codec
         * must be added here too, or its fields won't be guarded.
         */
        val CODEC_TYPES: List<KClass<*>> = listOf(
            RemoteConfig::class,
            NetworkCaptureRuleRemoteConfig::class,
            UiRemoteConfig::class,
            NetworkRemoteConfig::class,
            SessionRemoteConfig::class,
            LogRemoteConfig::class,
            ThreadBlockageRemoteConfig::class,
            DataRemoteConfig::class,
            KillSwitchRemoteConfig::class,
            AppExitInfoConfig::class,
            BackgroundActivityRemoteConfig::class,
            NetworkSpanForwardingRemoteConfig::class,
            OtelKotlinSdkConfig::class,
            UserSessionRemoteConfig::class,
        )

        val GOLDEN_SCHEMA = """
            version: 1
            RemoteConfig:
              threshold: Int?
              disabledEventAndLogPatterns: Set<String>?
              disabledUrlPatterns: Set<String>?
              networkCaptureRules: Set<NetworkCaptureRuleRemoteConfig>?
              uiConfig: UiRemoteConfig?
              networkConfig: NetworkRemoteConfig?
              sessionConfig: SessionRemoteConfig?
              logConfig: LogRemoteConfig?
              threadBlockageRemoteConfig: ThreadBlockageRemoteConfig?
              dataConfig: DataRemoteConfig?
              killSwitchConfig: KillSwitchRemoteConfig?
              internalExceptionCaptureEnabled: Boolean?
              appExitInfoConfig: AppExitInfoConfig?
              backgroundActivityConfig: BackgroundActivityRemoteConfig?
              maxUserSessionProperties: Int?
              networkSpanForwardingRemoteConfig: NetworkSpanForwardingRemoteConfig?
              uiLoadInstrumentationEnabled: Boolean?
              otelKotlinSdkConfig: OtelKotlinSdkConfig?
              pctStateCaptureEnabledV2: Float?
              pctNetworkCallbackConnectivityServiceEnabled: Float?
              pctNavigationStateCaptureEnabled: Float?
              userSession: UserSessionRemoteConfig?
            NetworkCaptureRuleRemoteConfig:
              id: String
              duration: Long?
              method: String
              urlRegex: String
              expiresIn: Long
              maxSize: Long
              maxCount: Int
              statusCodes: Set<Int>
            UiRemoteConfig:
              breadcrumbs: Int?
              taps: Int?
              webViews: Int?
              fragments: Int?
            NetworkRemoteConfig:
              defaultCaptureLimit: Int?
              domainLimits: Map<String, Int>?
            SessionRemoteConfig:
              isEnabled: Boolean?
            LogRemoteConfig:
              logMessageMaximumAllowedLength: Int?
              logInfoLimit: Int?
              logWarnLimit: Int?
              logErrorLimit: Int?
            ThreadBlockageRemoteConfig:
              pctEnabled: Int?
              sampleIntervalMs: Long?
              maxStacktracesPerInterval: Int?
              stacktraceFrameLimit: Int?
              intervalsPerSession: Int?
              minDuration: Int?
              monitorThreadPriority: Int?
            DataRemoteConfig:
              pctThermalStatusEnabled: Float?
            KillSwitchRemoteConfig:
              sigHandlerDetection: Boolean?
              jetpackCompose: Boolean?
            AppExitInfoConfig:
              appExitInfoTracesLimit: Int?
              pctAeiCaptureEnabled: Float?
              aeiMaxNum: Int?
            BackgroundActivityRemoteConfig:
              threshold: Float?
            NetworkSpanForwardingRemoteConfig:
              pctEnabled: Float?
            OtelKotlinSdkConfig:
              pctEnabled: Float?
            UserSessionRemoteConfig:
              maxDurationSeconds: Int?
              inactivityTimeoutSeconds: Int?
        """.trimIndent()
    }
}
