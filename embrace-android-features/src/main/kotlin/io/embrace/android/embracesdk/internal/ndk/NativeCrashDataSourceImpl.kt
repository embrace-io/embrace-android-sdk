package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.TypeUtils
import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.NativeCrashDataError
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.spans.toOtelSeverity
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes

internal class NativeCrashDataSourceImpl(
    private val sessionPropertiesService: SessionPropertiesService,
    private val ndkService: NdkService,
    private val preferencesService: PreferencesService,
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val serializer: PlatformSerializer,
    logger: EmbLogger,
) : NativeCrashDataSource, LogDataSourceImpl(
    destination = logWriter,
    logger = logger,
    limitStrategy = NoopLimitStrategy,
) {
    override fun getAndSendNativeCrash(): NativeCrashData? {
        return ndkService.getLatestNativeCrash()?.apply {
            sendNativeCrash(this)
        }
    }

    override fun getNativeCrashes(): List<NativeCrashData> = ndkService.getNativeCrashes()

    override fun sendNativeCrash(nativeCrash: NativeCrashData) {
        val nativeCrashNumber = preferencesService.incrementAndGetNativeCrashNumber()
        val crashAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties
        )
        crashAttributes.setAttribute(
            key = SessionIncubatingAttributes.SESSION_ID,
            value = nativeCrash.sessionId,
            keepBlankishValues = false,
        )

        crashAttributes.setAttribute(
            key = embCrashNumber,
            value = nativeCrashNumber.toString(),
            keepBlankishValues = false,
        )

        nativeCrash.appState?.let { appState ->
            crashAttributes.setAttribute(
                key = embState,
                value = appState,
                keepBlankishValues = false,
            )
        }

        nativeCrash.crash?.let { crashData ->
            crashAttributes.setAttribute(
                key = EmbType.System.NativeCrash.embNativeCrashException,
                value = crashData,
                keepBlankishValues = false,
            )
        }

        if (!nativeCrash.errors.isNullOrEmpty()) {
            runCatching {
                serializer.toJson(nativeCrash.errors, errorSerializerType).let { nativeErrorsJson ->
                    crashAttributes.setAttribute(
                        key = EmbType.System.NativeCrash.embNativeCrashErrors,
                        value = nativeErrorsJson.toByteArray().toUTF8String(),
                        keepBlankishValues = false,
                    )
                }
            }
        }

        if (!nativeCrash.symbols.isNullOrEmpty()) {
            runCatching {
                serializer.toJson(nativeCrash.symbols, Map::class.java).let { nativeSymbolsJson ->
                    crashAttributes.setAttribute(
                        EmbType.System.NativeCrash.embNativeCrashSymbols,
                        nativeSymbolsJson,
                        keepBlankishValues = false,
                    )
                }
            }
        }

        if (nativeCrash.unwindError != null) {
            runCatching {
                crashAttributes.setAttribute(
                    key = EmbType.System.NativeCrash.embNativeCrashUnwindError,
                    value = nativeCrash.unwindError.toString(),
                    keepBlankishValues = false,
                )
            }
        }

        logWriter.addLog(
            schemaType = SchemaType.NativeCrash(crashAttributes),
            severity = Severity.ERROR.toOtelSeverity(),
            message = "",
            addCurrentSessionInfo = false,
            timestampMs = nativeCrash.timestamp
        )
    }

    override fun deleteAllNativeCrashes() {
        ndkService.deleteAllNativeCrashes()
    }

    private companion object {
        private val errorSerializerType = TypeUtils.typedList(NativeCrashDataError::class)
    }
}
