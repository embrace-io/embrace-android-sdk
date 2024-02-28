package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.PendingApiCall
import io.embrace.android.embracesdk.internal.utils.SerializationAction
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.storage.StorageService
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A class that wraps [EmbraceCacheService] that emulates its locking semantics and largely defers to the real [EmbraceCacheService]
 * instance except when it's writing bytes.
 *
 * This admittedly is not the best way to test [EmbraceCacheService] (putting the custom logic in [StorageService] or wrapping [File] is
 * probably better) and the duplication causes some inherent brittleness, but it's good enough for now given that integration tests
 * will cover the end to end cases and should fail if issues are introduced.
 */
internal class FakeEmbraceCacheService(
    private val realEmbraceCacheService: EmbraceCacheService,
    private val realEmbraceCacheServiceStorageService: StorageService
) : CacheService by realEmbraceCacheService {
    private val fileLocks = mutableMapOf<String, ReentrantReadWriteLock>()
    private val pauseRead = AtomicBoolean(true)

    override fun <T> cacheObject(name: String, objectToCache: T, clazz: Class<T>) {
        findLock(name).write {
            realEmbraceCacheService.cacheObject(name, objectToCache, clazz)
        }
    }

    override fun <T> loadObject(name: String, clazz: Class<T>): T? =
        findLock(name).read {
            realEmbraceCacheService.loadObject(name, clazz)
        }

    override fun cacheBytes(name: String, bytes: ByteArray?) {
        findLock(name).write {
            if (name != "testfile-truncate" && name != "testfile-pause") {
                realEmbraceCacheService.cacheBytes(name, bytes)
            } else {
                if (bytes != null) {
                    val file = realEmbraceCacheServiceStorageService.getFileForWrite("emb_$name")
                    try {
                        if (name == "testfile-truncate") {
                            file.writeHalfTheBytes(bytes)
                        } else {
                            file.writeBytesWithPause(bytes)
                        }
                    } catch (ex: Exception) {
                        deleteFile(name)
                    }
                }
            }
        }
    }

    override fun cachePayload(name: String, action: SerializationAction) {
        findLock(name).write {
            realEmbraceCacheService.cachePayload(name, action)
        }
    }

    override fun writeSession(name: String, sessionMessage: SessionMessage) {
        findLock(name).write {
            realEmbraceCacheService.writeSession(name, sessionMessage)
        }
    }

    override fun loadBytes(name: String): ByteArray? =
        findLock(name).read {
            val pause = pauseRead.getAndSet(!pauseRead.get())
            if (pause && name == "testfile-read-alternating-pause") {
                Thread.sleep(30)
                realEmbraceCacheService.loadBytes(name)
            }
            realEmbraceCacheService.loadBytes(name)
        }

    override fun loadPayload(name: String): SerializationAction =
        findLock(name).read {
            realEmbraceCacheService.loadPayload(name)
        }

    override fun deleteFile(name: String): Boolean =
        findLock(name).write {
            realEmbraceCacheService.deleteFile(name)
        }

    override fun loadOldPendingApiCalls(name: String): List<PendingApiCall>? =
        findLock(name).read {
            realEmbraceCacheService.loadOldPendingApiCalls(name)
        }

    override fun replaceSession(name: String, transformer: (SessionMessage) -> SessionMessage) =
        findLock(name).write {
            realEmbraceCacheService.replaceSession(name, transformer)
        }

    private fun findLock(name: String) = fileLocks.getOrPut(name, ::ReentrantReadWriteLock)

    private fun File.writeBytesWithPause(array: ByteArray): Unit = FileOutputStream(this).use {
        val partOne = array.take(7).toByteArray()
        val partTwo = array.takeLast(array.size - 7).toByteArray()
        it.write(partOne)
        Thread.sleep(200)
        it.write(partTwo)
    }

    private fun File.writeHalfTheBytes(array: ByteArray): Unit = FileOutputStream(this).use {
        it.write(array.dropLast(array.size / 2).toByteArray())
        throw IllegalAccessException("Writing half the bytes and failing so you better take care of this")
    }
}
