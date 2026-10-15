package com.gow3.android.feature.home

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
import com.gow3.android.data.Gow3Data
import com.gow3.android.data.Gow3Ini
import com.gow3.android.data.ImportResult
import com.gow3.android.data.LosslessDllManager
import com.gow3.android.data.RemoveResult
import com.gow3.android.data.PatchCatalog
import com.gow3.android.data.LaunchParams
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn
import com.gow3.android.ui.components.formatBytes
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
    val prefs = remember(context) { context.getSharedPreferences("gow3", Context.MODE_PRIVATE) }

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
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
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

    fun save(key: String, value: String) {
        val param = LaunchParams.forCatalog(catalog).firstOrNull { it.key == key } ?: return
        if (!param.enabled || param.options.none { it.value == value }) return
        val updates = if (LaunchParams.isPatchSetting(key)) mapOf(key to value, "restart_unconfirmed" to "0")
                      else mapOf(key to value)
        if (Gow3Ini.write(gow3IniFile(context), updates)) {
            iniValues = loadIni(context)
        } else {
            Toast.makeText(context, context.getString(R.string.settings_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        Text(stringResource(R.string.title_god_of_war_iii), color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(stringResource(R.string.title_remastered), color = LauncherColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        LaunchButton(onStartGame)
        Spacer(Modifier.height(20.dp))
        LaunchParameters(gameDir, updateDir, iniValues, ::save, onChooseGameFolder, onChooseUpdateFolder,
            catalog, catalogLoading)
        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(24.dp))
    }
}

/** launcher 的 gow3.ini 路径，即游戏、run-gow3.sh 和 GameActivity 读取的同一个文件。 */
private fun gow3IniFile(context: Context): File = Gow3Data.config(context)

private fun loadIni(context: Context): Map<String, String> = Gow3Ini.read(gow3IniFile(context))

private fun currentGameDir(context: Context): String? =
    context.getSharedPreferences("gow3", Context.MODE_PRIVATE).getString("game_dir", null)

private fun currentUpdateDir(context: Context): String? =
    context.getSharedPreferences("gow3", Context.MODE_PRIVATE).getString("update_dir", null)

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
