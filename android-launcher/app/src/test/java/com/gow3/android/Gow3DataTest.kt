package com.gow3.android

import com.gow3.android.data.Gow3Data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gow3DataTest {
    private fun tempDir(): File = Files.createTempDirectory("gow3data-test").toFile()

    @Test
    fun `prepare migrates old user config dll and logs`() {
        val files = tempDir()
        val old = File(files, "gow3port").apply { mkdirs() }
        File(old, "user/savedata/1/CUSA01623/AUTOSAVE").mkdirs()
        File(old, "user/savedata/1/CUSA01623/AUTOSAVE/data.bin").writeText("save")
        File(old, "gow3.ini").writeText("fps_limit=60\n")
        File(old, "Lossless.dll").writeBytes(byteArrayOf(1, 2, 3))
        File(old, "logs").mkdirs()
        File(old, "logs/android-game.log").writeText("log")
        val defaults = File(files, "default.ini").apply { writeText("fps_limit=30\n") }

        Gow3Data.prepare(files, defaults)

        assertEquals("save", File(files, "gow3data/user/savedata/1/CUSA01623/AUTOSAVE/data.bin").readText())
        assertEquals("fps_limit=60\n", File(files, "gow3data/gow3.ini").readText())
        assertTrue(File(files, "gow3data/Lossless.dll").isFile)
        assertEquals("log", File(files, "gow3data/logs/android-game.log").readText())
        assertTrue(File(files, "gow3data/.data-schema").isFile)
    }

    @Test
    fun `prepare does not overwrite existing user data`() {
        val files = tempDir()
        File(files, "gow3port").mkdirs()
        File(files, "gow3port/gow3.ini").writeText("old=1\n")
        File(files, "gow3data").mkdirs()
        File(files, "gow3data/gow3.ini").writeText("new=1\n")
        val defaults = File(files, "default.ini").apply { writeText("default=1\n") }

        Gow3Data.prepare(files, defaults)

        assertEquals("new=1\n", File(files, "gow3data/gow3.ini").readText())
    }

    @Test
    fun `prepare creates default config when no legacy config exists`() {
        val files = tempDir()
        val defaults = File(files, "default.ini").apply { writeText("fps_limit=30\n") }

        Gow3Data.prepare(files, defaults)

        assertEquals("fps_limit=30\n", Gow3Data.config(files).readText())
        assertTrue(Gow3Data.user(files).isDirectory)
        assertTrue(Gow3Data.logs(files).isDirectory)
    }
}
