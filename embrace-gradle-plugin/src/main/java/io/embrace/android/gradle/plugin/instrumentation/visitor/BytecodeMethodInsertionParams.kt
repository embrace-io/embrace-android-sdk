package io.embrace.android.gradle.plugin.instrumentation.visitor

/**
 * The parameters that should be used to insert a function call into the body of a method.
 */
data class BytecodeMethodInsertionParams(

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
     * Declares the indices of variables on the operand stack that should be added as part of the inserted call.
     * This MUST be entered in ascending order.
     *
     * Virtual methods have an implicit reference as the 0th value on the stack, whereas static methods do not.
     * After this point, the stack should contain the ordered arguments to the containing method.
     *
     * As an example, listOf(1, 3) should be supplied if we wanted to pass the String and Boolean parameters of this virtual method:
     * foo(String, Int, Boolean)
     */
    val operandStackIndices: List<Int>,
)
