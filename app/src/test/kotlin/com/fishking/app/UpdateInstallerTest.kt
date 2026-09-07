package com.fishking.app

import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateInstallerTest {
    @Test
    fun downloadVerificationRejectsWrongSizeOrDigest() {
        UpdateInstaller.verifyDownload(10, HASH, 10, HASH.lowercase())
        assertThrows(UpdateDownloadException::class.java) {
            UpdateInstaller.verifyDownload(9, HASH, 10, HASH)
        }
        assertThrows(UpdateDownloadException::class.java) {
            UpdateInstaller.verifyDownload(10, "B".repeat(64), 10, HASH)
        }
    }

    private companion object {
        val HASH = "A".repeat(64)
    }
}
