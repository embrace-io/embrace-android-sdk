package io.embrace.android.embracesdk.internal.ndk

import io.embrace.android.embracesdk.internal.arch.attrs.embCrashNumber
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes

internal class NativeCrashDataSourceImpl(
    private val nativeCrashProcessor: NativeCrashProcessor,
    private val preferencesService: PreferencesService,
    destination: TelemetryDestination,
    private val serializer: PlatformSerializer,
    logger: EmbLogger,
) : NativeCrashDataSource, DataSourceImpl(
    destination = destination,
    logger = logger,
    limitStrategy = NoopLimitStrategy,
) {
    override fun getAndSendNativeCrash(): NativeCrashData? {
        return nativeCrashProcessor.getLatestNativeCrash()?.apply {
            sendNativeCrash(nativeCrash = this, sessionProperties = emptyMap(), metadata = emptyMap())
        }
    }

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashProcessor.getNativeCrashes()

    @OptIn(IncubatingApi::class)
    override fun sendNativeCrash(
        nativeCrash: NativeCrashData,
        sessionProperties: Map<String, String>,
        metadata: Map<String, String>,
    ) {
        captureTelemetry {
            val nativeCrashNumber = preferencesService.incrementAndGetNativeCrashNumber()
            val crashAttributes = TelemetryAttributes(
                sessionPropertiesProvider = { sessionProperties }
            )
            crashAttributes.setAttribute(
                key = SessionAttributes.SESSION_ID,
                value = nativeCrash.sessionId,
                keepBlankishValues = false,
            )

            metadata.forEach { attribute ->
                crashAttributes.setAttribute(attribute.key, attribute.value)
            }

            crashAttributes.setAttribute(
                key = embCrashNumber,
                value = nativeCrashNumber.toString(),
                keepBlankishValues = false,
            )

            nativeCrash.crash?.let { crashData ->
                crashAttributes.setAttribute(
                    key = EmbType.System.NativeCrash.embNativeCrashException,
                    value = crashData,
                    keepBlankishValues = false,
                )
            }

            if (!nativeCrash.symbols.isNullOrEmpty()) {
                serializer.toJson(nativeCrash.symbols, Map::class.java).let { nativeSymbolsJson ->
                    crashAttributes.setAttribute(
                        EmbType.System.NativeCrash.embNativeCrashSymbols,
                        nativeSymbolsJson,
                        keepBlankishValues = false,
                    )
                }
            }
            addLog(
                schemaType = SchemaType.NativeCrash(crashAttributes),
                severity = LogSeverity.ERROR,
                message = "",
                addCurrentSessionInfo = false,
                timestampMs = nativeCrash.timestamp
            )
        }
    }

    override fun deleteAllNativeCrashes() {
        nativeCrashProcessor.deleteAllNativeCrashes()
    }
}
