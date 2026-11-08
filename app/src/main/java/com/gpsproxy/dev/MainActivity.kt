package com.gpsproxy.dev

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpsproxy.dev.ui.components.MessageHost
import com.gpsproxy.dev.ui.feature.app.AppViewModel
import com.gpsproxy.dev.ui.icons.GpIcons
import com.gpsproxy.dev.ui.i18n.LocalStrings
import com.gpsproxy.dev.ui.nav.GpsProxyNavHost
import com.gpsproxy.dev.ui.theme.Gp
import com.gpsproxy.dev.ui.theme.GpsProxyTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 唯一的 Activity：承载 Compose 导航与全局消息宿主。
 *
 * 它不再持有业务 ViewModel——每个目的地自己创建各自的 ViewModel，
 * Activity 只负责启动门禁（[AppViewModel.ready]）、外部深链
 * （悬浮球/通知带来的目的地）与生命周期回调。
 */
class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.initialize(this)
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            val strings by AppGraph.i18n.strings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalStrings provides strings) {
                GpsProxyTheme {
                    val appVm: AppViewModel = viewModel(factory = AppViewModel.Factory)
                    val ready by appVm.ready.collectAsStateWithLifecycle()
                    val startHome by appVm.startHome.collectAsStateWithLifecycle()
                    LifecycleResumeEffect(Unit) {
                        appVm.refreshEnvironment()
                        onPauseOrDispose { }
                    }
                    Box(Modifier.fillMaxSize().background(Gp.Canvas)) {
                        if (ready) {
                            GpsProxyNavHost(
                                startHome = startHome,
                                pendingRoute = pendingRoute,
                                onRouteConsumed = { pendingRoute.value = null },
                                onExit = { finishAffinity() },
                            )
                        } else {
                            SplashScreen()
                        }
                        MessageHost(AppGraph.messenger)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "extra_route"
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3D96FF), Gp.Blue))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = .18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GpIcons.Pin, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("GpsProxy", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = com.gpsproxy.dev.core.i18n.StringsHolder.text { onboarding.splashTagline },
                color = Color.White.copy(alpha = .78f),
                fontSize = 11.5.sp,
            )
        }
    }
}
