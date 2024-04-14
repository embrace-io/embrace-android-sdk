package io.embrace.android.embracesdk.opentelemetry

import io.opentelemetry.api.common.AttributeKey

/**
 * [AttributeKey] defined by OpenTelemetry semantic conventions. Having these here instead of using the OpenTelemetry Java semantic
 * conventions project because there seems to be a desugaring issue when building in debug when referencing keys defined there.
 *
 * This workaround will be used until we can resolve that issue.
 */

internal val androidApiLevel: AttributeKey<String> = AttributeKey.stringKey("android.os.api_level")
internal val androidState: AttributeKey<String> = AttributeKey.stringKey("android.state")

internal val deviceManufacturer: AttributeKey<String> = AttributeKey.stringKey("device.manufacturer")
internal val deviceModelIdentifier: AttributeKey<String> = AttributeKey.stringKey("os.model.identifier")
internal val deviceModelName: AttributeKey<String> = AttributeKey.stringKey("os.model.name")

internal val logRecordUid: AttributeKey<String> = AttributeKey.stringKey("log.record.uid")

internal val osName: AttributeKey<String> = AttributeKey.stringKey("os.name")
internal val osVersion: AttributeKey<String> = AttributeKey.stringKey("os.version")
internal val osType: AttributeKey<String> = AttributeKey.stringKey("os.type")
internal val osBuildId: AttributeKey<String> = AttributeKey.stringKey("os.build_id")

internal val serviceName: AttributeKey<String> = AttributeKey.stringKey("service.name")
internal val serviceVersion: AttributeKey<String> = AttributeKey.stringKey("service.version")

internal val exceptionMessage: AttributeKey<String> = AttributeKey.stringKey("exception.message")
internal val exceptionStacktrace: AttributeKey<String> = AttributeKey.stringKey("exception.stacktrace")
internal val exceptionType: AttributeKey<String> = AttributeKey.stringKey("exception.type")
