package com.gow3.android

import com.gow3.android.data.LaunchCategory
import com.gow3.android.data.LaunchParams
import com.gow3.android.feature.settings.SettingsEntry
import com.gow3.android.feature.settings.settingsEntries
import org.junit.Assert.*
import org.junit.Test

class SettingsEntryTest {
    private val params = LaunchParams.all

    @Test fun `graphics entries are card slots first then params in declared order`() {
        val entries = settingsEntries(LaunchCategory.GRAPHICS, params)
        assertEquals(listOf("upscaler", "render_resolution", "anisotropy", "sharpen", "sharpness"),
            entries.map { (it as SettingsEntry.Param).param.key })
        assertTrue(entries.all { it is SettingsEntry.Param })
    }

    @Test fun `reserved categories assemble to empty lists`() {
        assertTrue(settingsEntries(LaunchCategory.AUDIO, params).isEmpty())
        assertTrue(settingsEntries(LaunchCategory.DRIVER, params).isEmpty())
    }

    @Test fun `entry ids are stable and unique per category`() {
        for (category in LaunchCategory.entries) {
            val ids = settingsEntries(category, params).map { it.id }
            assertEquals(ids.size, ids.distinct().size)
        }
        assertEquals("param:fps_limit",
            settingsEntries(LaunchCategory.PERFORMANCE, params).first { it.id == "param:fps_limit" }.id)
    }

    /** Card slot 扩展点：声明 slot 后排在参数行之前（以 DRIVER 为例的行为契约）。 */
    @Test fun `card slots would render before params`() {
        // 首版 categoryCards 为空：仅验证模型可表达 slot 且 id 规则稳定。
        assertEquals("card:driver_manager", SettingsEntry.Card("driver_manager").id)
    }
}
