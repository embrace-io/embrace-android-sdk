package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.incubating.DeviceIncubatingAttributes
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes
import org.junit.Assert

internal fun Resource.assertExpectedAttributes(
    serviceName: String,
    serviceVersion: String,
    systemInfo: SystemInfo
) {
    Assert.assertEquals(serviceName, getAttribute(ServiceIncubatingAttributes.SERVICE_NAME))
    Assert.assertEquals(serviceVersion, getAttribute(ServiceIncubatingAttributes.SERVICE_VERSION))
    Assert.assertEquals(systemInfo.osName, getAttribute(OsIncubatingAttributes.OS_NAME))
    Assert.assertEquals(systemInfo.osVersionName, getAttribute(OsIncubatingAttributes.OS_DESCRIPTION))
    Assert.assertEquals(systemInfo.osType, getAttribute(OsIncubatingAttributes.OS_TYPE))
    Assert.assertEquals(systemInfo.osVersion, getAttribute(OsIncubatingAttributes.OS_VERSION))
    Assert.assertEquals(systemInfo.osBuild, getAttribute(OsIncubatingAttributes.OS_BUILD_ID))
    Assert.assertEquals(systemInfo.deviceManufacturer, getAttribute(DeviceIncubatingAttributes.DEVICE_MANUFACTURER))
    Assert.assertEquals(systemInfo.deviceModel, getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_IDENTIFIER))
    Assert.assertEquals(systemInfo.deviceModel, getAttribute(DeviceIncubatingAttributes.DEVICE_MODEL_NAME))
}
