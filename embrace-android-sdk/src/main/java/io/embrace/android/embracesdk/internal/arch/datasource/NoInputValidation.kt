package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Syntactic sugar for when no input validation is required. Developers must explicitly state
 * this is the case.
 */
@InternalApi
public val NoInputValidation: () -> Boolean = { true }
