package io.embrace.android.gradle.integration.framework

import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.commands.BuildApksCommand
import java.io.File
import java.util.zip.ZipFile

class BundleToolApkBuilder {

    private val aapt2File = File("${System.getenv("ANDROID_HOME")}/build-tools/35.0.0/aapt2")

    /**
     * Generates a universal APK from the given bundle file.
     * Returns the generated APK file, or null if generation fails.
     */
    fun generateApkFromBundle(bundleFile: File): File {
        if (!aapt2File.exists()) {
            error(
                "Please set the ANDROID_BUILD_TOOLS_HOME environment variable. This is needed in order to use bundletool."
            )
        }
        if (!bundleFile.exists()) {
            error("Bundle file does not exist at ${bundleFile.path}")
        }

        val outputFile = File.createTempFile("generated-apks", ".apks")

        BuildApksCommand
            .builder()
            .setBundlePath(bundleFile.toPath())
            .setOverwriteOutput(true)
            .setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2File.toPath()))
            .setOutputFile(outputFile.toPath())
            .setApkBuildMode(BuildApksCommand.ApkBuildMode.UNIVERSAL)
            .build()
            .execute()

        return extractApkFromApks(outputFile).also {
            outputFile.delete()
        }
    }

    /**
     * Extracts a universal APK from the given .apks file.
     * Returns the extracted APK file, or null if extraction fails.
     */
    private fun extractApkFromApks(apksFile: File): File {
        val outputFile = File.createTempFile("generated-apk", ".apk")
        ZipFile(apksFile).use { zip ->
            zip.entries().asSequence()
                .find { it.name == "universal.apk" }
                ?.let { entry ->
                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }

        return outputFile
    }
}
