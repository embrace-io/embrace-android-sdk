package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Test-only shims that read the new flat [EnvelopeResource.attributes] map using the explicit keys in the old format.
 * Added so existing tests that assert the envelope won't have to change
 */

internal val EnvelopeResource.appFramework: AppFramework?
    get() = attributes["app_framework"]?.intOrNull?.let(AppFramework::fromInt)

internal val EnvelopeResource.appVersion: String?
    get() = attributes["service.version"]?.contentOrNull

internal val EnvelopeResource.hostedSdkVersion: String?
    get() = attributes["hosted_sdk_version"]?.contentOrNull

internal val EnvelopeResource.hostedPlatformVersion: String?
    get() = attributes["hosted_platform_version"]?.contentOrNull

internal val EnvelopeResource.javascriptPatchNumber: String?
    get() = attributes["javascript_patch_number"]?.contentOrNull

internal val EnvelopeResource.unityBuildId: String?
    get() = attributes["unity_build_id"]?.contentOrNull

internal val EnvelopeResource.reactNativeBundleId: String?
    get() = attributes["react_native_bundle_id"]?.contentOrNull

internal val EnvelopeResource.sdkVersion: String?
    get() = attributes["telemetry.distro.version"]?.contentOrNull

internal val EnvelopeResource.osVersion: String?
    get() = attributes["os.version"]?.contentOrNull

internal val EnvelopeResource.osName: String?
    get() = attributes["os.name"]?.contentOrNull

internal val EnvelopeResource.deviceModel: String?
    get() = attributes["device.model.identifier"]?.contentOrNull
