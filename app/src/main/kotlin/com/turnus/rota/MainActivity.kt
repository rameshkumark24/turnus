package com.turnus.rota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.turnus.rota.ui.month.MonthScreen
import com.turnus.rota.ui.month.MonthViewModel
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
                    // A single screen for now. Navigation arrives with the
                    // second one — adding a graph for one destination is
                    // ceremony, not architecture.
                    val viewModel = remember { MonthViewModel(repository) }
                    Box(Modifier.fillMaxSize()) {
                        MonthScreen(viewModel)
                    }
                }
            }
        }
    }
}
