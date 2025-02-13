package io.embrace.android.gradle.plugin.instrumentation.visitor

import com.android.build.api.instrumentation.ClassContext

@Suppress("UnstableApiUsage")
interface ClassVisitFilter {

    /**
     * Returns true if a visitor wants to visit a given class context.
     */
    fun accept(classContext: ClassContext): Boolean
}
