package io.embrace.android.gradle.plugin.instrumentation.fakes

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData

class TestClassContext(override val currentClassData: ClassData) : ClassContext {

    override fun loadClassData(className: String): ClassData? {
        error("Classloading not implemented")
    }
}
