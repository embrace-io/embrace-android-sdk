package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

internal class NativeCrashDataSourceImpl(
    private val nativeCrashProcessor: NativeCrashProcessor,
    private val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller,
    private val args: InstrumentationArgs,
) : NativeCrashDataSource, DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy,
    instrumentationName = "native_crash_data_source",
) {
    /**
     * Every crash loaded in a process shares the same symbol map instance
     */
    private var serializedSymbols: Pair<Map<String, String>, String>? = null

    override fun onDataCaptureDisabled() {
        nativeCrashHandlerInstaller.disable()
    }

    override fun getNativeCrashes(): List<NativeCrashData> = nativeCrashProcessor.getNativeCrashes()

    override fun sendNativeCrash(
        nativeCrash: NativeCrashData,
        userSessionProperties: Map<String, String>,
        metadata: Map<String, String>,
    ) {
        captureTelemetry {
            val fullCrash = nativeCrashProcessor.loadNativeCrash(nativeCrash) ?: return@captureTelemetry
            val nativeCrashNumber = args.ordinalStore.incrementAndGet(Ordinal.NATIVE_CRASH)
            val crashAttributes = TelemetryAttributes().apply {
                setAttribute(
                    key = EmbSessionAttributes.EMB_SESSION_PART_ID,
                    value = fullCrash.sessionPartId,
                )

                setAttribute(
                    key = EmbSessionAttributes.EMB_USER_SESSION_ID,
                    value = fullCrash.userSessionId,
                )

                metadata.forEach { attribute ->
                    setAttribute(attribute.key, attribute.value)
                }

                setAttribute(
                    key = EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER,
                    value = nativeCrashNumber.toString(),
                    keepBlankishValues = false,
                )

                fullCrash.crash?.let { crashData ->
                    setAttribute(
                        key = EmbType.System.NativeCrash.embNativeCrashException,
                        value = crashData,
                        keepBlankishValues = false,
                    )
                }

                val symbols = fullCrash.symbols
                if (!symbols.isNullOrEmpty()) {
                    setAttribute(
                        EmbType.System.NativeCrash.embNativeCrashSymbols,
                        serializeSymbols(symbols),
                        keepBlankishValues = false,
                    )
                }

                userSessionProperties.forEach {
                    setAttribute(key = it.key, it.value)
                }
            }
            addLog(
                schemaType = SchemaType.NativeCrash(crashAttributes),
                severity = LogSeverity.ERROR,
                message = "",
                timestampMs = fullCrash.timestamp,
            )
        }
    }

    private fun serializeSymbols(symbols: Map<String, String>): String {
        serializedSymbols?.let { (map, json) ->
            if (map === symbols) {
                return json
            }
        }
        return args.serializer.toJson(symbols, MapSerializer(String.serializer(), String.serializer())).also {
            serializedSymbols = symbols to it
        }
    }

    override fun deleteAllNativeCrashes() {
        nativeCrashProcessor.deleteAllNativeCrashes()
    }

    override fun deleteNativeCrash(nativeCrash: NativeCrashData) {
        nativeCrashProcessor.deleteNativeCrash(nativeCrash)
    }
}
