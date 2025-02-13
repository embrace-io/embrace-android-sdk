package io.embrace.android.gradle.plugin.instrumentation.fakes

import com.android.build.api.instrumentation.ClassData

class TestClassData(
    override val className: String,
    override val classAnnotations: List<String> = emptyList(),
    override val interfaces: List<String> = emptyList(),
    override val superClasses: List<String> = emptyList()
) : ClassData
