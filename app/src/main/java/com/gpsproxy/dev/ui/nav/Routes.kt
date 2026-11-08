package com.gpsproxy.dev.ui.nav

object Routes {
    const val INTRO = "intro"
    const val UNSUPPORTED = "unsupported"
    const val ENGINE = "engine"
    const val HOME = "home"
    const val SEARCH = "search"
    const val PICK = "pick?mode={mode}"
    const val FAVORITES = "favorites"
    const val ROUTE = "route"
    const val ROUTES = "routes"
    const val RUNNING = "running"
    const val APPS = "apps"
    const val CONTAINER_INIT = "container_init"
    const val CONTAINER_APPS = "container_apps"
    const val SETTINGS = "settings"
    const val DIAGNOSTIC = "diagnostic"
    const val PERMISSIONS = "permissions"

    fun pick(mode: PickMode = PickMode.SIMULATE): String = "pick?mode=${mode.raw}"
}
