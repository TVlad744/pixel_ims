package com.takaisaisei.pixelims

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.takaisaisei.pixelims.system.NotificationController
import com.takaisaisei.pixelims.ui.MainScreen
import com.takaisaisei.pixelims.ui.MainViewModel
import com.takaisaisei.pixelims.ui.theme.PixelIMSTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val notifications by lazy { NotificationController(this) }

    private var pendingPairingPrompt = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && pendingPairingPrompt) {
                notifications.showPairingPrompt()
            } else if (!granted && pendingPairingPrompt) {
                toast(getString(R.string.msg_notification_permission_needed))
            }
            pendingPairingPrompt = false
        }

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NotificationController.ACTION_PAIR) return
            val code = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(NotificationController.KEY_PAIRING_CODE)
                ?.toString()
                ?.trim()
            if (!code.isNullOrEmpty()) viewModel.submitPairingCode(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerPairingReceiver()
        ensureNotificationPermissionAtStartup()
        observePairingSuccess()

        enableEdgeToEdge()
        setContent {
            PixelIMSTheme {
                MainScreen(
                    onEnableWirelessDebugging = ::onEnableWirelessDebugging,
                    viewModel = viewModel,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(pairingReceiver) }
        notifications.cancelPairing()
    }

    private fun registerPairingReceiver() {
        val filter = IntentFilter(NotificationController.ACTION_PAIR)
        ContextCompat.registerReceiver(
            this,
            pairingReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun observePairingSuccess() {
        lifecycleScope.launch {
            viewModel.pairingSucceeded.collect {
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun ensureNotificationPermissionAtStartup() {
        if (!hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun onEnableWirelessDebugging() {
        if (hasNotificationPermission()) {
            notifications.showPairingPrompt()
        } else {
            pendingPairingPrompt = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        openWirelessDebuggingSettings()
    }

    private fun openWirelessDebuggingSettings() {
        val highlightArgs = Bundle().apply {
            putString(EXTRA_FRAGMENT_ARG_KEY, ADB_WIRELESS_PREF_KEY)
        }
        val devOptionsHighlighted = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, ADB_WIRELESS_PREF_KEY)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, highlightArgs)

        runCatching { startActivity(devOptionsHighlighted) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .onFailure { toast(getString(R.string.msg_developer_options_not_found)) }
    }

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private companion object {
        const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
        const val ADB_WIRELESS_PREF_KEY = "toggle_adb_wireless"
    }
}
