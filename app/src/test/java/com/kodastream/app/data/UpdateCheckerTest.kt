package com.kodastream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val apk = "https://github.com/mo7AmMeD64/cloudSTREAM3/releases/download/v0.6.0/cloud3.apk"

    private fun release(
        tag: String = "v0.6.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        apkUrl: String? = apk,
    ) = Release(tag, draft, prerelease, apkUrl, "notes")

    private fun offered(
        release: Release = release(),
        current: String = "0.5.0",
        skipped: String? = null,
    ) = UpdateChecker.decide(release, current, skipped).update

    @Test
    fun versionsCompareNumerically() {
        // The one bug a string compare always has.
        assertTrue(UpdateChecker.isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "0.10.0"))
        assertTrue(UpdateChecker.isNewerVersion("v0.3.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun newerOfficialReleaseIsOffered() {
        val update = offered()
        assertEquals("0.6.0", update?.version)
        assertEquals(apk, update?.url)
        assertEquals("notes", update?.notes)
    }

    @Test
    fun ciBuildTagIsNeverOffered() {
        assertNull(offered(release(tag = "build-15")))
        assertNull(UpdateChecker.officialVersion("build-15"))
        assertNull(UpdateChecker.officialVersion("0.6.0"))
        assertEquals("0.6.0", UpdateChecker.officialVersion("v0.6.0"))
    }

    @Test
    fun preReleaseAndDraftAreIgnored() {
        assertNull(offered(release(prerelease = true)))
        assertNull(offered(release(draft = true)))
    }

    @Test
    fun releaseWithoutApkIsIgnored() {
        assertNull(offered(release(apkUrl = null)))
    }

    @Test
    fun sameOrOlderVersionIsNotOffered() {
        assertNull(offered(current = "0.6.0"))
        assertNull(offered(current = "0.7.0"))
    }

    @Test
    fun v07InstallIsOfferedV08() {
        // The shipped v0.8.0 release, as /releases/latest returns it.
        val update = offered(
            release = release(
                tag = "v0.8.0",
                apkUrl = "https://github.com/mo7AmMeD64/cloudSTREAM3/releases/download/v0.8.0/cloud3-105.apk",
            ),
            current = "0.7.0",
        )
        assertEquals("0.8.0", update?.version)
        assertEquals(
            "https://github.com/mo7AmMeD64/cloudSTREAM3/releases/download/v0.8.0/cloud3-105.apk",
            update?.url,
        )
    }

    @Test
    fun skippedVersionIsNotOffered() {
        assertNull(offered(skipped = "0.6.0"))
    }

    @Test
    fun skippedVersionDoesNotHideANewerOne() {
        val update = offered(
            release = release(tag = "v0.7.0"),
            skipped = "0.6.0",
        )
        assertEquals("0.7.0", update?.version)
    }

    @Test
    fun storedUpdateIsOfferedUntilTheInstallCatchesUp() {
        val stored = AppUpdate("0.6.0", "notes", apk)
        assertEquals(stored, UpdateChecker.decideStored(stored, "0.5.0", null).update)
        assertNull(UpdateChecker.decideStored(stored, "0.6.0", null).update)
    }

    @Test
    fun storedSkippedVersionIsNotOffered() {
        val stored = AppUpdate("0.6.0", null, apk)
        assertNull(UpdateChecker.decideStored(stored, "0.5.0", "0.6.0").update)
    }

    @Test
    fun onlyCompletedResponsesCountAsACheck() {
        assertTrue(UpdateChecker.isSuccessfulCheck(200))
        assertTrue(UpdateChecker.isSuccessfulCheck(304))
        assertTrue(UpdateChecker.isSuccessfulCheck(404))
        assertFalse(UpdateChecker.isSuccessfulCheck(403))
        assertFalse(UpdateChecker.isSuccessfulCheck(500))
        assertFalse(UpdateChecker.isSuccessfulCheck(null))
    }
}
