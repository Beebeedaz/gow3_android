package com.gow3.android.feature.home

import com.gow3.android.ui.components.ScreenColumn
import com.gow3.android.ui.components.formatBytes

import com.gow3.android.core.settings.GamePrefs
import com.gow3.android.core.settings.Gow3Settings
import com.gow3.android.data.Gow3Data
import com.gow3.android.data.PatchCatalog
import com.gow3.android.data.driver.DriverEntry
import com.gow3.android.data.driver.DriverManager
import com.gow3.android.data.model.MobilePreset
import com.gow3.android.data.model.MobilePresets
import com.gow3.android.data.session.ImportResult
import com.gow3.android.data.session.LosslessDllManager
import com.gow3.android.data.session.RemoveResult
import com.gow3.android.ui.components.LauncherColors


import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gow3.android.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun HomeScreen(
    onStartGame: () -> Unit,
    losslessDlls: LosslessDllManager,
    onGetFromSteam: ((Boolean, String) -> Unit) -> Unit,
    onChooseGameFolder: () -> Unit,
    onChooseUpdateFolder: () -> Unit,
) {
    val context = LocalContext.current
    var iniValues by remember { mutableStateOf(loadIni(context)) }
    var dllState by remember { mutableStateOf(losslessDlls.resolve()) }
    var gameDir by remember { mutableStateOf(currentGameDir(context)) }
    var updateDir by remember { mutableStateOf(currentUpdateDir(context)) }
    var notice by remember { mutableStateOf<String?>(null) }
    var catalog by remember { mutableStateOf<PatchCatalog?>(null) }
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogRevision by remember { mutableIntStateOf(0) }
    // 驱动卡片的重扫信号：回到前台 / 目录变化 / 本页写入选择时都会递增。
    var driverRevision by remember { mutableIntStateOf(0) }
    // 启动配置卡驱动下拉的数据：每次 revision 递增（前台/目录变化/本页写入）重扫磁盘。
    var driverEntries by remember { mutableStateOf(emptyList<DriverEntry>()) }
    val prefs = remember(context) { GamePrefs(context) }

    fun refreshDll() {
        dllState = losslessDlls.resolve()
    }

    fun toast(message: String) {
        notice = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // 玩家可能在游戏运行期间把 Lossless.dll 放到游戏目录旁，因此每次 Home 回到前台
    // 时都会从磁盘重新读取状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, prefs) {
        fun refreshSettings() {
            gameDir = currentGameDir(context)
            updateDir = currentUpdateDir(context)
            iniValues = loadIni(context)
            catalog = null
            catalogLoading = true
            catalogRevision++
            driverRevision++
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "game_dir" || key == "update_dir") refreshSettings()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshDll()
                refreshSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        prefs.raw.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.raw.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(driverRevision) {
        driverEntries = withContext(Dispatchers.IO) { DriverManager.scan(context.filesDir) }
    }

    LaunchedEffect(gameDir, updateDir, catalogRevision) {
        catalog = null
        catalogLoading = true
        val game = gameDir?.let(::File)
        val update = updateDir?.takeIf { it.isNotBlank() }?.let(::File)
        catalog = withContext(Dispatchers.IO) {
            runCatching { PatchCatalog.load(game, update, context.assets::open) }.getOrNull()
        }
        catalogLoading = false
    }

    fun saveUpdates(updates: Map<String, String>): Boolean {
        return if (Gow3Settings.update(gow3IniFile(context), updates)) {
            iniValues = loadIni(context)
            true
        } else {
            Toast.makeText(context, context.getString(R.string.settings_save_failed), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun applyPreset(preset: MobilePreset) {
        val currentCatalog = catalog ?: return
        if (preset !in MobilePresets.available(currentCatalog)) return
        saveUpdates(MobilePresets.updates(preset, currentCatalog, loadIni(context)))
    }

    /** 驱动选择：写 `gpu_driver=` 键（系统驱动写 auto），与 run-bionic.sh 的解析契约一致。 */
    fun selectDriver(id: String?, displayName: String) {
        if (saveUpdates(mapOf(DriverManager.INI_KEY to (id ?: DriverManager.SYSTEM_VALUE)))) {
            toast(context.getString(R.string.driver_pick_selected_fmt, displayName))
        }
        driverRevision++
    }

    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        Text(stringResource(R.string.title_god_of_war_iii), color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(stringResource(R.string.title_remastered), color = LauncherColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        LaunchButton(onStartGame)
        Spacer(Modifier.height(20.dp))
        LaunchParameters(gameDir, updateDir, iniValues, onChooseGameFolder, onChooseUpdateFolder,
            catalog, catalogLoading, ::applyPreset, driverEntries, gameDir != null) { id ->
            // 下拉只回传 id：展示名从扫描结果解析（系统驱动回退到本地化文案）。
            val name = id?.let { picked -> driverEntries.firstOrNull { it.id == picked }?.name }
                ?: context.getString(R.string.driver_card_system)
            selectDriver(id, name)
        }
        Spacer(Modifier.height(16.dp))
        // gow3port: LSFG 运行时能力已废除（Bionic 迁移，见 openspec change
        // migrate-android-runtime-to-bionic）。「Steam 下载 LSFG / 导入 LSFG」入口保留代码、
        // 仅隐藏（不删除文件、不产生帧生成行为），供未来插帧方案复用。恢复时打开下面的调用。
        if (false) {
            FrameGenerationCard(
                state = dllState,
                notice = notice,
                onImport = {
                    losslessDlls.launchPicker { result ->
                        when (result) {
                            is ImportResult.Success -> {
                                refreshDll()
                                toast(context.getString(R.string.home_imported_fmt, LosslessDllManager.FILE_NAME, formatBytes(result.state.sizeBytes)))
                            }
                            ImportResult.Cancelled -> refreshDll()
                            is ImportResult.Failed -> toast(result.message)
                        }
                    }
                },
                onSteam = {
                    // Steam 下载成功后（该构建不含 Steam 时则由 stub 自行提示）回到这里刷新，
                    // 让完成的下载无需重新进入页面即可显示。
                    onGetFromSteam { _, message ->
                        refreshDll()
                        toast(message)
                    }
                },
                onRemove = {
                    when (val result = losslessDlls.remove()) {
                        is RemoveResult.Done -> {
                            refreshDll()
                            toast(
                                if (result.gameFolderRemains)
                                    context.getString(R.string.home_removed_game_folder_copy)
                                else
                                    context.getString(R.string.home_removed_fmt, LosslessDllManager.FILE_NAME),
                            )
                        }
                        is RemoveResult.Failed -> toast(result.message)
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** launcher 的 gow3.ini 路径，即游戏、run-gow3.sh 和 GameActivity 读取的同一个文件。 */
private fun gow3IniFile(context: Context): File = Gow3Data.config(context)

private fun loadIni(context: Context): Map<String, String> = Gow3Settings.read(gow3IniFile(context))

private fun currentGameDir(context: Context): String? =
    GamePrefs(context).gameDir()

private fun currentUpdateDir(context: Context): String? =
    GamePrefs(context).updateDir()

@Composable
private fun LaunchButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFB8172B), Color(0xFF8C0D1A), Color(0xFFB8172B))))
            .border(BorderStroke(1.dp, LauncherColors.gold), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.launch_button), color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
        Text(stringResource(R.string.start_game_button), color = LauncherColors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
    }
}
