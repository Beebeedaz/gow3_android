package com.gow3.android.data

import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal enum class PatchGroup { RESOLUTION, FRAME_RATE, VIDEO, FIX, EFFECT }

internal data class GamePatch(
    val name: String,
    val note: String,
    val author: String,
    val validation: String,
    val defaultEnabled: Boolean,
) {
    val resolution: String? get() = name.takeIf { it.startsWith("Resolution Patch - ") }?.removePrefix("Resolution Patch - ")
    val fps: String? get() = name.takeIf { it.startsWith("Frame Rate Patch - ") }?.removePrefix("Frame Rate Patch - ")?.removeSuffix(" FPS")
    val group: PatchGroup get() = when {
        resolution != null -> PatchGroup.RESOLUTION
        fps != null -> PatchGroup.FRAME_RATE
        name.startsWith("Skip ") -> PatchGroup.VIDEO
        name.startsWith("Disable ") -> PatchGroup.EFFECT
        else -> PatchGroup.FIX
    }
}

internal enum class CatalogProblem { NO_GAME, INVALID_GAME, INVALID_UPDATE, UNSUPPORTED, VERSION, READ_FAILED }
internal class PatchCatalogException(val problem: CatalogProblem) : Exception(problem.name)

internal data class PatchCatalog(val titleId: String, val appVersion: String, val patches: List<GamePatch>) {
    val selectionKey: String get() = "patches_${titleId}_${appVersion.replace('.', '_')}"

    /** 分辨率和帧率与首页共用设置，其余选择按 Title ID 和版本隔离。 */
    fun selected(ini: Map<String, String>): Set<String> {
        val selected = if (selectionKey in ini) ini.getValue(selectionKey).split(';').filter { it.isNotBlank() }.toMutableSet()
        else patches.filter { it.defaultEnabled }.map { it.name }.toMutableSet()
        val resolution = ini["render_resolution"] ?: "native"
        val fps = ini["engine_fps"] ?: "120"
        selected.removeAll { it.startsWith("Resolution Patch") || it.startsWith("Frame Rate Patch") }
        patches.firstOrNull { it.resolution == resolution }?.let { selected.add(it.name) }
        patches.firstOrNull { it.fps == fps }?.let { selected.add(it.name) }
        if (resolution != "native") selected.remove(TEXTURE)
        else if (selectionKey !in ini && patches.any { it.name == TEXTURE }) selected.add(TEXTURE)
        return selected.intersect(patches.map { it.name }.toSet())
    }

    fun toggle(ini: Map<String, String>, patch: GamePatch, enabled: Boolean): Map<String, String> {
        require(patch in patches)
        val selected = selected(ini).toMutableSet()
        val updates = linkedMapOf<String, String>()
        if (enabled) selected.add(patch.name) else selected.remove(patch.name)
        patch.resolution?.let { value ->
            selected.removeAll { it.startsWith("Resolution Patch") && it != patch.name }
            if (enabled) selected.remove(TEXTURE)
            updates["render_resolution"] = if (enabled) value else "native"
        }
        patch.fps?.let { value ->
            selected.removeAll { it.startsWith("Frame Rate Patch") && it != patch.name }
            updates["engine_fps"] = if (enabled) value else "60"
        }
        if (patch.name == TEXTURE && enabled) {
            selected.removeAll { it.startsWith("Resolution Patch") }
            updates["render_resolution"] = "native"
        }
        // 页面保存是下一次启动的新请求，不能被上一次未确认重启的回滚覆盖。
        updates["restart_unconfirmed"] = "0"
        updates[selectionKey] = patches.filter { it.name in selected }.joinToString(";") { it.name }
        return updates
    }

    companion object {
        private const val TEXTURE = "Bug Fix - Texture Corruption Fix"
        private val versions = mapOf("CUSA01623" to "01.02", "CUSA01715" to "01.02", "CUSA01720" to "01.02", "CUSA01740" to "01.00")

        fun load(game: File?, update: File?, open: (String) -> InputStream): PatchCatalog {
            if (game == null) throw PatchCatalogException(CatalogProblem.NO_GAME)
            if (!File(game, "eboot.bin").isFile) throw PatchCatalogException(CatalogProblem.INVALID_GAME)
            val base = GameSfo.read(File(game, "sce_sys/param.sfo")) ?: throw PatchCatalogException(CatalogProblem.INVALID_GAME)
            val effective = if (update == null) base else {
                val values = GameSfo.read(File(update, "sce_sys/param.sfo"))
                if (!File(update, "eboot.bin").isFile || values == null || values["TITLE_ID"] != base["TITLE_ID"] ||
                    game.canonicalFile == update.canonicalFile || game.canonicalFile.toPath().startsWith(update.canonicalFile.toPath()) ||
                    update.canonicalFile.toPath().startsWith(game.canonicalFile.toPath())) throw PatchCatalogException(CatalogProblem.INVALID_UPDATE)
                values
            }
            val title = effective["TITLE_ID"] ?: throw PatchCatalogException(CatalogProblem.INVALID_GAME)
            val version = effective["APP_VER"] ?: throw PatchCatalogException(CatalogProblem.INVALID_GAME)
            if (title !in versions) throw PatchCatalogException(CatalogProblem.UNSUPPORTED)
            if (version != versions[title]) throw PatchCatalogException(CatalogProblem.VERSION)
            return open("God_of_War_III_Remastered_$title.xml").use { parse(it, title, version) }
        }

        /** 仅解析 APK 内随运行时一起打包的目录，不读取玩家的外部 XML。 */
        fun parse(input: InputStream, title: String, version: String): PatchCatalog {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).documentElement
            val ids = root.getElementsByTagName("ID")
            require(ids.length == 1 && ids.item(0).textContent.trim() == title)
            val metas = root.getElementsByTagName("Metadata")
            val patches = (0 until metas.length).map { metas.item(it) as Element }
                .filter { it.getAttribute("AppVer") == version && it.getAttribute("AppElf") == "eboot.bin" }
                .map { meta ->
                    val always = meta.getAttribute("AlwaysOn").ifEmpty {
                        (meta.getAttribute("Name") == "Bug Fix - Startup Black Screen").toString()
                    }.toBoolean()
                    GamePatch(meta.getAttribute("Name"), meta.getAttribute("Note"), meta.getAttribute("Author"),
                        meta.getAttribute("Validation"), meta.getAttribute("isEnabled").toBoolean() || always)
                }
            require(patches.isNotEmpty() && patches.map { it.name }.distinct().size == patches.size)
            return PatchCatalog(title, version, patches)
        }
    }
}
