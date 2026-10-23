package com.rendyhd.vicu.ui.components.shared

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight

/**
 * The scroll of a list screen's top bar: the bar stays put with its title next to the menu button
 * (there is no large title to fold, it took space and folded back inconsistently) and its container
 * turns to the surface container colour while the list is scrolled under it. Give [modifier] to the
 * screen's Scaffold (it carries the nested scroll) and the object itself to [VicuTopAppBar]. Keeps
 * the experimental Material 3 scroll behaviour out of the screens.
 */
class VicuTopBarScroll @OptIn(ExperimentalMaterial3Api::class) internal constructor(
    internal val behavior: TopAppBarScrollBehavior,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    val modifier: Modifier = Modifier.nestedScroll(behavior.nestedScrollConnection)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberVicuTopBarScroll(): VicuTopBarScroll {
    val behavior = TopAppBarDefaults.pinnedScrollBehavior()
    return remember(behavior) { VicuTopBarScroll(behavior) }
}

/**
 * The top bar of a screen. With [scroll] it is a list screen's bar (bold title, its container turning
 * to the surface container colour once scrolled); without it, the plain bar (settings, review,
 * routines). [extraActions] come before Search, [trailingActions] after it (a screen's own "more" menu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VicuTopAppBar(
    title: @Composable () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToSearch: (() -> Unit)? = null,
    extraActions: @Composable (RowScope.() -> Unit)? = null,
    scroll: VicuTopBarScroll? = null,
    trailingActions: @Composable (RowScope.() -> Unit)? = null,
) {
    val navigationIcon: @Composable () -> Unit = {
        IconButton(onClick = onOpenDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Open menu")
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        extraActions?.invoke(this)
        if (onNavigateToSearch != null) {
            IconButton(onClick = onNavigateToSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
        trailingActions?.invoke(this)
    }
    if (scroll == null) {
        TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions)
        return
    }
    val boldTitle: @Composable () -> Unit = {
        ProvideTextStyle(LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)) { title() }
    }
    TopAppBar(
        title = boldTitle,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scroll.behavior,
    )
}
