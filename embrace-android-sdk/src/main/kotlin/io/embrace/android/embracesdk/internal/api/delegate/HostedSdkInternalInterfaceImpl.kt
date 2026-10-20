package io.embrace.android.embracesdk.internal.api.delegate

import android.content.Context
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.ReactNativeCrash.embAndroidReactNativeCrashJsException
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.capture.metadata.RnBundleIdTracker
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.envelope.resource.EnvelopeResourceSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logs.LogExceptionType
import io.embrace.android.embracesdk.internal.payload.JsException
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.encodeToUTF8String
import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.ExceptionAttributes

internal class HostedSdkInternalInterfaceImpl(
    private val logsApi: LogsApi,
    private val configService: ConfigService,
    private val resourceSource: EnvelopeResourceSource,
    private val instrumentationRegistry: InstrumentationRegistry,
    private val serializer: PlatformSerializer,
    private val rnBundleIdTracker: Lazy<RnBundleIdTracker>,
    private val hostedSdkVersionInfo: HostedSdkVersionInfo,
    private val errorHandler: ErrorHandler,
    private val telemetryService: InternalTelemetryService,
) : EmbraceInternalInterface,
    ReactNativeInternalInterface,
    UnityInternalInterface,
    FlutterInternalInterface,
    InternalInterfaceApi {

    override val internalInterface: EmbraceInternalInterface = this
    override val reactNativeInternalInterface: ReactNativeInternalInterface = this
    override val unityInternalInterface: UnityInternalInterface = this
    override val flutterInternalInterface: FlutterInternalInterface = this

    override fun isNetworkSpanForwardingEnabled(): Boolean = errorHandler.guardSdkCode(SdkErrorType.PublicApiFail, false) {
        configService.networkSpanForwardingBehavior.isNetworkSpanForwardingEnabled()
    }

    override fun addEnvelopeResource(key: String, value: String) = record("add_envelope_resource") {
        resourceSource.add(key, value)
    }

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    ) = record("log_unhandled_js_exception") {
        val dataSource = instrumentationRegistry.findByType(JvmCrashDataSource::class)
        dataSource?.telemetryModifier = { attributes ->
            val exception = JsException(name, message, type, stacktrace)
            attributes.setAttribute(
                embAndroidReactNativeCrashJsException,
                encodeToUTF8String(
                    serializer.toJson(exception, JsException.serializer()),
                ),
            )
            SchemaType.ReactNativeCrash(attributes)
        }
    }

    override fun setJavaScriptPatchNumber(number: String?) = record("set_javascript_patch_number") {
        if (!number.isNullOrEmpty()) {
            hostedSdkVersionInfo.javaScriptPatchNumber = number
        }
    }

    override fun setReactNativeSdkVersion(version: String?) = record("set_react_native_sdk_version") {
        hostedSdkVersionInfo.hostedSdkVersion = version
    }

    override fun setReactNativeVersionNumber(version: String?) = record("set_react_native_version_number") {
        if (!version.isNullOrEmpty()) {
            hostedSdkVersionInfo.hostedPlatformVersion = version
        }
    }

    override fun setJavaScriptBundleUrl(context: Context, url: String) = record("set_javascript_bundle_url") {
        rnBundleIdTracker.value.setReactNativeBundleId(url)
    }

    override fun setUnityMetaData(
        unityVersion: String?,
        buildGuid: String?,
        unitySdkVersion: String?,
    ) = record("set_unity_metadata") {
        if (unityVersion == null || buildGuid == null) {
            val sdkVersionMessage = unitySdkVersion ?: "null or previous than 1.7.5"
            errorHandler.log(
                "Unity metadata is corrupted or malformed. Unity version is " +
                    unityVersion + ", Unity build id is " + buildGuid +
                    " and Unity SDK version is " + sdkVersionMessage,
                severity = ErrorHandler.Severity.ERROR,
            )
        } else if (unitySdkVersion != null) {
            hostedSdkVersionInfo.batch {
                hostedSdkVersionInfo.hostedPlatformVersion = unityVersion
                hostedSdkVersionInfo.hostedSdkVersion = unitySdkVersion
                hostedSdkVersionInfo.unityBuildIdNumber = buildGuid
            }
        }
    }

    override fun logUnhandledUnityException(name: String, message: String, stacktrace: String?) =
        record("log_unhandled_unity_exception") {
            logUnityException(name, message, stacktrace, LogExceptionType.UNHANDLED)
        }

    override fun logHandledUnityException(name: String, message: String, stacktrace: String?) =
        record("log_handled_unity_exception") {
            logUnityException(name, message, stacktrace, LogExceptionType.HANDLED)
        }

    private fun logUnityException(
        name: String?,
        message: String,
        stacktrace: String?,
        exceptionType: LogExceptionType,
    ) {
        val attrs = mutableMapOf(
            "emb.type" to "sys.exception",
            EmbSessionAttributes.EMB_PRIVATE_SEND_MODE to "immediate",
            ExceptionAttributes.EXCEPTION_MESSAGE to message,
        )

        // add exception name + message attrs
        name?.let { attrs[ExceptionAttributes.EXCEPTION_TYPE] = it }
        stacktrace?.let { attrs[ExceptionAttributes.EXCEPTION_STACKTRACE] = it }

        if (exceptionType != LogExceptionType.NONE) {
            attrs[EmbAndroidAttributes.EMB_EXCEPTION_HANDLING] = exceptionType.value
        }

        logsApi.logMessage(
            severity = Severity.ERROR,
            message = "Unity exception",
            properties = attrs,
        )
    }

    override fun setEmbraceFlutterSdkVersion(version: String?) = record("set_embrace_flutter_sdk_version") {
        if (version != null) {
            hostedSdkVersionInfo.hostedSdkVersion = version
        }
    }

    override fun setDartVersion(version: String?) = record("set_dart_version") {
        if (version != null) {
            hostedSdkVersionInfo.hostedPlatformVersion = version
        }
    }

    /**
     * Records usage of the internal API [name] then performs [call], reporting anything it throws as an SDK error.
     */
    private fun record(name: String, call: () -> Unit) = errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
        telemetryService.onPublicApiCalled(name)
        call()
    }
}
