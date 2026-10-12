package io.embrace.gradle.plugin.instrumentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * A ViewModel that calls each of ViewModel's public constructors.
 */
@Suppress("unused")
class FixtureViewModel : ViewModel {

    constructor() : super()

    constructor(scope: CoroutineScope) : super(scope)

    constructor(closeable: AutoCloseable) : super(closeable)

    constructor(scope: CoroutineScope, closeable: AutoCloseable) : super(scope, closeable)
}
