package com.gpsproxy.dev

import android.app.Application
import android.content.Context
import android.util.Log
import com.gpsproxy.dev.core.log.LogBus
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

class GpsProxyApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        try {
            BlackBoxCore.get().closeCodeInit()
            BlackBoxCore.get().onBeforeMainApplicationAttach(this, base)
            BlackBoxCore.get().doAttachBaseContext(base, object : ClientConfiguration() {
                override fun getHostPackageName(): String = base.packageName
                override fun isEnableDaemonService(): Boolean = false
            })
            BlackBoxCore.get().onAfterMainApplicationAttach(this, base)
            containerReady = true
        } catch (t: Throwable) {
            Log.e(TAG, "BlackBox attach failed, official mock only", t)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (containerReady) {
            try {
                BlackBoxCore.get().doCreate()
            } catch (t: Throwable) {
                containerReady = false
                Log.e(TAG, "BlackBox doCreate failed, official mock only", t)
            }
        }
        val mainProcess = !containerReady || try {
            BlackBoxCore.get().isMainProcess
        } catch (t: Throwable) {
            true
        }
        if (mainProcess) {
            AppGraph.initialize(this)
            LogBus.info("GpsProxy ${BuildConfig.VERSION_NAME} started · container=$containerReady")
        }
    }

    companion object {
        private const val TAG = "GpsProxyApp"

        @Volatile
        var containerReady = false
            private set
    }
}