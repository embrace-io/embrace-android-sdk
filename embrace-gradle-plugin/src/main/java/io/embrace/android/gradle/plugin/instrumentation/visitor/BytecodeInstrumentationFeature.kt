package io.embrace.android.gradle.plugin.instrumentation.visitor

import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassVisitStrategy

/**
 * Declares all the necessary information for performing bytecode instrumentation on a specific feature.
 */
data class BytecodeInstrumentationFeature(

    /**
     * The name of the feature.
     */
    val name: String,

    /**
     * Instructions for identifying a target method in the class that will be instrumented.
     */
    val targetParams: BytecodeClassInsertionParams,

    /**
     * Instructions for how to insert bytecode into the target method.
     */
    val insertionParams: BytecodeMethodInsertionParams,

    /**
     * Instructions for determining whether a class should be visited for instrumentation for this feature.
     */
    val visitStrategy: ClassVisitStrategy,
)
