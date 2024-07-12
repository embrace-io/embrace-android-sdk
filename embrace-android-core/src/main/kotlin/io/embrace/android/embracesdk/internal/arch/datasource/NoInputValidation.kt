package io.embrace.android.embracesdk.internal.arch.datasource

/**
 * Syntactic sugar for when no input validation is required. Developers must explicitly state
 * this is the case.
 */
public val NoInputValidation: () -> Boolean = { true }
