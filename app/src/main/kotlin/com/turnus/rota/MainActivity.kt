package com.turnus.rota

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
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
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.ui.OnDateChange
import com.turnus.rota.ui.RootState
import com.turnus.rota.ui.RootViewModel
import com.turnus.rota.ui.TodayClock
import com.turnus.rota.ui.UpdateRequiredDialog
import com.turnus.rota.ui.month.MonthScreen
import com.turnus.rota.ui.month.MonthViewModel
import com.turnus.rota.ui.setup.SetupScreen
import com.turnus.rota.notify.ReminderScheduler
import com.turnus.rota.ui.settings.SettingsScreen
import com.turnus.rota.ui.settings.SettingsViewModel
import com.turnus.rota.ui.setup.SetupViewModel
import com.turnus.rota.ui.shifts.ShiftEditorScreen
import com.turnus.rota.ui.shifts.ShiftEditorViewModel
import com.turnus.rota.ui.year.YearScreen
import com.turnus.rota.ui.year.YearViewModel
import com.turnus.rota.ui.theme.TurnusTheme
import com.turnus.rota.widget.RotaWidgetReceiver
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    /**
     * The day a notification tap asked for, or null.
     *
     * Compose state on the Activity rather than a read of `getIntent()` inside
     * the composition: a tap that arrives while the app is already running goes
     * to [onNewIntent], which recomposes nothing by itself, and reading the
     * intent during composition would replay the same tap on every rotation.
     * The counter makes two taps on the same day two distinct requests.
     */
    private var showDay by mutableStateOf<ShowDay?>(null)
    private var showDayCount = 0

    /**
     * Set only by a successful config read that names a version newer than this
     * one. Never true by default, never true offline on a first run, and never
     * true because something failed -- see [AdConfig].
     */
    private var updateRequired by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Only on a real start. On a recreation — a rotation, a font-size
        // change, the process coming back — the launching intent is delivered
        // again, and acting on it a second time would drag the user back to the
        // calendar from wherever they had since navigated.
        if (savedInstanceState == null) consume(intent)

        val application = application as TurnusApplication
        val repository = application.repository
        val clock = application.clock

        setContent {
            TurnusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        TurnusApp(
                            repository = repository,
                            clock = clock,
                            onRotaChanged = ::syncReminders,
                            onCalendarShown = ::startAds,
                            showDay = showDay,
                        )
                        // Over the app rather than instead of it. A dialog with
                        // a blank screen behind it reads as a crash; the user
                        // should be able to see their calendar is still there
                        // while being told they cannot use this build of it.
                        if (updateRequired) UpdateRequiredDialog()
                    }
                }
            }
        }
    }

    /**
     * A tap that arrived while the app was already running.
     *
     * setIntent so that anything reading the Activity's intent later sees the
     * one that actually brought it forward, which is the documented contract
     * and cheaper than finding out it is not.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        if (intent?.action != ACTION_SHOW_DAY) return
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE)
        if (day == Long.MIN_VALUE) return
        showDay = ShowDay(DayNumber(day), ++showDayCount)
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

    /**
     * Asks for ad consent only once the calendar is on screen.
     *
     * Not in onCreate, where it used to be. In the EEA the consent form is a
     * dense legal wall naming hundreds of ad partners, and putting it in front
     * of someone who has not yet seen the app do a single useful thing is
     * asking them to accept a stranger's terms sight unseen. Deferring it means
     * the first thing a new user meets is the setup wizard, and the form only
     * appears once they have a working rota in front of them.
     *
     * This is the same rule the notification permission already follows, and
     * for the same reason: ask when the answer means something.
     */
    private fun startAds() {
        AdGate.start(this) {
            (application as TurnusApplication).applicationScope.launch {
                val config = AdConfig.refresh(applicationContext)
                AdGate.applyConfig(config)
                // Snapshot state is safe to write from any thread; the
                // recomposition it schedules happens on the main one.
                updateRequired = config.minVersion > BuildConfig.VERSION_CODE
            }
        }
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

    companion object {
        /** Set by [com.turnus.rota.notify.ReminderReceiver] on a notification tap. */
        const val ACTION_SHOW_DAY = "com.turnus.rota.action.SHOW_DAY"

        /** A [DayNumber] value — a civil day count, never epoch millis. */
        const val EXTRA_DAY = "com.turnus.rota.extra.DAY"
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
private fun TurnusApp(
    repository: RotaRepository,
    clock: TodayClock,
    onRotaChanged: () -> Unit,
    onCalendarShown: () -> Unit,
    showDay: ShowDay?,
) {
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
        factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
    )
    val state by rootViewModel.state.collectAsStateWithLifecycle()

    when (state) {
        // Deliberately blank: the database answers within a frame or two, and a
        // spinner that appears and vanishes reads worse than nothing at all.
        RootState.Loading -> Box(Modifier.fillMaxSize())

        RootState.NeedsSetup -> {
            val setupViewModel: SetupViewModel = viewModel(
                factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
            )
            SetupScreen(setupViewModel, onComplete = { /* state flips on save */ })
        }

        RootState.Ready -> {
            // Consent is asked for here, not at launch: by this point a rota
            // exists and the calendar is on screen, so the user has seen what
            // they are being asked to fund. Keyed on Unit so moving between
            // the calendar, year and settings does not re-ask.
            LaunchedEffect(Unit) { onCalendarShown() }

            // Hoisted above the branch so the year view can tell it which month
            // to open. Obtained from the store either way, so this is the same
            // instance the month screen was already using.
            val monthViewModel: MonthViewModel = viewModel(
                factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
            )

            // One receiver for the whole app, not one per screen. The day is
            // held once now, so a screen that was away while it changed comes
            // back to the right answer rather than to its own stale copy.
            OnDateChange(clock::refresh)

            // A notification tap lands on the calendar, on the month holding
            // the shift it was about — both halves matter. Sending the user to
            // the month view while it still shows whatever they last browsed to
            // answers the tap with the wrong grid, which is the same failure
            // wearing a different screen.
            LaunchedEffect(showDay) {
                val request = showDay ?: return@LaunchedEffect
                monthViewModel.showMonth(YearMonth.from(request.day.toLocalDate()))
                destination = Destination.Month
            }

            when (destination) {
                Destination.Month -> MonthScreen(
                    viewModel = monthViewModel,
                    onOpenSettings = { destination = Destination.Settings },
                    onOpenYear = { destination = Destination.Year },
                    onRotaChanged = onRotaChanged,
                )

                Destination.Year -> {
                    val yearViewModel: YearViewModel = viewModel(
                        factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
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

                Destination.Shifts -> {
                    val shiftViewModel: ShiftEditorViewModel = viewModel(
                        factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
                    )
                    BackHandler { destination = Destination.Settings }
                    ShiftEditorScreen(
                        viewModel = shiftViewModel,
                        // Back to settings, not the calendar: this screen is
                        // reached from there and nowhere else.
                        onBack = { destination = Destination.Settings },
                    )
                }

                Destination.Pattern -> {
                    // The same wizard that ran at first launch, re-entered.
                    // Rebuilding a second, subtly different rota editor is how
                    // the two drift apart until one of them has the anchor bug.
                    val setupViewModel: SetupViewModel = viewModel(
                        factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
                    )
                    // Idempotent: this runs again on every rotation and font
                    // change, and priming twice would discard a built cycle.
                    LaunchedEffect(Unit) { setupViewModel.editActive() }
                    val leave = {
                        setupViewModel.stopEditing()
                        destination = Destination.Settings
                    }
                    SetupScreen(
                        viewModel = setupViewModel,
                        onComplete = leave,
                        onExit = leave,
                    )
                }

                Destination.Settings -> {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = remember(repository, clock) { turnusViewModelFactory(repository, clock) },
                    )
                    BackHandler { destination = Destination.Month }
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { destination = Destination.Month },
                        // Reschedule as soon as a setting changes rather than
                        // waiting for onStop: someone who just turned reminders
                        // on and is watching the screen should not have to leave
                        // the app for it to take effect.
                        onRotaChanged = onRotaChanged,
                        onEditShifts = { destination = Destination.Shifts },
                        onChangeRota = { destination = Destination.Pattern },
                    )
                }
            }
        }
    }
}

/**
 * A request to show one day, carrying its own identity.
 *
 * [sequence] is what makes two taps on the same day two events rather than one:
 * without it a second tap is an equal value, the effect keyed on it in
 * [TurnusApp] never re-runs, and someone who has since walked off to another
 * screen stays there.
 */
private data class ShowDay(val day: DayNumber, val sequence: Int)

/** The calendar's sibling screens. Not a stack — each one returns to the month. */
private enum class Destination { Month, Year, Settings, Shifts, Pattern }

/**
 * One factory for the three ViewModels the app has.
 *
 * A DI framework would be a dependency, a compile step and a layer of
 * indirection to build a graph this small.
 */
private fun turnusViewModelFactory(
    repository: RotaRepository,
    clock: TodayClock,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { RootViewModel(repository) }
        initializer { SetupViewModel(repository) }
        initializer { MonthViewModel(repository, clock) }
        initializer { SettingsViewModel(repository, clock) }
        initializer { YearViewModel(repository, clock) }
        initializer { ShiftEditorViewModel(repository) }
    }
