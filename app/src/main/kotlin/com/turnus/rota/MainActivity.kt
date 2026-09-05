package com.turnus.rota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.turnus.rota.ui.setup.SetupViewModel
import com.turnus.rota.ui.theme.TurnusTheme

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
                    TurnusApp(repository)
                }
            }
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
private fun TurnusApp(repository: RotaRepository) {
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
            val monthViewModel: MonthViewModel = viewModel(
                factory = remember(repository) { turnusViewModelFactory(repository) },
            )
            MonthScreen(monthViewModel)
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
    }
