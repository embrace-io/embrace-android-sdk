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

    private fun sendNativeCrash(nativeCrash: NativeCrashData) {
        val nativeCrashNumber = preferencesService.incrementAndGetNativeCrashNumber()
        val crashAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionPropertiesService::getProperties
        )
        crashAttributes.setAttribute(SessionIncubatingAttributes.SESSION_ID, nativeCrash.sessionId)
        crashAttributes.setAttribute(embCrashNumber, nativeCrashNumber.toString())
        nativeCrash.crash?.let { crashAttributes.setAttribute(EmbType.System.NativeCrash.embNativeCrashException, it) }
        val nativeErrorsJson = serializer.toJson(nativeCrash.errors, errorSerializerType)
        crashAttributes.setAttribute(EmbType.System.NativeCrash.embNativeCrashErrors, nativeErrorsJson.toByteArray().toUTF8String())
        val nativeSymbolsJson = serializer.toJson(nativeCrash.symbols, Map::class.java)
        crashAttributes.setAttribute(EmbType.System.NativeCrash.embNativeCrashSymbols, nativeSymbolsJson)
        crashAttributes.setAttribute(EmbType.System.NativeCrash.embNativeCrashUnwindError, nativeCrash.unwindError.toString())

        logWriter.addLog(SchemaType.NativeCrash(crashAttributes), Severity.ERROR.toOtelSeverity(), "")
    }

    private companion object {
        private val errorSerializerType = TypeUtils.typedList(NativeCrashDataError::class)
    }
}
