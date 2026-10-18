package com.gow3.android.feature.home

import com.gow3.android.data.PatchCatalog
import com.gow3.android.data.driver.DriverEntry
import com.gow3.android.data.driver.DriverManager
import com.gow3.android.data.model.MobilePreset
import com.gow3.android.data.model.MobilePresets
import com.gow3.android.ui.components.LauncherColors


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow3.android.R

/**
 * Home 页的「启动配置」卡片：承载游戏目录/更新目录选择、默认配置（DEFAULT PROFILE）
 * 预设选择与 GPU 驱动下拉。启动参数的就地编辑全部位于 Settings tab
 * （见 change add-android-launcher-config-tab），驱动管理（列表/删除/导入/下载/加速）
 * 位于 Settings DRIVER 分类（见 change add-android-launcher-driver-manager）。
 */
@Composable
internal fun LaunchParameters(
    gameDir: String?,
    updateDir: String?,
    ini: Map<String, String>,
    onChooseGameFolder: () -> Unit,
    onChooseUpdateFolder: () -> Unit,
    catalog: PatchCatalog?,
    catalogLoading: Boolean,
    applyPreset: (MobilePreset) -> Unit,
    driverEntries: List<DriverEntry>,
    driverEnabled: Boolean,
    onDriverSelected: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.surface)
            .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.launch_setup_title), color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChooseGameFolder)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.game_folder_label), color = LauncherColors.textMuted, fontSize = 13.sp)
                Text(stringResource(R.string.change_button), color = LauncherColors.gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
            }
            Text(
                gameDir ?: stringResource(R.string.game_folder_not_selected),
                color = if (gameDir != null) LauncherColors.textPrimary else LauncherColors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
//            Text(stringResource(R.string.game_folder_hint), color = LauncherColors.textMuted, fontSize = 10.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChooseUpdateFolder)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.update_folder_label), color = LauncherColors.textMuted, fontSize = 13.sp)
                Text(stringResource(R.string.change_button), color = LauncherColors.gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
            }
            Text(
                updateDir ?: stringResource(R.string.update_folder_not_set),
                color = if (updateDir != null) LauncherColors.textPrimary else LauncherColors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text(stringResource(R.string.update_folder_description), color = LauncherColors.textMuted, fontSize = 10.sp)
        }
        Text(
            if (catalog != null) stringResource(R.string.home_patch_identity, catalog.titleId, catalog.appVersion)
            else stringResource(if (catalogLoading) R.string.mods_loading else R.string.home_patch_unavailable),
            color = LauncherColors.textMuted, fontSize = 11.sp, lineHeight = 16.sp,
        )
        MobilePresetSelector(catalog, ini, applyPreset)
        DriverSelector(ini, driverEntries, driverEnabled, onDriverSelected)
    }
}

@Composable
private fun MobilePresetSelector(catalog: PatchCatalog?, ini: Map<String, String>, apply: (MobilePreset) -> Unit) {
    val presets = MobilePresets.available(catalog)
    val selected = MobilePresets.matching(catalog, ini)
    var expanded by remember(catalog) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.mobile_preset_title), color = LauncherColors.gold,
            fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.navSurface)
                    .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                    .clickable(enabled = presets.isNotEmpty()) { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(selected?.labelRes ?: if (presets.isEmpty()) R.string.mobile_preset_unavailable
                    else R.string.mobile_preset_custom), modifier = Modifier.weight(1f),
                    color = if (presets.isEmpty()) LauncherColors.textMuted else LauncherColors.textPrimary,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("▾", color = LauncherColors.textMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                presets.forEach { preset ->
                    DropdownMenuItem(text = { Text(stringResource(preset.labelRes)) }, onClick = {
                        expanded = false
                        apply(preset)
                    })
                }
            }
        }
        Text(stringResource(R.string.mobile_preset_description), color = LauncherColors.textMuted,
            fontSize = 11.sp, lineHeight = 16.sp)
    }
}

/**
 * GPU 驱动下拉（spec：首页展示与选择驱动）：款式与 [MobilePresetSelector] 一致，
 * 菜单项 =「系统驱动」兜底 + 各有效驱动包（名称 · 版本），选中经 [onSelected] 写
 * `gow3.ini` 的 `gpu_driver=`。无游戏目录时下拉禁用并以 footer 提示。
 */
@Composable
private fun DriverSelector(
    ini: Map<String, String>,
    entries: List<DriverEntry>,
    enabled: Boolean,
    onSelected: (String?) -> Unit,
) {
    val valid = entries.filter { it.valid }
    // 指向失效/已删除包的选择按「系统驱动」回退展示（spec：驱动包失效后显示回退）。
    val selectedId = DriverManager.selectedId(ini)
    val selectedEntry = valid.firstOrNull { it.id == selectedId }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.driver_card_title), color = LauncherColors.gold,
            fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.navSurface)
                    .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = selectedEntry?.let { it.name + if (it.version.isBlank()) "" else "  ·  ${it.version}" }
                    ?: stringResource(R.string.driver_card_system)
                Text(current, modifier = Modifier.weight(1f),
                    color = if (enabled) LauncherColors.textPrimary else LauncherColors.textMuted,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("▾", color = LauncherColors.textMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.driver_card_system)) }, onClick = {
                    expanded = false
                    onSelected(null)
                })
                valid.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.name + if (entry.version.isBlank()) "" else "  ·  ${entry.version}") },
                        onClick = {
                            expanded = false
                            onSelected(entry.id)
                        },
                    )
                }
            }
        }
        Text(
            if (enabled) stringResource(R.string.driver_card_footer)
            else stringResource(R.string.driver_card_needs_game_folder),
            color = LauncherColors.textMuted, fontSize = 11.sp, lineHeight = 16.sp,
        )
    }
}
