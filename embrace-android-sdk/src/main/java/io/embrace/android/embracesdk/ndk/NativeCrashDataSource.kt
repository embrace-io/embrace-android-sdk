package io.embrace.android.embracesdk.ndk

import com.squareup.moshi.Types
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashErrors
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashException
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashSymbols
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.NativeCrash.embNativeCrashUnwindError
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.utils.toUTF8String
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.opentelemetry.embCrashNumber
import io.embrace.android.embracesdk.opentelemetry.embSessionId
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NativeCrashDataError
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties

internal interface NativeCrashDataSource : LogDataSource, NativeCrashService

internal class NativeCrashDataSourceImpl(
    private val sessionProperties: EmbraceSessionProperties,
    private val ndkService: NdkService,
    private val preferencesService: PreferencesService,
    private val logWriter: LogWriter,
    private val configService: ConfigService,
    private val serializer: EmbraceSerializer,
    logger: EmbLogger,
) : NativeCrashDataSource, LogDataSourceImpl(
    destination = logWriter,
    logger = logger,
    limitStrategy = NoopLimitStrategy,
) {
    override fun getAndSendNativeCrash(): NativeCrashData? {
        return ndkService.getNativeCrash()?.apply {
            sendNativeCrash(this)
        }
    }
    private fun sendNativeCrash(nativeCrash: NativeCrashData) {
        val nativeCrashNumber = preferencesService.incrementAndGetNativeCrashNumber()
        val crashAttributes = TelemetryAttributes(
            configService = configService,
            sessionPropertiesProvider = sessionProperties::get
        )
        crashAttributes.setAttribute(embSessionId, nativeCrash.sessionId)
        crashAttributes.setAttribute(embCrashNumber, nativeCrashNumber.toString())
        nativeCrash.crash?.let { crashAttributes.setAttribute(embNativeCrashException, it) }
        val nativeErrorsJson = serializer.toJson(nativeCrash.errors, errorSerializerType)
        crashAttributes.setAttribute(embNativeCrashErrors, nativeErrorsJson.toByteArray().toUTF8String())
        val nativeSymbolsJson = serializer.toJson(nativeCrash.symbols, Map::class.java)
        crashAttributes.setAttribute(embNativeCrashSymbols, nativeSymbolsJson)
        crashAttributes.setAttribute(embNativeCrashUnwindError, nativeCrash.unwindError.toString())

        logWriter.addLog(SchemaType.NativeCrash(crashAttributes), Severity.ERROR, "")
    }

    companion object {
        private val errorSerializerType = Types.newParameterizedType(List::class.java, NativeCrashDataError::class.java)
    }
}
