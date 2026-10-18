package com.rendyhd.vicu.di

import com.rendyhd.vicu.ui.SyncStateViewModel
import com.rendyhd.vicu.ui.components.collab.CollabViewModel
import com.rendyhd.vicu.ui.components.selection.SelectionViewModel
import com.rendyhd.vicu.ui.navigation.DrawerViewModel
import com.rendyhd.vicu.ui.screens.anytime.AnytimeViewModel
import com.rendyhd.vicu.ui.screens.comments.TaskCommentsViewModel
import com.rendyhd.vicu.ui.screens.customlist.CustomListViewModel
import com.rendyhd.vicu.ui.screens.inbox.InboxViewModel
import com.rendyhd.vicu.ui.screens.logbook.LogbookViewModel
import com.rendyhd.vicu.ui.screens.project.ProjectViewModel
import com.rendyhd.vicu.ui.screens.review.ReviewViewModel
import com.rendyhd.vicu.ui.screens.routines.RoutinesViewModel
import com.rendyhd.vicu.ui.screens.search.SearchViewModel
import com.rendyhd.vicu.ui.screens.settings.SettingsViewModel
import com.rendyhd.vicu.ui.screens.setup.SetupViewModel
import com.rendyhd.vicu.ui.screens.tag.TagViewModel
import com.rendyhd.vicu.ui.screens.taskdetail.TaskDetailViewModel
import com.rendyhd.vicu.ui.screens.taskentry.TaskEntryViewModel
import com.rendyhd.vicu.ui.screens.today.TodayViewModel
import com.rendyhd.vicu.ui.screens.upcoming.UpcomingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Every view model of the shared UI; the same on each platform. */
val viewModelModule = module {
    viewModelOf(::SyncStateViewModel)
    viewModelOf(::SelectionViewModel)
    viewModelOf(::DrawerViewModel)
    viewModelOf(::AnytimeViewModel)
    viewModelOf(::CustomListViewModel)
    viewModelOf(::InboxViewModel)
    viewModelOf(::LogbookViewModel)
    viewModelOf(::ProjectViewModel)
    viewModelOf(::ReviewViewModel)
    viewModelOf(::RoutinesViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SetupViewModel)
    viewModelOf(::TagViewModel)
    viewModelOf(::TaskDetailViewModel)
    viewModelOf(::TaskEntryViewModel)
    viewModelOf(::TodayViewModel)
    viewModelOf(::UpcomingViewModel)
    viewModelOf(::CollabViewModel)
    viewModelOf(::TaskCommentsViewModel)
}
