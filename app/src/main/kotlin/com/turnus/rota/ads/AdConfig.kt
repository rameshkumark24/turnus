package com.turnus.rota.ads

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The remote kill switch for ad slots.
 *
 * A static JSON file, fetched over plain HTTPS with the JDK's own client and
 * parsed with Android's own JSON class. No networking or serialisation library
 * is added for this: it is one GET of a few hundred bytes, and the app is
 * otherwise entirely offline — a dependency here would be a supply-chain
 * surface and an update obligation bought for nothing.
 *
 * It is not a backend. The file is a static asset; nothing is sent to it, and
 * the app works identically when it cannot be reached.
 *
 * **Fails open.** Every failure path — no network, a timeout, malformed JSON, a
 * 404 — leaves the slots enabled. A kill switch that turns everything off when
 * it cannot phone home would take the app's entire revenue down with any
 * hosting hiccup, which is a far more likely event than needing to kill a slot.
 */
object AdConfig {

    private const val TAG = "AdConfig"
    private const val PREFS = "ad_config"
    private const val KEY_BANNERS = "banners_enabled"
    private const val KEY_NATIVE = "native_enabled"

    /**
     * Served from the project's own repository, so there is nothing to run and
     * nothing to pay for.
     */
    private const val URL =
        "https://raw.githubusercontent.com/rameshkumark24/turnus/main/config/ads.json"

    private const val TIMEOUT_MS = 8_000

    data class Values(
        val bannersEnabled: Boolean = true,
        val nativeEnabled: Boolean = true,
    )

    /** The last known configuration. Defaults to everything on. */
    @Volatile
    var current: Values = Values()
        private set

    /**
     * Loads the cached value, then refreshes from the network.
     *
     * The cache is read first so a launch with no connection still honours a
     * kill decision made on a previous run — a switch that only works while
     * online is not much of a switch.
     */
    suspend fun refresh(context: Context): Values = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        current = Values(
            bannersEnabled = prefs.getBoolean(KEY_BANNERS, true),
            nativeEnabled = prefs.getBoolean(KEY_NATIVE, true),
        )

        val fetched = fetch()
        if (fetched != null) {
            Log.i(TAG, "config: banners=${fetched.bannersEnabled} native=${fetched.nativeEnabled}")
            current = fetched
            prefs.edit {
                putBoolean(KEY_BANNERS, fetched.bannersEnabled)
                putBoolean(KEY_NATIVE, fetched.nativeEnabled)
            }
        }
        current
    }

    private fun fetch(): Values? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                // Nothing about the device or the user is sent. There is no
                // identifier to attach and no reason to attach one.
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "config responded ${connection.responseCode}; keeping current")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            Values(
                bannersEnabled = json.optBoolean(KEY_BANNERS, true),
                nativeEnabled = json.optBoolean(KEY_NATIVE, true),
            )
        } catch (failure: Exception) {
            // Deliberately broad. Every one of these — UnknownHost, SocketTimeout,
            // JSONException, a captive portal returning HTML — means the same
            // thing here: no new instruction, so carry on as before.
            Log.i(TAG, "could not read ad config, failing open: ${failure.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
