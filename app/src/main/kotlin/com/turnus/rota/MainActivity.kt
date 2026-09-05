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
import com.turnus.rota.ui.theme.TurnusTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as TurnusApplication).repository

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
 *
 * A graph earns its place when the year view, settings and the day sheet land.
 * One destination does not need one.
 */
@Composable
private fun TurnusApp(repository: RotaRepository, onRotaChanged: () -> Unit) {
    // Settings is the second destination, and still not enough to earn a
    // navigation graph: one boolean expresses it exactly, and back is handled
    // by the same BackHandler pattern the setup wizard already uses.
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

        RootState.Ready -> if (showSettings) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = remember(repository) { turnusViewModelFactory(repository) },
            )
            BackHandler { showSettings = false }
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { showSettings = false },
                // Reschedule as soon as a setting changes rather than waiting
                // for onStop: someone who just turned reminders on and is
                // watching the screen should not have to leave the app for it
                // to take effect.
                onRemindersChanged = onRotaChanged,
            )
        } else {
            val monthViewModel: MonthViewModel = viewModel(
                factory = remember(repository) { turnusViewModelFactory(repository) },
            )
            MonthScreen(monthViewModel, onOpenSettings = { showSettings = true })
        }
    }
}

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
    }
