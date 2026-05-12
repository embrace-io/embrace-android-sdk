package io.embrace.android.exampleapp.di

/**
 * Marker for the Metro dependency-graph scope that lives as long as the [android.app.Application].
 * Bindings annotated with `@SingleIn(AppScope::class)` are singletons within that lifetime.
 */
abstract class AppScope private constructor()
