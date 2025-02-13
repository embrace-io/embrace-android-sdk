package io.embrace.android.gradle.plugin.tasks.registration

/**
 * Implementations of this interface are responsible for registering tasks.
 */
interface EmbraceTaskRegistration {

    /**
     * Register a task with the given parameters. It is the responsibility of the implementation
     * to use project.afterEvaluate (if required).
     */
    fun register(params: RegistrationParams)
}
