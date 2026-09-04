package com.turnus.rota.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the app has a rota yet.
 *
 * [Loading] exists so the first frame is never the setup screen flashing past
 * for someone who has used the app for months — the database answers a few
 * milliseconds after the window opens, and a wrong guess in between is jarring.
 */
sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data object Ready : RootState
}

class RootViewModel(repository: RotaRepository) : ViewModel() {

    val state: StateFlow<RootState> = repository.observeActivePattern()
        .map { if (it == null) RootState.NeedsSetup else RootState.Ready }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = RootState.Loading,
        )
}
