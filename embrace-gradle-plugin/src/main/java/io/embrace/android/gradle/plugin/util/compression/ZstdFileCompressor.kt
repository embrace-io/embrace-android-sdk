package io.embrace.android.gradle.plugin.util.compression

import com.github.luben.zstd.ZstdOutputStream
import io.embrace.android.gradle.plugin.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ZstdFileCompressor : FileCompressor {
    private val logger = Logger(ZstdFileCompressor::class.java)

    override fun compress(inputFile: File, outputFile: File): File? {
        return try {
            val parent = outputFile.parentFile ?: return null
            if (!parent.exists()) {
                parent.mkdirs()
            }
            ZstdOutputStream(FileOutputStream(outputFile)).use { outStream ->
                FileInputStream(inputFile).use { stdout ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var n: Int
                    while (stdout.read(buffer).also { n = it } > -1) {
                        outStream.write(buffer, 0, n)
                    }
                    return outputFile
                }
            }
        } catch (e: IOException) {
            logger.error("Error compressing ${inputFile.path} to ${outputFile.path}", e)
            null
        }
    }
}

private const val BUFFER_SIZE = 16384
