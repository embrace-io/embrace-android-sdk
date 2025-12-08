package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.payload.JsException
import io.embrace.android.embracesdk.internal.utils.encodeToUTF8String

internal class ReactNativeInternalInterfaceImpl(
    private val embrace: EmbraceImpl,
    private val impl: EmbraceInternalInterface,
    private val bootstrapper: ModuleInitBootstrapper,
    private val rnBundleIdTracker: RnBundleIdTracker,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val logger: EmbLogger,
) : EmbraceInternalInterface by impl, ReactNativeInternalInterface {

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    ) {
        if (embrace.isStarted) {
            val registry = bootstrapper.instrumentationModule.instrumentationRegistry
            val dataSource = registry.findByType(JvmCrashDataSource::class)
            dataSource?.telemetryModifier = { attributes ->
                val exception = JsException(name, message, type, stacktrace)
                val serializer = bootstrapper.initModule.jsonSerializer
                attributes.setAttribute(
                    embAndroidReactNativeCrashJsException,
                    encodeToUTF8String(
                        serializer.toJson(exception, JsException::class.java),
                    ),
                )
                SchemaType.ReactNativeCrash(attributes)
            }
        } else {
            logger.logSdkNotInitialized("log JS exception")
        }
    }

    override fun setJavaScriptPatchNumber(number: String?) {
        if (embrace.isStarted) {
            if (number.isNullOrEmpty()) {
                return
            }
            hostedSdkVersionInfo.javaScriptPatchNumber = number
        } else {
            logger.logSdkNotInitialized("set JavaScript patch number")
        }
    }

    override fun setReactNativeSdkVersion(version: String?) {
        if (embrace.isStarted) {
            hostedSdkVersionInfo.hostedSdkVersion = version
        } else {
            logger.logSdkNotInitialized("set React Native SDK version")
        }
    }

    override fun setReactNativeVersionNumber(version: String?) {
        if (embrace.isStarted) {
            if (version.isNullOrEmpty()) {
                return
            }
            hostedSdkVersionInfo.hostedPlatformVersion = version
        } else {
            logger.logSdkNotInitialized("set React Native version number")
        }
    }

    override fun setJavaScriptBundleUrl(context: Context, url: String) {
        if (embrace.isStarted) {
            rnBundleIdTracker.setReactNativeBundleId(url)
        } else {
            logger.logSdkNotInitialized("set JavaScript bundle URL")
        }
    }
}
