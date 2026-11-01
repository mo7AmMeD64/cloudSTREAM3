package com.kodastream.app.manga.extension

import com.kodastream.app.manga.store.AvailableExtension
import com.kodastream.app.manga.store.ContentWarning
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class TrustAndInstallTest {
    @Test
    fun storeKeyTrustsAnyVersion() {
        assertTrue(
            isExtensionTrusted(
                pkgName = "pkg",
                versionCode = 7,
                signatures = listOf("store-signature"),
                storeSigningKeys = listOf("store-signature"),
                manualTrustKeys = emptySet(),
            )
        )
    }

    @Test
    fun manualTrustMatchesOnlyTheExactPackageVersionAndSignature() {
        val key = extensionTrustKey("pkg", 7, "manual-signature")

        assertTrue(
            isExtensionTrusted("pkg", 7, listOf("manual-signature"), emptyList(), setOf(key))
        )
        assertFalse(
            isExtensionTrusted("pkg", 8, listOf("manual-signature"), emptyList(), setOf(key))
        )
        assertFalse(
            isExtensionTrusted("other", 7, listOf("manual-signature"), emptyList(), setOf(key))
        )
        assertFalse(
            isExtensionTrusted("pkg", 7, listOf("wrong-signature"), emptyList(), setOf(key))
        )
    }

    @Test
    fun unsignedAndUnknownKeysAreNotTrusted() {
        assertFalse(isExtensionTrusted("pkg", 7, emptyList(), listOf("store"), emptySet()))
        assertFalse(isExtensionTrusted("pkg", 7, listOf("other"), listOf("store"), emptySet()))
        assertFalse(isExtensionTrusted("pkg", 7, listOf("other"), listOf(""), emptySet()))
    }

    @Test
    fun installFlowEmitsTheWholeSequenceAndDeletesTheTempFile() = runBlocking {
        val target = tempFile()
        var existsDuringInstall = false
        var seenDownload = false
        val backend = object : ExtensionInstallBackend {
            override fun download(extension: AvailableExtension, target: File): Flow<Int> = flow {
                seenDownload = target.parentFile?.isDirectory == true
                target.writeText("apk")
                emit(0)
                emit(50)
                emit(50)
                emit(100)
            }

            override fun install(extension: AvailableExtension, target: File) {
                existsDuringInstall = target.isFile
            }
        }

        val steps = installExtensionFlow(extension(), target, backend).toList()

        assertEquals(
            listOf(
                InstallStep.Pending,
                InstallStep.Downloading(0),
                InstallStep.Downloading(50),
                InstallStep.Downloading(100),
                InstallStep.Installing,
                InstallStep.Installed,
            ),
            steps,
        )
        assertTrue(seenDownload)
        assertTrue(existsDuringInstall)
        assertFalse(target.exists())
    }

    @Test
    fun installFlowStopsWithAnErrorWhenTheDownloadFails() = runBlocking {
        val target = tempFile()
        val backend = object : ExtensionInstallBackend {
            override fun download(extension: AvailableExtension, target: File): Flow<Int> = flow {
                throw IOException("offline")
            }

            override fun install(extension: AvailableExtension, target: File) = Unit
        }

        val steps = installExtensionFlow(extension(), target, backend).toList()

        assertEquals(
            listOf(InstallStep.Pending, InstallStep.Error("offline")),
            steps,
        )
        assertFalse(target.exists())
    }

    @Test
    fun installFlowClampsProgressOutsideZeroToHundred() = runBlocking {
        val target = tempFile()
        val backend = object : ExtensionInstallBackend {
            override fun download(extension: AvailableExtension, target: File): Flow<Int> = flow {
                emit(-5)
                emit(150)
            }

            override fun install(extension: AvailableExtension, target: File) = Unit
        }

        val steps = installExtensionFlow(extension(), target, backend).toList()

        assertEquals(
            listOf(
                InstallStep.Pending,
                InstallStep.Downloading(0),
                InstallStep.Downloading(100),
                InstallStep.Installing,
                InstallStep.Installed,
            ),
            steps,
        )
        assertNull(steps.firstOrNull { it is InstallStep.Error })
    }

    private fun extension() = AvailableExtension(
        name = "Extension",
        pkgName = "pkg",
        apkUrl = "https://example.test/ext.apk",
        iconUrl = "",
        versionCode = 1,
        versionName = "1.0",
        libVersion = 1.5,
        lang = "en",
        contentWarning = ContentWarning.SAFE,
        sources = emptyList(),
        storeUrl = "https://example.test/index.pb",
        storeName = "Store",
        badgeLabel = "Store",
    )

    private fun tempFile() = File(
        System.getProperty("java.io.tmpdir"),
        "kodastream-test-${UUID.randomUUID()}.part",
    )
}
