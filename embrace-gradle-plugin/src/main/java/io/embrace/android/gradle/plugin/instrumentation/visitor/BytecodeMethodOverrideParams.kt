package io.embrace.android.gradle.plugin.instrumentation.visitor

/**
 * The parameters that should be used to insert an override into a class that calls to super.
 */
data class BytecodeMethodOverrideParams(

    /**
     * The fully qualified class name containing the method to be instrumented.
     */
    val owner: String,

    /**
     * The function name that will be instrumented.
     */
    val name: String,

    /**
     * The type signature of the function to be instrumented.
     */
    val descriptor: String,
)
