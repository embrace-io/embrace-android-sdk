package io.embrace.android.gradle.plugin.instrumentation.visitor

/**
 * Parameters used to detect that a method in a class is a target for bytecode instrumentation.
 */
internal data class BytecodeClassInsertionParams(

    /**
     * The function name that will be targeted for bytecode instrumentation.
     */
    val name: String,

    /**
     * The type signature of the function that will be targeted for bytecode instrumentation.
     */
    val descriptor: String,
)
