package io.embrace.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData

class FakeClassContext(
    override val currentClassData: ClassData
) : ClassContext {

    constructor(clzName: String) : this(FakeClassData(clzName))

    override fun loadClassData(className: String): ClassData? {
        error("Unsupported operation")
    }
}
