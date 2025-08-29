package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.config.USE_KOTLIN_SDK
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.creator.createCompatObjectCreator
import io.embrace.opentelemetry.kotlin.creator.createObjectCreator

/**
 * ObjectCreator based on the Kotlin SDK
 */
@OptIn(ExperimentalApi::class)
val sdkObjectCreator = createObjectCreator()

/**
 * ObjectCreator based on the Java SDK
 */
@OptIn(ExperimentalApi::class)
val fakeCompatObjectCreator = createCompatObjectCreator()

/**
 * ObjectCreator based on the current SDK in use
 */
@OptIn(ExperimentalApi::class)
val fakeObjectCreator = if (USE_KOTLIN_SDK) {
    sdkObjectCreator
} else {
    fakeCompatObjectCreator
}
