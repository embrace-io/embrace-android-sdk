package io.embrace.android.gradle.plugin.instrumentation.fakes

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import org.objectweb.asm.ClassVisitor

class TestClassVisitor(api: Int = ASM_API_VERSION) : ClassVisitor(api)
