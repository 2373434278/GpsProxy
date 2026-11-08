package com.gpsproxy.dev.core.i18n.text

/** 搜索、地图选点与收藏历史的文案。 */
interface PlaceTexts {
    val searchTitle: String
    val searchPlaceholder: String
    val searchAction: String
    fun searchTabLabel(tab: String): String
    fun poiCategoryLabel(category: String): String
    fun poiSourceLabel(source: String): String
    fun dataSource(label: String): String
    val builtinHint: String
    val searchFailed: String
    val searchError: String
    val fallbackWarning: String
    val unnamedPlace: String
    fun nearAddress(city: String, name: String): String
    val emptyHistoryTitle: String
    val emptyHistoryMessage: String
    val recentSearches: String
    val clear: String
    val noResultsTitle: String
    val noResultsMessage: String
    val goPick: String
    val loading: String
    val loadMore: String

    val favoritesTitle: String
    val addNew: String
    val favoritesTab: String
    val historyTab: String
    val emptyFavoritesTitle: String
    val emptyFavoritesMessage: String
    val mapPick: String
    val noHistoryTitle: String
    val noHistoryMessage: String
    fun recentCount(count: Int): String
    fun deletedUndo(name: String): String
    val undo: String
    val renameFavorite: String
    val renameFavoriteHint: String

    val pickWatermark: String
    val reverseResolving: String
    fun mockPickAddress(coords: String): String
    val chipRouteEnd: String
    val chipWaypoint: String
    val reverseTag: String
    fun blurNotice(radiusMeters: Int): String
    val pasteCoords: String
    val copy: String
    val latitude: String
    val longitude: String
    val invalidRangeMessage: String
    val setAsWaypoint: String
    val setAsEnd: String
    val setAsMock: String
    val pickHintPrefix: String
    val waypointOrderHint: String
    fun coordSystemHint(label: String): String
    val unparsableCoords: String
    val clipboardEmpty: String
    val pickFirst: String
    val mockStarted: String
}