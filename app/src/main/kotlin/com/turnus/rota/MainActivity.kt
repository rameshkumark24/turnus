package com.turnus.rota

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.turnus.rota.ads.AdConfig
import com.turnus.rota.ads.AdGate
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.ui.RootState
import com.turnus.rota.ui.RootViewModel
import com.turnus.rota.ui.month.MonthScreen
import com.turnus.rota.ui.month.MonthViewModel
import com.turnus.rota.ui.setup.SetupScreen
import com.turnus.rota.notify.ReminderScheduler
import com.turnus.rota.ui.settings.SettingsScreen
import com.turnus.rota.ui.settings.SettingsViewModel
import com.turnus.rota.ui.setup.SetupViewModel
import com.turnus.rota.ui.year.YearScreen
import com.turnus.rota.ui.year.YearViewModel
import com.turnus.rota.ui.theme.TurnusTheme
import com.turnus.rota.widget.RotaWidgetReceiver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as TurnusApplication).repository

        // Consent first, then the SDK, then the remote switch. Started here
        // rather than in Application because the consent form is a dialog and
        // needs an Activity to show over.
        AdGate.start(this) {
            (application as TurnusApplication).applicationScope.launch {
                AdGate.applyConfig(AdConfig.refresh(applicationContext))
            }
        }

        setContent {
            TurnusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TurnusApp(repository, onRotaChanged = ::syncReminders)
                }
            }
        }
    }

    /**
     * Rebuilds the alarm window when the user leaves, not while they are here.
     *
     * onStop rather than onStart: this is the point at which a session's edits
     * are finished, so the window is built once against the final state instead
     * of repeatedly against a rota still being changed.
     *
     * Edits made *during* a session do not need an immediate reschedule,
     * because a stale alarm cannot post a wrong reminder: [ReminderReceiver]
     * re-reads the rota when it fires and drops anything that no longer says
     * the user is working. The gap this leaves is a newly added shift with no
     * alarm yet, which the next open or the daily worker picks up.
     */
    override fun onStop() {
        super.onStop()
        syncReminders()
    }

    private fun syncReminders() {
        val application = application as TurnusApplication
        // The application scope, not a lifecycle one: this deliberately outlives
        // the Activity that started it, which is the whole point of doing it as
        // the user walks away.
        application.applicationScope.launch {
            runCatching { ReminderScheduler.reschedule(applicationContext, application.repository) }
                .onFailure { Log.e("MainActivity", "could not reschedule reminders", it) }
            // The home screen must not keep showing a shift the user has just
            // changed. Same moment as the alarms: once the edits are done.
            runCatching { RotaWidgetReceiver.refresh(applicationContext) }
                .onFailure { Log.e("MainActivity", "could not refresh widget", it) }
        }
    }
}

/**
 * Setup and the calendar are alternative roots, not a stack.
 *
 * Saving a pattern flips the observed state, which swaps the screen — so
 * completing setup *replaces* it, and pressing back from the calendar exits the
 * app rather than walking into onboarding. A navigation graph would give the
 * same result only by carefully suppressing its own back behaviour.
 */
@Composable
private fun TurnusApp(repository: RotaRepository, onRotaChanged: () -> Unit) {
    // Three destinations now, all siblings of the calendar rather than a stack.
    // A navigation graph would buy argument passing and deep links, neither of
    // which exists here, in exchange for a dependency and a back-behaviour
    // override — the year and settings screens both return to the month, and
    // nothing else.
    var destination by rememberSaveable { mutableStateOf(Destination.Month) }
    // viewModel(), not remember: a remembered instance never enters a
    // ViewModelStore, so onCleared never runs and viewModelScope is never
    // cancelled. Every rotation, theme switch or font-size change would leave
    // another RootViewModel collecting the pattern table forever — and would
    // restart the setup wizard from the top, throwing away a built cycle.
    val rootViewModel: RootViewModel = viewModel(
        factory = remember(repository) { turnusViewModelFactory(repository) },
    )
    val state by rootViewModel.state.collectAsStateWithLifecycle()

    when (state) {
        // Deliberately blank: the database answers within a frame or two, and a
        // spinner that appears and vanishes reads worse than nothing at all.
        RootState.Loading -> Box(Modifier.fillMaxSize())

        RootState.NeedsSetup -> {
            val setupViewModel: SetupViewModel = viewModel(
                factory = remember(repository) { turnusViewModelFactory(repository) },
            )
            SetupScreen(setupViewModel, onComplete = { /* state flips on save */ })
        }

        RootState.Ready -> {
            // Hoisted above the branch so the year view can tell it which month
            // to open. Obtained from the store either way, so this is the same
            // instance the month screen was already using.
            val monthViewModel: MonthViewModel = viewModel(
                factory = remember(repository) { turnusViewModelFactory(repository) },
            )

            when (destination) {
                Destination.Month -> MonthScreen(
                    viewModel = monthViewModel,
                    onOpenSettings = { destination = Destination.Settings },
                    onOpenYear = { destination = Destination.Year },
                )

                Destination.Year -> {
                    val yearViewModel: YearViewModel = viewModel(
                        factory = remember(repository) { turnusViewModelFactory(repository) },
                    )
                    BackHandler { destination = Destination.Month }
                    YearScreen(
                        viewModel = yearViewModel,
                        onOpenMonth = { month ->
                            monthViewModel.showMonth(month)
                            destination = Destination.Month
                        },
                        onBack = { destination = Destination.Month },
                    )
                }

                Destination.Settings -> {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = remember(repository) { turnusViewModelFactory(repository) },
                    )
                    BackHandler { destination = Destination.Month }
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { destination = Destination.Month },
                        // Reschedule as soon as a setting changes rather than
                        // waiting for onStop: someone who just turned reminders
                        // on and is watching the screen should not have to leave
                        // the app for it to take effect.
                        onRemindersChanged = onRotaChanged,
                    )
                }
            }
        }
    }
}

/** The calendar's sibling screens. Not a stack — each one returns to the month. */
private enum class Destination { Month, Year, Settings }

/**
 * One factory for the three ViewModels the app has.
 *
 * A DI framework would be a dependency, a compile step and a layer of
 * indirection to build a graph this small.
 */
private fun turnusViewModelFactory(repository: RotaRepository): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { RootViewModel(repository) }
        initializer { SetupViewModel(repository) }
        initializer { MonthViewModel(repository) }
        initializer { SettingsViewModel(repository) }
        initializer { YearViewModel(repository) }
    }
