package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.payload.JsException
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.toUTF8String

/**
 * Holds JS exception details. JS exceptions result in a JVM exception being thrown so we want
 * to append them to the telemetry sent for the JVM crash, rather than sending separately.
 */
internal class JsCrashServiceImpl(
    private val serializer: PlatformSerializer,
) : JsCrashService {

    private var jsException: JsException? = null

    override fun appendCrashTelemetryAttributes(attributes: TelemetryAttributes): SchemaType {
        jsException?.let { e ->
            attributes.setAttribute(
                embAndroidReactNativeCrashJsException,
                encodeToUTF8String(
                    serializer.toJson(e, JsException::class.java),
                ),
            )
            return SchemaType.ReactNativeCrash(attributes)
        }
        return SchemaType.JvmCrash(attributes)
    }

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    ) {
        jsException = JsException(name, message, type, stacktrace)
    }

    private fun encodeToUTF8String(source: String): String {
        return source.toByteArray().toUTF8String()
    }
}
