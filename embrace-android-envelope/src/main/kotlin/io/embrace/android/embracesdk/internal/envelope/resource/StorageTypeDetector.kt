package io.embrace.android.embracesdk.internal.envelope.resource

import java.io.File

/**
 * Infers whether the device uses an eMMC module for its primary storage by inspecting the names of
 * the block device nodes that the kernel exposes under [BLOCK_DEVICE_DIR].
 *
 * eMMC modules appear as `mmcblkN` nodes, but so do removable SD/TF cards, so an MMC node on its own
 * isn't conclusive. Devices with faster primary storage expose it as a SCSI/UFS (`sdX`), NVMe
 * (`nvmeXnY`), virtio (`vdX`) or raw NAND (`mtdblockN`) node instead, so if one of those is present
 * then the MMC node is assumed to be a removable card rather than the primary storage.
 *
 * @return true if the only primary storage device present is an MMC one, false if some other primary
 * storage device is present, and null if no conclusion can be drawn - either because the directory
 * couldn't be listed, or because it contained nothing recognisable as a primary storage device.
 */
internal fun detectUsesEmmcStorage(blockDeviceDir: File = File(BLOCK_DEVICE_DIR)): Boolean? =
    runCatching {
        val names = blockDeviceDir.list() ?: return@runCatching null
        var mmc = false
        var otherPrimary = false

        names.forEach { name ->
            when {
                MMC_DEVICE.matches(name) -> mmc = true
                OTHER_PRIMARY_DEVICE.matches(name) -> otherPrimary = true
            }
        }

        when {
            mmc && !otherPrimary -> true
            mmc || otherPrimary -> false
            else -> null
        }
    }.getOrNull()

private const val BLOCK_DEVICE_DIR = "/dev/block"

/**
 * An MMC device, or one of its partitions: `mmcblk0`, `mmcblk0p1`, `mmcblk0boot0`, `mmcblk0rpmb`.
 */
private val MMC_DEVICE = Regex("""mmcblk\d+.*""")

/**
 * A non-MMC device that could plausibly hold the primary storage, or one of its partitions. The
 * letter counts are bounded so that names which merely start with the same prefix (`sdcard`) don't
 * get misread as a device node.
 */
private val OTHER_PRIMARY_DEVICE = Regex("""(sd|vd)[a-z]{1,2}\d*|nvme\d+n\d+.*|mtdblock\d+""")
