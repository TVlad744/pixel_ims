package com.takaisaisei.pixelims

import android.app.Application
import android.util.Log
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import okio.Path.Companion.toPath
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File

/**
 * Process-wide, low-level initialization that must run for *every* entry point, not just the UI.
 * In particular the Kadb key store powers loopback ADB from the headless
 * [com.takaisaisei.pixelims.boot.ReapplyWorker] when the app process is started without the
 * Activity.
 */
class PixelImsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initHiddenApiBypass()
        initKadbCert()
    }

    private fun initHiddenApiBypass() {
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onFailure { Log.e(TAG, "Failed to apply HiddenApiBypass exemptions", it) }
    }

    private fun initKadbCert() {
        runCatching {
            val privateKeyFile = File(filesDir, "kadb_private_key.pem")
            KadbCert.configure(
                store = OkioFilePrivateKeyStore(privateKeyFile.absolutePath.toPath()),
                policy = KadbCertPolicy(),
                additionalPrivateKeysPem = emptyList(),
            )
            KadbCert.ensureReady()
        }.onFailure { Log.e(TAG, "Failed to configure KadbCert", it) }
    }

    private companion object {
        const val TAG = "PixelIMS"
    }
}
