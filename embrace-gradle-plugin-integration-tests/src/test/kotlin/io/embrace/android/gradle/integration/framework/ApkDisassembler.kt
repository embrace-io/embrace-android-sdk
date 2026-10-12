package io.embrace.android.gradle.integration.framework

import com.android.tools.smali.baksmali.Baksmali
import com.android.tools.smali.baksmali.BaksmaliOptions
import com.android.tools.smali.dexlib2.DexFileFactory
import java.io.File
import java.nio.file.Files

/**
 * Disassembles selected classes from an APK's dex files into smali.
 */
internal class ApkDisassembler {

    /**
     * Returns a smali file for each class name, in the same order. Class names take the form
     * '/com/example/Foo'.
     */
    fun disassembleClasses(apkFile: File, classNames: List<String>): List<File> {
        if (!apkFile.exists()) {
            error("APK file does not exist at ${apkFile.path}")
        }
        val outDir = Files.createTempDirectory("decoded_apk").toFile()
        val descriptors = classNames.map { "L${it.removePrefix("/")};" }
        val container = DexFileFactory.loadDexContainer(apkFile, null)

        container.dexEntryNames.forEach { name ->
            val dexFile = container.getEntry(name)?.dexFile ?: return@forEach
            Baksmali.disassembleDexFile(dexFile, outDir, 1, createOptions(), descriptors)
        }
        return classNames.map { name ->
            File(outDir, "$name.smali").takeIf(File::exists) ?: error("Smali file named '$name' not found")
        }
    }

    private fun createOptions() = BaksmaliOptions().apply {
        deodex = false
        implicitReferences = false
        parameterRegisters = true
        localsDirective = true
        sequentialLabels = true
        debugInfo = true
        codeOffsets = false
        accessorComments = false
        registerInfo = 0
    }
}
