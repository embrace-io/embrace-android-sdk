package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.incubating.AndroidIncubatingAttributes
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes
import org.junit.Assert.assertEquals

internal fun Resource.assertExpectedAttributes(
    serviceName: String,
    serviceVersion: String,
    systemInfo: SystemInfo
) {
    assertEquals(serviceName, getAttribute(ServiceIncubatingAttributes.SERVICE_NAME))
    assertEquals(serviceVersion, getAttribute(ServiceIncubatingAttributes.SERVICE_VERSION))
    assertEquals(systemInfo.osName, getAttribute(OsIncubatingAttributes.OS_NAME))
    assertEquals(systemInfo.osVersion, getAttribute(OsIncubatingAttributes.OS_VERSION))
    assertEquals(systemInfo.osType, getAttribute(OsIncubatingAttributes.OS_TYPE))
    assertEquals(systemInfo.osBuild, getAttribute(OsIncubatingAttributes.OS_BUILD_ID))
    assertEquals(systemInfo.androidOsApiLevel, getAttribute(AndroidIncubatingAttributes.ANDROID_OS_API_LEVEL))
    assertEquals(systemInfo.deviceManufacturer, getAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER))
    assertEquals(systemInfo.deviceModel, getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER))
    assertEquals(systemInfo.deviceModel, getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME))
}
