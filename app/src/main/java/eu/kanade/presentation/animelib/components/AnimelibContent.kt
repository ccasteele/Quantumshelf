package eu.kanade.presentation.animelib.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.core.prefs.PreferenceMutableState
import eu.kanade.domain.animelib.model.AnimelibAnime
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.library.model.LibraryDisplayMode
import eu.kanade.presentation.components.PullRefresh
import eu.kanade.presentation.components.rememberPagerState
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.tachiyomi.ui.animelib.AnimelibItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun AnimelibContent(
    categories: List<Category>,
    searchQuery: String?,
    selection: List<AnimelibAnime>,
    contentPadding: PaddingValues,
    currentPage: () -> Int,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    onChangeCurrentPage: (Int) -> Unit,
    onAnimeClicked: (Long) -> Unit,
    onContinueWatchingClicked: ((AnimelibAnime) -> Unit)?,
    onToggleSelection: (AnimelibAnime) -> Unit,
    onToggleRangeSelection: (AnimelibAnime) -> Unit,
    onRefresh: (Category?) -> Boolean,
    onGlobalSearchClicked: () -> Unit,
    getNumberOfAnimeForCategory: (Category) -> Int?,
    getDisplayModeForPage: @Composable (Int) -> LibraryDisplayMode,
    getColumnsForOrientation: (Boolean) -> PreferenceMutableState<Int>,
    getAnimelibForPage: (Int) -> List<AnimelibItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val coercedCurrentPage = remember { currentPage().coerceAtMost(categories.lastIndex) }
        val pagerState = rememberPagerState(coercedCurrentPage)

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && categories.size > 1) {
            LibraryTabs(
                categories = categories,
                currentPageIndex = pagerState.currentPage,
                getNumberOfMangaForCategory = getNumberOfAnimeForCategory,
            ) { scope.launch { pagerState.animateScrollToPage(it) } }
        }

        val notSelectionMode = selection.isEmpty()
        val onClickAnime = { anime: AnimelibAnime ->
            if (notSelectionMode) {
                onAnimeClicked(anime.anime.id)
            } else {
                onToggleSelection(anime)
            }
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val started = onRefresh(categories[currentPage()])
                if (!started) return@PullRefresh
                scope.launch {
                    // Fake refresh status but hide it after a second as it's a long running task
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            enabled = notSelectionMode,
        ) {
            AnimelibPager(
                state = pagerState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                pageCount = categories.size,
                hasActiveFilters = hasActiveFilters,
                selectedAnime = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                getDisplayModeForPage = getDisplayModeForPage,
                getColumnsForOrientation = getColumnsForOrientation,
                getAnimelibForPage = getAnimelibForPage,
                onClickAnime = onClickAnime,
                onLongClickAnime = onToggleRangeSelection,
                onClickContinueWatching = onContinueWatchingClicked,
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }
    }
}