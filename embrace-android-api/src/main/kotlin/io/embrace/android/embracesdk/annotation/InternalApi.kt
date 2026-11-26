package io.embrace.android.embracesdk.annotation

/**
 * Classes and methods marked with this annotation are part of Embrace's Internal API
 * and may change without warning, so are used at your own risk. Please contact support@embrace.io
 * if you have a use case that cannot be fulfilled using our public API.
 */
@RequiresOptIn(
    message = "This is part of Embrace's internal API and may change without warning. " +
        "Please contact support if you have a use case that our public API does not fulfil.",
    level = RequiresOptIn.Level.ERROR,
)
public annotation class InternalApi
