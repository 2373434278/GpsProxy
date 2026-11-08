package com.gpsproxy.dev.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gpsproxy.dev.AppGraph
import com.gpsproxy.dev.domain.place.DefaultLocation
import com.gpsproxy.dev.ui.feature.apps.TargetAppsViewModel
import com.gpsproxy.dev.ui.feature.container.ContainerViewModel
import com.gpsproxy.dev.ui.feature.diagnostic.DiagnosticsViewModel
import com.gpsproxy.dev.ui.feature.favorites.FavoritesViewModel
import com.gpsproxy.dev.ui.feature.home.HomeViewModel
import com.gpsproxy.dev.ui.feature.permissions.PermissionsViewModel
import com.gpsproxy.dev.ui.feature.pick.PickViewModel
import com.gpsproxy.dev.ui.feature.route.RouteRunningViewModel
import com.gpsproxy.dev.ui.feature.route.RouteViewModel
import com.gpsproxy.dev.ui.feature.route.RoutesViewModel
import com.gpsproxy.dev.ui.feature.search.SearchViewModel
import com.gpsproxy.dev.ui.feature.settings.SettingsViewModel
import com.gpsproxy.dev.ui.feature.startup.StartupViewModel
import com.gpsproxy.dev.ui.screens.ContainerAppsScreen
import com.gpsproxy.dev.ui.screens.ContainerInitScreen
import com.gpsproxy.dev.ui.screens.DiagnosticScreen
import com.gpsproxy.dev.ui.screens.EngineScreen
import com.gpsproxy.dev.ui.screens.FavoritesScreen
import com.gpsproxy.dev.ui.screens.HomeScreen
import com.gpsproxy.dev.ui.screens.IntroScreen
import com.gpsproxy.dev.ui.screens.PermissionsScreen
import com.gpsproxy.dev.ui.screens.PickScreen
import com.gpsproxy.dev.ui.screens.RouteConfigScreen
import com.gpsproxy.dev.ui.screens.RouteRunningScreen
import com.gpsproxy.dev.ui.screens.RoutesScreen
import com.gpsproxy.dev.ui.screens.SearchScreen
import com.gpsproxy.dev.ui.screens.SettingsScreen
import com.gpsproxy.dev.ui.screens.TargetAppsScreen
import com.gpsproxy.dev.ui.screens.UnsupportedScreen
import kotlinx.coroutines.flow.StateFlow

/** 选点页的三种语义：普通选点、为巡航选择终点，或选择途经点。 */
enum class PickMode(val raw: String) {
    SIMULATE("simulate"),
    ROUTE_END("routeEnd"),
    ROUTE_WAYPOINT("routeWaypoint");

    companion object {
        fun parse(raw: String?): PickMode = entries.firstOrNull { it.raw == raw } ?: SIMULATE
    }
}

/**
 * 导航图。
 *
 * 每个目的地通过 `viewModel(factory = ...)` 获得**自己**的 ViewModel：
 * - 屏幕不再共享一个上帝 ViewModel，页面之间只共享显式的会话状态
 *   （选点草稿、巡航草稿、模拟引擎）；
 * - 屏幕被回收时对应 VM 一并回收，不会把搜索/容器状态一直留在内存里；
 * - 需要跨页的"正在选的点"由组合根持有的 Store 负责，语义清晰。
 */
@Composable
fun GpsProxyNavHost(
    startHome: Boolean,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
    onExit: () -> Unit,
) {
    val navController = rememberNavController()
    val startDestination = remember(startHome) {
        if (startHome) Routes.HOME else Routes.INTRO
    }

    androidx.compose.runtime.LaunchedEffect(navController) {
        pendingRoute.collect { route ->
            if (route == null) return@collect
            when (val destination = OverlayDestination.parse(route)) {
                OverlayDestination.FAVORITES -> navController.navigate(Routes.FAVORITES) { launchSingleTop = true }
                OverlayDestination.PICK -> {
                    preparePickFromCurrent()
                    navController.navigate(Routes.pick()) { launchSingleTop = true }
                }
                OverlayDestination.APPS -> navController.navigate(Routes.APPS) { launchSingleTop = true }
                OverlayDestination.ROUTE -> navController.navigate(Routes.ROUTE) { launchSingleTop = true }
                OverlayDestination.UNKNOWN -> Unit
            }
            onRouteConsumed()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.INTRO) {
            val vm: StartupViewModel = viewModel(factory = StartupViewModel.Factory)
            IntroScreen(
                vm = vm,
                onAgree = {
                    navController.navigate(
                        if (vm.capability.supported) Routes.ENGINE else Routes.UNSUPPORTED,
                    )
                },
                onExit = onExit,
            )
        }
        composable(Routes.UNSUPPORTED) {
            val vm: StartupViewModel = viewModel(factory = StartupViewModel.Factory)
            UnsupportedScreen(
                vm = vm,
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Routes.INTRO)
                },
            )
        }
        composable(Routes.ENGINE) {
            val vm: StartupViewModel = viewModel(factory = StartupViewModel.Factory)
            EngineScreen(
                vm = vm,
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.INTRO) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onUnsupported = { navController.navigate(Routes.UNSUPPORTED) },
                onContainerInit = { navController.navigate(Routes.CONTAINER_INIT) },
            )
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            HomeScreen(
                vm = vm,
                onSearch = { navController.navigate(Routes.SEARCH) },
                onPick = {
                    vm.preparePickFromCurrent()
                    navController.navigate(Routes.pick())
                },
                onFavorites = { navController.navigate(Routes.FAVORITES) },
                onRoute = { navController.navigate(Routes.ROUTE) },
                onApps = { navController.navigate(Routes.APPS) },
                // 阻断弹窗的引导目标是容器应用管理，不是"目标应用"收藏页。
                onContainerApps = {
                    navController.navigate(Routes.CONTAINER_APPS) { launchSingleTop = true }
                },
                onContainerInit = {
                    navController.navigate(Routes.CONTAINER_INIT) { launchSingleTop = true }
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onPermissions = { navController.navigate(Routes.PERMISSIONS) },
                onRunning = { navController.navigate(Routes.RUNNING) },
            )
        }
        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
            SearchScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onSelectPoi = { navController.navigate(Routes.pick()) },
                onGoPick = {
                    preparePickFromCurrent()
                    navController.navigate(Routes.pick())
                },
            )
        }
        composable(
            route = Routes.PICK,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = PickMode.SIMULATE.raw
                }
            ),
        ) { entry ->
            val mode = PickMode.parse(entry.arguments?.getString("mode"))
            val vm: PickViewModel = viewModel(factory = PickViewModel.Factory)
            PickScreen(
                vm = vm,
                mode = mode.raw,
                onBack = { navController.popBackStack() },
                onConfirmed = {
                    when (mode) {
                        PickMode.ROUTE_END, PickMode.ROUTE_WAYPOINT -> navController.popBackStack()
                        PickMode.SIMULATE -> navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                },
            )
        }
        composable(Routes.FAVORITES) {
            val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
            FavoritesScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onAdd = {
                    preparePickFromCurrent()
                    navController.navigate(Routes.pick())
                },
                onGoHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.ROUTE) {
            val vm: RouteViewModel = viewModel(factory = RouteViewModel.Factory)
            RouteConfigScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onPickEnd = { navController.navigate(Routes.pick(PickMode.ROUTE_END)) },
                onPickWaypoint = { navController.navigate(Routes.pick(PickMode.ROUTE_WAYPOINT)) },
                onOpenRoutes = { navController.navigate(Routes.ROUTES) },
                onStarted = { navController.navigate(Routes.RUNNING) },
            )
        }
        composable(Routes.ROUTES) {
            val vm: RoutesViewModel = viewModel(factory = RoutesViewModel.Factory)
            RoutesScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onLoaded = { navController.popBackStack() },
            )
        }
        composable(Routes.RUNNING) {
            val vm: RouteRunningViewModel = viewModel(factory = RouteRunningViewModel.Factory)
            RouteRunningScreen(
                vm = vm,
                onBackHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onFavorites = { navController.navigate(Routes.FAVORITES) },
                onPick = {
                    preparePickFromCurrent()
                    navController.navigate(Routes.pick())
                },
            )
        }
        composable(Routes.APPS) {
            val vm: TargetAppsViewModel = viewModel(factory = TargetAppsViewModel.Factory)
            TargetAppsScreen(vm = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTAINER_INIT) {
            val vm: ContainerViewModel = viewModel(factory = ContainerViewModel.Factory)
            ContainerInitScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onApps = { navController.navigate(Routes.CONTAINER_APPS) { launchSingleTop = true } },
            )
        }
        composable(Routes.CONTAINER_APPS) {
            val vm: ContainerViewModel = viewModel(factory = ContainerViewModel.Factory)
            ContainerAppsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onInit = { navController.navigate(Routes.CONTAINER_INIT) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            SettingsScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
                onDiagnostic = { navController.navigate(Routes.DIAGNOSTIC) },
                onContainer = { navController.navigate(Routes.CONTAINER_INIT) },
            )
        }
        composable(Routes.DIAGNOSTIC) {
            val vm: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory)
            DiagnosticScreen(vm = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.PERMISSIONS) {
            val vm: PermissionsViewModel = viewModel(factory = PermissionsViewModel.Factory)
            PermissionsScreen(vm = vm, onBack = { navController.popBackStack() })
        }
    }
}

/** 选点前把"当前模拟位置"写进共享草稿，供选点页初始化。 */
private fun preparePickFromCurrent() {
    val state = AppGraph.engine.state.value
    AppGraph.selection.prepare(
        point = state.point ?: DefaultLocation.POINT,
        address = state.address.ifBlank { DefaultLocation.ADDRESS },
    )
}
