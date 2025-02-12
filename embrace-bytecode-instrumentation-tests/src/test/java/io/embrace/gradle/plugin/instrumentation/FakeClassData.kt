package io.embrace.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassData

class FakeClassData(
    override val className: String,
    override val classAnnotations: List<String> = emptyList(),
    override val interfaces: List<String> = emptyList(),
    override val superClasses: List<String> = emptyList(),
) : ClassData
