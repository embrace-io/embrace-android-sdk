package io.embrace.android.embracesdk.opentelemetry

import io.opentelemetry.api.common.AttributeKey

/**
 * [AttributeKey] defined by OpenTelemetry semantic conventions. Having these here instead of using the OpenTelemetry Java semantic
 * conventions project because there seems to be a desugaring issue when building in debug when referencing keys defined there.
 *
 * This workaround will be used until we can resolve that issue.
 */

internal val serviceName = AttributeKey.stringKey("service.name")
internal val serviceVersion = AttributeKey.stringKey("service.version")
