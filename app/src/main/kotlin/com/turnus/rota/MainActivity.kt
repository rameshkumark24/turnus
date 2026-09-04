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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val rootViewModel = remember { RootViewModel(repository) }
    val state by rootViewModel.state.collectAsStateWithLifecycle()

    when (state) {
        // Deliberately blank: the database answers within a frame or two, and a
        // spinner that appears and vanishes reads worse than nothing at all.
        RootState.Loading -> Box(Modifier.fillMaxSize())

        RootState.NeedsSetup -> {
            val setupViewModel = remember { SetupViewModel(repository) }
            SetupScreen(setupViewModel, onComplete = { /* state flips on save */ })
        }

        RootState.Ready -> {
            val monthViewModel = remember { MonthViewModel(repository) }
            MonthScreen(monthViewModel)
        }
    }
}
