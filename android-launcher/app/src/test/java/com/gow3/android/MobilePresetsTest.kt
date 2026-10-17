package com.gow3.android

import com.gow3.android.data.Gow3Ini
import com.gow3.android.data.LaunchParams
import com.gow3.android.data.MobilePresets
import com.gow3.android.data.PatchCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MobilePresetsTest {
    @get:Rule val temp = TemporaryFolder()
    private fun catalog(): PatchCatalog = File(requireNotNull(System.getProperty("gow3.patchCatalogDir")),
        "God_of_War_III_Remastered_CUSA01740.xml").inputStream().use {
        PatchCatalog.parse(it, "CUSA01740", "01.00")
    }

    @Test fun `1740 offers all five imported mobile tiers`() {
        assertEquals(listOf("480p", "540p", "720p", "900p", "1080p"), MobilePresets.available(catalog()).map { it.resolution })
        assertTrue(MobilePresets.available(null).isEmpty())
        val c = catalog()
        val limited = c.copy(patches = c.patches.filter { it.resolution != "900p" })
        assertFalse(MobilePresets.available(limited).any { it.resolution == "900p" })
        assertTrue(MobilePresets.available(c.copy(patches = c.patches.filter { it.name != "Disable SSAO" })).isEmpty())
    }

    @Test fun `profiles persist graphics and nine patches together without changing personal settings`() {
        val c = catalog()
        val file = temp.newFile("gow3.ini")
        assertTrue(Gow3Ini.write(file, mapOf(
            "vibration" to "25", "lsfg_enabled" to "1", "pad.cross" to "a",
            "patches_CUSA01715_01_02" to "Skip Intro", "restart_unconfirmed" to "1",
            "render_resolution" to "480p", "engine_fps" to "120",
            "display_stretch" to "1",
            c.selectionKey to "Bug Fix - Startup Black Screen;Resolution Patch - 480p",
        )))
        for (preset in MobilePresets.available(c)) {
            assertTrue(Gow3Ini.write(file, MobilePresets.updates(preset, c, Gow3Ini.read(file))))
            val ini = Gow3Ini.read(file)
            assertEquals(preset, MobilePresets.matching(c, ini))
            assertEquals("25", ini["vibration"])
            assertEquals("1", ini["lsfg_enabled"])
            assertEquals("a", ini["pad.cross"])
            assertEquals("Skip Intro", ini["patches_CUSA01715_01_02"])
            assertEquals("0", ini["restart_unconfirmed"])
            assertEquals("60", ini["engine_fps"])
            assertEquals("60", ini["fps_limit"])
            // display_stretch 是显示端个人偏好：预设不重置它（用户开的宽屏保持开启），
            // 也不会因它不等于默认值而让预设匹配失败。
            assertEquals("1", ini["display_stretch"])
            assertEquals(setOf("Resolution Patch - ${preset.resolution}", "Disable SSAO", "Disable Motion Blur",
                "Disable Depth of Field", "Disable Bloom", "Disable Soft Particles", "Disable Dynamic Shadows",
                "Skip Intro", "Skip Any Video With X Button", "Bug Fix - Startup Black Screen"), c.selected(ini))
            for (param in LaunchParams.forCatalog(c).filter { it.key != "vibration" && it.key != "display_stretch" }) {
                assertEquals(preset.settings[param.key], LaunchParams.selectedOption(ini, param)?.value)
            }
        }
    }

    @Test fun `single settings and patch switches remain editable and show custom`() {
        val c = catalog()
        val preset = MobilePresets.available(c).first { it.resolution == "720p" }
        val ini = MobilePresets.updates(preset, c, emptyMap())
        assertEquals(9, c.selected(ini).size)
        assertNull(MobilePresets.matching(c, ini + ("fps_limit" to "30")))
        assertNull(MobilePresets.matching(c, ini + ("render_resolution" to "1440p")))
        val bloom = c.patches.first { it.name == "Disable Bloom" }
        val changed = ini + c.toggle(ini, bloom, false)
        assertFalse(bloom.name in c.selected(changed))
        assertNull(MobilePresets.matching(c, changed))
        assertEquals(preset, MobilePresets.matching(c, changed + MobilePresets.updates(preset, c, changed)))
    }

    @Test fun `unsupported profile cannot partially apply`() {
        val c = catalog().let { c -> c.copy(patches = c.patches.filter { it.resolution != "540p" }) }
        val light = MobilePresets.all.first { it.resolution == "540p" }
        assertThrows(IllegalArgumentException::class.java) {
            MobilePresets.updates(light, c, emptyMap())
        }
    }
}
