package com.fishking.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UpdateCheckerTest {
    @Test
    fun acceptsOnlyReleaseApksFromTheFishKingRepository() {
        val release = UpdateChecker.parseRelease(releaseJson("https://github.com/fivespeedbuck/fishking/releases/download/v0.2.0/fishking.apk"))
        assertEquals("v0.2.0", release.tag)
        assertEquals(1234L, release.apkSize)

        assertThrows(UpdateMetadataException::class.java) {
            UpdateChecker.parseRelease(releaseJson("https://example.com/fishking.apk"))
        }
    }

    @Test
    fun publicManifestIsUsedWhenGithubApiFails() {
        val result = UpdateChecker.fetchLatestWith { url ->
            if (url == UpdateChecker.LATEST_RELEASE_API) error("rate limited")
            releaseJson("https://github.com/fivespeedbuck/fishking/releases/download/v0.2.0/fishking.apk")
        }
        assertTrue(result is UpdateChecker.FetchResult.Success)
        assertEquals(UpdateChecker.Source.PUBLIC_MANIFEST, (result as UpdateChecker.FetchResult.Success).source)
    }

    @Test
    fun versionComparisonUsesNumericSegments() {
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.9"))
        assertTrue(UpdateChecker.isNewer("0.1.10", "0.1.9"))
        assertFalse(UpdateChecker.isNewer("v0.1.1", "0.1.1"))
        assertFalse(UpdateChecker.isNewer("v0.1.0", "0.1.1"))
    }

    private fun releaseJson(url: String) = """
        {
          "tag_name": "v0.2.0",
          "name": "FishKing 0.2.0",
          "html_url": "https://github.com/fivespeedbuck/fishking/releases/tag/v0.2.0",
          "assets": [{
            "name": "fishking.apk",
            "browser_download_url": "$url",
            "size": 1234,
            "digest": "sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
          }]
        }
    """.trimIndent()
}
