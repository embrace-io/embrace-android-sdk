package io.embrace.android.embracesdk.internal.envelope.resource

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

internal class StorageTypeDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `emmc partitions and nothing else is detected as emmc`() {
        assertTrue(
            checkNotNull(
                detect(
                    "mmcblk0",
                    "mmcblk0p1",
                    "mmcblk0p2",
                    "mmcblk0boot0",
                    "mmcblk0boot1",
                    "mmcblk0rpmb",
                ),
            ),
        )
    }

    @Test
    fun `non storage nodes are ignored`() {
        assertTrue(
            checkNotNull(
                detect(
                    "mmcblk0",
                    "dm-0",
                    "loop0",
                    "zram0",
                    "ram0",
                    "by-name",
                    "bootdevice",
                    "sdcard",
                ),
            ),
        )
    }

    @Test
    fun `mmc alongside ufs is not detected as emmc`() {
        assertFalse(checkNotNull(detect("mmcblk1", "mmcblk1p1", "sda", "sda1", "sdb")))
    }

    @Test
    fun `mmc alongside nvme is not detected as emmc`() {
        assertFalse(checkNotNull(detect("mmcblk1", "nvme0n1", "nvme0n1p1")))
    }

    @Test
    fun `mmc alongside a virtual disk is not detected as emmc`() {
        assertFalse(checkNotNull(detect("mmcblk0", "vda", "vdb", "vdc1")))
    }

    @Test
    fun `mmc alongside raw nand is not detected as emmc`() {
        assertFalse(checkNotNull(detect("mmcblk0", "mtdblock0", "mtdblock1")))
    }

    @Test
    fun `ufs with no mmc is not detected as emmc`() {
        assertFalse(checkNotNull(detect("sda", "sda1", "dm-0")))
    }

    @Test
    fun `no recognisable storage device yields no result`() {
        assertNull(detect("dm-0", "loop0", "zram0", "by-name"))
    }

    @Test
    fun `empty directory yields no result`() {
        assertNull(detect())
    }

    @Test
    fun `missing directory yields no result`() {
        assertNull(detectUsesEmmcStorage(File(tempFolder.root, "does-not-exist")))
    }

    @Test
    fun `a file in place of the directory yields no result`() {
        assertNull(detectUsesEmmcStorage(tempFolder.newFile("block")))
    }

    private fun detect(vararg deviceNames: String): Boolean? {
        val dir = tempFolder.newFolder()
        deviceNames.forEach { File(dir, it).createNewFile() }
        return detectUsesEmmcStorage(dir)
    }
}
