package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.opentelemetry.kotlin.semconv.AndroidAttributes
import io.opentelemetry.kotlin.semconv.DeviceAttributes
import io.opentelemetry.kotlin.semconv.HostAttributes
import io.opentelemetry.kotlin.semconv.OsAttributes
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import io.opentelemetry.kotlin.semconv.TelemetryAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

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
            deviceModel = "testModel",
            architecture = "arm64-v8a",
        )

        val configuration = OtelSdkConfig(
            spanSink = SpanSinkImpl(),
            logSink = LogSinkImpl(),
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "2.5.1",
            packageName = "com.test.app",
            systemInfo = systemInfo,
        )

        val attrs = FakeAttributesMutator().apply(configuration.resourceAction).attributes
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
            HostAttributes.HOST_ARCH to systemInfo.architecture,
            DeviceAttributes.DEVICE_MANUFACTURER to systemInfo.deviceManufacturer,
            DeviceAttributes.DEVICE_MODEL_IDENTIFIER to systemInfo.deviceModel,
            DeviceAttributes.DEVICE_MODEL_NAME to systemInfo.deviceModel,
        ).mapKeys { it.key }
        assertEquals(expected, attrs)
    }

    @Test
    fun `Embrace-set attributes cannot be overwritten when override is disabled`() {
        val config = createConfig(resourceAttributeOverrideEnabled = false).apply {
            setResourceAttribute(ServiceAttributes.SERVICE_VERSION, "customer.version")
            setResourceAttribute("custom.key", "custom.value")
        }

        val attrs = config.getResourceAttributes()
        assertEquals("2.5.1", attrs[ServiceAttributes.SERVICE_VERSION])
        assertEquals("custom.value", attrs["custom.key"])
    }

    @Test
    fun `Embrace-set attributes can be overwritten when override is enabled`() {
        val config = createConfig(resourceAttributeOverrideEnabled = true).apply {
            setResourceAttribute(ServiceAttributes.SERVICE_VERSION, "customer.version")
        }

        assertEquals("customer.version", config.getResourceAttributes()[ServiceAttributes.SERVICE_VERSION])
    }

    @Test
    fun `emb-prefixed attributes are never overridable regardless of setting`() {
        listOf(true, false).forEach { overrideEnabled ->
            val config = createConfig(resourceAttributeOverrideEnabled = overrideEnabled).apply {
                setResourceAttribute("emb.custom", "nope")
            }
            assertFalse(config.getResourceAttributes().containsKey("emb.custom"))
        }
    }

    @Test
    fun `custom attributes are capped at the first 20 added`() {
        val config = createConfig(resourceAttributeOverrideEnabled = false).apply {
            (35 downTo 10).forEach { setResourceAttribute("custom.key.$it", "v$it") }
        }

        val customKeys = config.getResourceAttributes().keys.filter { it.startsWith("custom.key.") }
        assertEquals(20, customKeys.size)
        assertEquals((35 downTo 16).map { "custom.key.$it" }, customKeys)
    }

    @Test
    fun `overriding an Embrace-set key does not count toward the cap`() {
        val config = createConfig(resourceAttributeOverrideEnabled = true).apply {
            setResourceAttribute(ServiceAttributes.SERVICE_VERSION, "customer.version")
            (1..20).forEach { setResourceAttribute("custom.key.$it", "v$it") }
        }

        val attrs = config.getResourceAttributes()
        assertEquals("customer.version", attrs[ServiceAttributes.SERVICE_VERSION])
        assertEquals(20, attrs.keys.count { it.startsWith("custom.key.") })
    }

    @Test
    fun `overridden value not taken if the feature flag is disabled`() {
        val config = createConfig(resourceAttributeOverrideEnabled = false).apply {
            setResourceAttribute(ServiceAttributes.SERVICE_VERSION, "customer.version")
            (1..20).forEach { setResourceAttribute("custom.key.$it", "v$it") }
        }

        val attrs = config.getResourceAttributes()
        assertEquals("2.5.1", attrs[ServiceAttributes.SERVICE_VERSION])
        assertEquals(20, attrs.keys.count { it.startsWith("custom.key.") })
    }

    private fun createConfig(resourceAttributeOverrideEnabled: Boolean) = OtelSdkConfig(
        spanSink = SpanSinkImpl(),
        logSink = LogSinkImpl(),
        sdkName = "sdk",
        sdkVersion = "1.0",
        appVersion = "2.5.1",
        packageName = "com.test.app",
        systemInfo = SystemInfo(),
        resourceAttributeOverrideEnabled = { resourceAttributeOverrideEnabled },
    )
}
