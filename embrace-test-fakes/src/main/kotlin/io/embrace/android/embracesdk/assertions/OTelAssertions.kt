package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes
import org.junit.Assert.assertEquals

fun Resource.assertExpectedAttributes(
    expectedServiceName: String,
    expectedServiceVersion: String,
    systemInfo: SystemInfo
) {
    assertEquals(expectedServiceName, getAttribute(ServiceAttributes.SERVICE_NAME))
    assertEquals(expectedServiceVersion, getAttribute(ServiceAttributes.SERVICE_VERSION))
    assertEquals(expectedServiceName, getAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME))
    assertEquals(expectedServiceVersion, getAttribute(TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION))
    assertEquals(systemInfo.osName, getAttribute(OsIncubatingAttributes.OS_NAME))
    assertEquals(systemInfo.osVersion, getAttribute(OsIncubatingAttributes.OS_VERSION))
    assertEquals(systemInfo.osType, getAttribute(OsIncubatingAttributes.OS_TYPE))
    assertEquals(systemInfo.osBuild, getAttribute(OsIncubatingAttributes.OS_BUILD_ID))
    assertEquals(systemInfo.androidOsApiLevel, getAttribute(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL))
    assertEquals(systemInfo.deviceManufacturer, getAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER))
    assertEquals(systemInfo.deviceModel, getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER))
    assertEquals(systemInfo.deviceModel, getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME))
}

fun SpanData.assertHasEmbraceAttribute(key: EmbraceAttributeKey, value: String) {
    assertEquals(value, attributes.get(AttributeKey.stringKey(key.name)))
}
