package io.embrace.bytecode.fakes

import io.embrace.bytecode.ASM_API_VERSION
import org.objectweb.asm.ClassVisitor

class TestClassVisitor(api: Int = ASM_API_VERSION) : ClassVisitor(api)
