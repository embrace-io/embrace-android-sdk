package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.fakes.FakeMutableAttributeContainer
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.IncubatingApi
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
internal class OtelSdkConfigTest {

    @Test
    fun `check resource attributes`() {
        val systemInfo = SystemInfo(
            osName = "testOS",
            osType = "testOsType",
            osBuild = "testBuild",
            osVersion = "testOsVersionName",
            androidOsApiLevel = "99",
            deviceManufacturer = "testManufacturer",
            deviceModel = "testModel"
        )

        val configuration = OtelSdkConfig(
            spanSink = SpanSinkImpl(),
            logSink = LogSinkImpl(),
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "2.5.1",
            packageName = "com.test.app",
            systemInfo = systemInfo
        )

        val attrs = FakeMutableAttributeContainer().apply(configuration.resourceAction).attributes
        val expected = mapOf(
            ServiceAttributes.SERVICE_NAME to configuration.packageName,
            ServiceAttributes.SERVICE_VERSION to configuration.appVersion,
            TelemetryAttributes.TELEMETRY_DISTRO_NAME to configuration.sdkName,
            TelemetryAttributes.TELEMETRY_DISTRO_VERSION to configuration.sdkVersion,
            OsAttributes.OS_NAME to systemInfo.osName,
            OsAttributes.OS_VERSION to systemInfo.osVersion,
            OsAttributes.OS_TYPE to systemInfo.osType,
            OsAttributes.OS_BUILD_ID to systemInfo.osBuild,
            AndroidAttributes.ANDROID_OS_API_LEVEL to systemInfo.androidOsApiLevel,
            DeviceAttributes.DEVICE_MANUFACTURER to systemInfo.deviceManufacturer,
            DeviceAttributes.DEVICE_MODEL_IDENTIFIER to systemInfo.deviceModel,
            DeviceAttributes.DEVICE_MODEL_NAME to systemInfo.deviceModel
        ).mapKeys { it.key }
        assertEquals(expected, attrs)
    }
}
