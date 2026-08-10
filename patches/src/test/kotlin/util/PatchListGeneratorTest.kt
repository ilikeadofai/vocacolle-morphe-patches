package util

import app.morphe.patcher.patch.loadPatchesFromJar
import io.github.ilikeadofai.vocacolle.patches.vocacolleAdControlPatch
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assumptions.assumeTrue

class PatchListGeneratorTest {
    @Test
    fun `restores public dependency names after loading the generated MPP`() {
        val bundlePath = System.getProperty("vocacolle.mpp")
        assumeTrue(bundlePath != null, "vocacolle.mpp is required for MPP reload coverage")
        val loadedPatches = loadPatchesFromJar(setOf(File(bundlePath!!)))
        val adControl = loadedPatches.single { it.name == "VocaColle ad control" }

        assertEquals(
            listOf("VocaColle Morphe settings"),
            patchDependencyNames(adControl, loadedPatches)
        )
    }

    @Test
    fun `serializes dependency patch names instead of implementation class names`() {
        assertEquals(
            listOf("VocaColle Morphe settings"),
            patchDependencyNames(vocacolleAdControlPatch)
        )
    }

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
