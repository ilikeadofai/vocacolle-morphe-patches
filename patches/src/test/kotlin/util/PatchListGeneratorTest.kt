package util

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PatchListGeneratorTest {
    @Test
    fun `uses the exact current patch bundle even when another bundle is newer`() {
        val directory = createTempDirectory("patch-list-generator").toFile()
        try {
            File(directory, "patches-1.0.0.mpp").apply {
                writeText("stale")
                setLastModified(2_000L)
            }
            val current = File(directory, "patches-1.1.0.mpp").apply {
                writeText("current")
                setLastModified(1_000L)
            }

            assertEquals(current.canonicalFile, requireGeneratedPatchBundle(current).canonicalFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects a directory whose name ends in mpp`() {
        val directory = createTempDirectory("patch-list-generator-directory").toFile()
        try {
            val fakeBundle = File(directory, "patches-1.1.0.mpp").apply { mkdirs() }

            assertFailsWith<IllegalStateException> { requireGeneratedPatchBundle(fakeBundle) }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `rejects a directory without a valid patch bundle`() {
        val directory = createTempDirectory("patch-list-generator-empty").toFile()
        try {
            val missingBundle = File(directory, "patches-1.1.0.mpp")

            assertFailsWith<IllegalStateException> { requireGeneratedPatchBundle(missingBundle) }
        } finally {
            directory.deleteRecursively()
        }
    }
}
