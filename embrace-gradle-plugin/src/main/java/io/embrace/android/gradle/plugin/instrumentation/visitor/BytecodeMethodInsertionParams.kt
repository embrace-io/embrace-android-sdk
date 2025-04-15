package io.embrace.android.gradle.plugin.instrumentation.visitor

/**
 * The parameters that should be used to insert a function call into the body of a method.
 */
internal data class BytecodeMethodInsertionParams(

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

    /**
     * The starting index of the local variable on the operand stack. We assume that by default the bytecode
     * entrypoint will take the current object as its first parameter, then all other parameters.
     *
     * For example, an instrumentation entrypoint for a View.OnClickListener would have the following
     * signature: instrumentedMethodName(android.view.View.OnClickListener thiz, android.view.View view).
     *
     * In future we can revisit this decision, given that the current object is not usually used.
     */
    val startVarIndex: Int = 0,
)
