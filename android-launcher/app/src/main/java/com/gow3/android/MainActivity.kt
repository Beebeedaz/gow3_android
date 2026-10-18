package com.gow3.android

import com.gow3.android.ui.LauncherScreen
import com.gow3.android.ui.theme.Gow3LauncherTheme

import com.gow3.android.core.settings.LanguageManager
import com.gow3.android.data.session.GameFolderManager
import com.gow3.android.data.session.LogManager
import com.gow3.android.data.session.LosslessDllManager
import com.gow3.android.data.session.SaveManager
import com.gow3.android.game.GameActivity
import com.gow3.android.game.SteamDllDialog
import com.gow3.android.ui.components.LauncherColors

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private lateinit var gameFolderManager: GameFolderManager
    private lateinit var losslessDllManager: LosslessDllManager
    private lateinit var saveManager: SaveManager
    private lateinit var logManager: LogManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLanguage(newBase))
    }

    /** 按 LanguageManager 的语言覆盖（或系统语言）为 MainActivity 注入 Locale 配置。 */
    private fun applyLanguage(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(LanguageManager.resolve(base))
        return base.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = LauncherColors.backgroundTop.toArgbCompat()
        window.navigationBarColor = LauncherColors.backgroundBottom.toArgbCompat()
        window.decorView.systemUiVisibility = 0

        gameFolderManager = GameFolderManager(this)
        losslessDllManager = LosslessDllManager(this)
        saveManager = SaveManager(this)
        logManager = LogManager(this)
        setContent {
            Gow3LauncherTheme(dynamicColor = false) {
                LauncherScreen(
                    onStartGame = {
                        if (gameFolderManager.hasGameFolder()) {
                            // action=launch：GameActivity 的单次启动踢动标记。没有它，
                            // surfaceCreated 只记录 holder、等一个永不到来的用户动作，
                            // 启动流程（runtime 解包 → 游戏进程）不会开始。
                            startActivity(
                                Intent(this, GameActivity::class.java)
                                    .putExtra("action", "launch"),
                            )
                        }
                    },
                    losslessDlls = losslessDllManager,
                    onGetFromSteam = { onDownloaded ->
                        SteamDllDialog(this, losslessDllManager.appCopy()) { ok, msg ->
                            onDownloaded(ok, msg)
                        }.show()
                    },
                    onChooseGameFolder = { gameFolderManager.chooseGameFolder() },
                    onChooseUpdateFolder = { gameFolderManager.chooseUpdateFolder() },
                    saves = saveManager,
                    logs = logManager,
                    onChangeLanguage = { value ->
                        LanguageManager.set(this, value)
                        recreate()
                    },
                )
            }
        }
        gameFolderManager.ensureGameFolder()
    }

    @Deprecated("Legacy Activity Result API is required by this target SDK flow")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        gameFolderManager.onPermissionResult(requestCode, grantResults)
    }

    @Deprecated("Legacy Activity Result API is required by this target SDK flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (saveManager.onActivityResult(requestCode, resultCode, data)) return
        if (logManager.onActivityResult(requestCode, resultCode, data)) return
        if (losslessDllManager.onPickerResult(requestCode, resultCode, data)) return
        gameFolderManager.onFolderResult(requestCode, resultCode, data)
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
