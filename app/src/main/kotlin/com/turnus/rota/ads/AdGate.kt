package com.turnus.rota.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.turnus.rota.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single answer to "may this app show an ad right now?"
 *
 * Two independent gates, both of which must be open:
 *
 * 1. **Consent.** In the EEA and UK an ad cannot be requested until the user has
 *    been asked. That is a legal requirement, not a preference, and it is why
 *    the ads SDK is not initialised until [ConsentInformation.canRequestAds]
 *    says so — initialising earlier would start fetching before anyone had been
 *    asked anything.
 * 2. **The remote switch.** [AdConfig] can turn a slot off without shipping a
 *    release, which is the only lever available when a slot turns out to be
 *    misplaced or a unit starts misbehaving in the field.
 *
 * Everything here fails towards *not* showing an ad, except the remote switch,
 * which fails open — a config that cannot be fetched must not silently cost the
 * app its entire revenue.
 */
object AdGate {

    private const val TAG = "AdGate"

    private val initialised = AtomicBoolean(false)

    /**
     * The last answer consent gave.
     *
     * Held separately because [applyConfig] can be called long afterwards — when
     * the remote switch finally arrives, or on a later launch — and it must not
     * be able to re-enable ads for someone who has since withdrawn consent.
     * Every gate has to be re-checked at the point the decision is made, not
     * assumed from the fact that ads were once permitted.
     */
    @Volatile
    private var consentAllowsAds = false

    private val _bannersAllowed = MutableStateFlow(false)

    /** True only once consent permits ads, the SDK is up, and the switch is on. */
    val bannersAllowed: StateFlow<Boolean> = _bannersAllowed.asStateFlow()

    private val _nativeAllowed = MutableStateFlow(false)

    /**
     * The same three gates, switched separately.
     *
     * Separately because the two slots can fail separately: a native unit that
     * starts serving something inappropriate next to someone's working year has
     * to be killable without taking the banner — and the app's whole income —
     * down with it.
     */
    val nativeAllowed: StateFlow<Boolean> = _nativeAllowed.asStateFlow()

    /**
     * Asks for consent if it is needed, then starts the SDK.
     *
     * Called from the Activity because the consent form is a dialog and needs
     * one. Safe to call on every launch: the SDK does the "have I already asked"
     * bookkeeping, and re-entry is guarded.
     */
    fun start(activity: Activity, onConfigNeeded: () -> Unit = {}) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)

        val parameters = ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // Without this a debug build is treated as whatever region
                    // the tester is actually in, so the EEA form — the one that
                    // most needs testing — can never be seen from elsewhere.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .apply {
                                // Debug geography is ignored unless the device is
                                // registered as a test device. Without this the
                                // EEA form silently never appears on real
                                // hardware outside the EEA, and the path most
                                // worth testing is the one that cannot be.
                                // UMP logs the id to use on first run.
                                BuildConfig.AD_TEST_DEVICE_ID
                                    .takeIf { it.isNotBlank() }
                                    ?.let { addTestDeviceHashedId(it) }
                            }
                            .build(),
                    )
                }
            }
            .build()

        consent.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "consent form: ${formError.errorCode} ${formError.message}")
                    }
                    onConsentResolved(activity, consent, onConfigNeeded)
                }
            },
            { requestError ->
                // A failure here is not consent. Outside the EEA canRequestAds
                // is true anyway; inside it, this correctly leaves ads off.
                Log.w(TAG, "consent update: ${requestError.errorCode} ${requestError.message}")
                onConsentResolved(activity, consent, onConfigNeeded)
            },
        )
    }

    private fun onConsentResolved(
        context: Context,
        consent: ConsentInformation,
        onConfigNeeded: () -> Unit,
    ) {
        consentAllowsAds = consent.canRequestAds()

        // Before the consent branch, deliberately. The same file carries the
        // ad switches and `min_version`, and the second of those has nothing
        // to do with advertising: it is the only way to stop a broken release
        // running. While this sat below the early return, a user who declined
        // consent never fetched the config and so could never be told to
        // update -- the kill switch was missing exactly the people who had
        // already said no to something. Fetching is safe either way, because
        // applyConfig re-checks consent before it turns any slot on.
        onConfigNeeded()

        if (!consentAllowsAds) {
            Log.i(TAG, "consent does not permit ads")
            _bannersAllowed.value = false
            _nativeAllowed.value = false
            return
        }
        // initialize is idempotent, but the callback is not free and the
        // listener list is global, so it is done exactly once per process.
        if (initialised.compareAndSet(false, true)) {
            MobileAds.initialize(context) { Log.i(TAG, "ads sdk ready") }
        }
        applyConfig(AdConfig.current)
    }

    /**
     * Called when the remote switch is fetched, and whenever it changes.
     *
     * Consent is re-checked here rather than taken for granted: this runs after
     * a network round trip, by which time the user may have withdrawn it in
     * settings, and turning ads back on for them would be the one mistake in
     * this file with legal weight.
     */
    fun applyConfig(config: AdConfig.Values) {
        val permitted = consentAllowsAds && initialised.get()
        _bannersAllowed.value = permitted && config.bannersEnabled
        _nativeAllowed.value = permitted && config.nativeEnabled
    }

    /**
     * Whether a consent form exists that the user can reopen.
     *
     * Regulators require a way to change your mind, so settings needs to know
     * whether to offer the option at all rather than showing a control that
     * does nothing outside the EEA.
     */
    fun privacyOptionsRequired(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Reopens the consent form so a decision can be changed. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "privacy form: ${error.errorCode} ${error.message}")
            onConsentResolved(
                activity,
                UserMessagingPlatform.getConsentInformation(activity),
                onConfigNeeded = {},
            )
        }
    }
}
