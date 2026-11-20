package de.radicalfishgames.crosscode.steam

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.handlers.steamapps.GamePlayedInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The app's own Steam connection: QR sign-in, "playing CrossCode" while the game is in front
 * (that is what counts playtime and shows on the friends list), and achievement unlocks.
 *
 * All state changes run on one serial coroutine dispatcher, so nothing here needs locks.
 * The refresh token never leaves the app's private "steam" preferences, which backups exclude.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object SteamBridge {
    const val APP_ID = 368340
    private const val TAG = "CrossAndroidSteam"
    private const val FLUSH_DELAY_MS = 3_000L
    private const val REQUEST_TIMEOUT_MS = 20_000L

    enum class Status { SIGNED_OUT, CONNECTING, WAITING_FOR_QR, ONLINE, OFFLINE }

    data class State(
        val status: Status,
        val accountName: String? = null,
        val qrUrl: String? = null,
        val message: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _state = MutableStateFlow(State(Status.SIGNED_OUT))
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var prefs: SharedPreferences

    private var client: SteamClient? = null
    private var stats: UserStatsHandler? = null
    private var pumpThread: Thread? = null

    @Volatile private var pumping = false
    private var loggedOn = false
    private var wantQr = false
    private var playing = false
    private var reconnectDelayMs = 5_000L
    private var qrJob: Job? = null
    private var flushJob: Job? = null
    private var reconnectJob: Job? = null
    private var schema: Map<String, UserStatsHandler.AchievementSlot>? = null

    private val accountName get() = prefs.getString("account_name", null)
    private val refreshToken get() = prefs.getString("refresh_token", null)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("steam", Context.MODE_PRIVATE)
        if (refreshToken != null) {
            _state.value = State(Status.OFFLINE, accountName)
        }
    }

    val isSignedIn get() = ::prefs.isInitialized && refreshToken != null

    // ---- public API, safe to call from any thread ----

    /** Starts a QR sign-in; the code appears in [state] as [State.qrUrl]. */
    fun startQrLogin() =
        scope.launch {
            wantQr = true
            val c = client
            if (c == null) {
                connect()
            } else if (c.isConnected && !loggedOn) {
                beginQr()
            }
        }

    fun cancelQrLogin() =
        scope.launch {
            if (!wantQr) return@launch
            wantQr = false
            qrJob?.cancel()
            if (refreshToken == null) disconnect(State(Status.SIGNED_OUT))
        }

    fun signOut() =
        scope.launch {
            prefs.edit {
                remove("account_name")
                remove("refresh_token")
            }
            disconnect(State(Status.SIGNED_OUT))
        }

    /** Connects with the saved token if there is one and we are not connected yet. */
    fun ensureConnected() =
        scope.launch {
            if (refreshToken != null && client == null) connect()
        }

    /** The game is in front (true) or not (false). Steam counts playtime while true. */
    fun setPlaying(nowPlaying: Boolean) =
        scope.launch {
            if (playing == nowPlaying) return@launch
            playing = nowPlaying
            if (loggedOn) {
                sendPlaying()
            } else if (nowPlaying && refreshToken != null && client == null) {
                connect()
            }
        }

    /** Queues an achievement by its Steam API name; uploads it as soon as we are online. */
    fun unlock(apiName: String) =
        scope.launch {
            val pending = prefs.getStringSet("pending_unlocks", emptySet())!!
            if (apiName !in pending) {
                prefs.edit { putStringSet("pending_unlocks", pending + apiName) }
            }
            scheduleFlush()
        }

    // ---- connection ----

    private fun connect() {
        if (client != null) return
        _state.value = State(Status.CONNECTING, accountName)

        val config =
            SteamConfiguration.create { builder ->
                builder.withProtocolTypes(ProtocolTypes.WEB_SOCKET)
                builder.withConnectionTimeout(15_000L)
            }
        val steamClient = SteamClient(config)
        val handler = UserStatsHandler()
        steamClient.addHandler(handler)
        val manager = CallbackManager(steamClient)

        manager.subscribe(ConnectedCallback::class.java) { scope.launch { onConnected() } }
        manager.subscribe(DisconnectedCallback::class.java) { cb -> scope.launch { onDisconnected(steamClient, cb.isUserInitiated) } }
        manager.subscribe(LoggedOnCallback::class.java) { cb -> scope.launch { onLoggedOn(cb) } }
        manager.subscribe(LoggedOffCallback::class.java) { cb -> scope.launch { onLoggedOff(cb.result) } }

        client = steamClient
        stats = handler
        pumping = true
        pumpThread =
            Thread({
                while (pumping) {
                    try {
                        manager.runWaitCallbacks(1_000L)
                    } catch (e: Exception) {
                        Log.e(TAG, "Callback pump failed", e)
                    }
                }
            }, "SteamCallbacks").apply {
                isDaemon = true
                start()
            }

        steamClient.connect()
    }

    private fun disconnect(newState: State) {
        loggedOn = false
        qrJob?.cancel()
        flushJob?.cancel()
        reconnectJob?.cancel()
        stats?.cancelAll()
        val c = client
        client = null
        stats = null
        pumping = false
        pumpThread = null
        c?.disconnect()
        _state.value = newState
    }

    private fun onConnected() {
        Log.i(TAG, "Connected to Steam")
        reconnectDelayMs = 5_000L
        val token = refreshToken
        if (token != null) {
            logOn(accountName!!, token)
        } else if (wantQr) {
            beginQr()
        }
    }

    private fun onDisconnected(
        which: SteamClient,
        userInitiated: Boolean,
    ) {
        if (which !== client) return // an old client we already dropped
        Log.i(TAG, "Disconnected from Steam (userInitiated=$userInitiated)")
        loggedOn = false
        stats?.cancelAll()
        client = null
        stats = null
        pumping = false

        if (refreshToken == null) {
            if (_state.value.status != Status.SIGNED_OUT) _state.value = State(Status.SIGNED_OUT, message = "Connection lost")
            wantQr = false
            return
        }
        _state.value = State(Status.OFFLINE, accountName, message = "Reconnecting...")
        val wait = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(120_000L)
        reconnectJob =
            scope.launch {
                delay(wait)
                if (client == null && refreshToken != null) connect()
            }
    }

    private fun logOn(
        account: String,
        token: String,
    ) {
        val user = client?.getHandler(SteamUser::class.java) ?: return
        user.logOn(
            LogOnDetails(
                username = account,
                accessToken = token,
                shouldRememberPassword = true,
                // A loginID of its own stops this session from replacing the PC client's session
                loginID = loginId(),
                machineName = "CrossAndroid (${Build.MODEL})",
                clientOSType = EOSType.AndroidUnknown,
            ),
        )
    }

    private fun loginId(): Int {
        var id = prefs.getInt("login_id", 0)
        if (id == 0) {
            id = Random.nextInt(1, Int.MAX_VALUE)
            prefs.edit { putInt("login_id", id) }
        }
        return id
    }

    private fun onLoggedOn(cb: LoggedOnCallback) {
        if (cb.result == EResult.OK) {
            Log.i(TAG, "Logged on as $accountName")
            loggedOn = true
            _state.value = State(Status.ONLINE, accountName)
            client?.getHandler(SteamFriends::class.java)?.setPersonaState(EPersonaState.Online)
            if (playing) sendPlaying()
            scheduleFlush(0)
            return
        }

        Log.w(TAG, "Logon failed: ${cb.result} / ${cb.extendedResult}")
        when (cb.result) {
            EResult.InvalidPassword, EResult.AccessDenied, EResult.Expired, EResult.Revoked, EResult.InvalidSignature -> {
                // The saved token no longer works; the user has to scan a new QR code
                prefs.edit { remove("refresh_token") }
                disconnect(State(Status.SIGNED_OUT, message = "Steam sign-in expired (${cb.result}), please sign in again"))
            }
            else -> {
                _state.value = State(Status.OFFLINE, accountName, message = "Steam logon failed: ${cb.result}")
                client?.disconnect() // onDisconnected schedules the retry
            }
        }
    }

    private fun onLoggedOff(result: EResult) {
        Log.w(TAG, "Logged off: $result")
        loggedOn = false
        if (result == EResult.LoggedInElsewhere || result == EResult.LogonSessionReplaced) {
            // Another device started a game on this account. Don't fight it; reconnect when the game resumes.
            disconnect(State(Status.OFFLINE, accountName, message = "Steam is in use on another device"))
            playing = false
        }
    }

    // ---- QR sign-in ----

    private fun beginQr() {
        val c = client ?: return
        qrJob?.cancel()
        qrJob =
            scope.launch {
                try {
                    val details =
                        AuthSessionDetails().apply {
                            deviceFriendlyName = "CrossAndroid (${Build.MODEL})"
                            persistentSession = true
                        }
                    val session = c.authentication.beginAuthSessionViaQR(details).await()
                    session.challengeUrlChanged =
                        IChallengeUrlChanged { changed ->
                            changed?.let { s -> scope.launch { if (wantQr) _state.value = State(Status.WAITING_FOR_QR, qrUrl = s.challengeUrl) } }
                        }
                    _state.value = State(Status.WAITING_FOR_QR, qrUrl = session.challengeUrl)

                    val result = session.pollingWaitForResult().await()
                    prefs.edit {
                        putString("account_name", result.accountName)
                        putString("refresh_token", result.refreshToken)
                    }
                    wantQr = false
                    _state.value = State(Status.CONNECTING, result.accountName)
                    logOn(result.accountName, result.refreshToken)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "QR sign-in failed", e)
                    wantQr = false
                    disconnect(State(Status.SIGNED_OUT, message = "Sign-in failed: ${e.message}"))
                }
            }
    }

    // ---- playtime ----

    private fun sendPlaying() {
        val c = client ?: return
        val apps = c.getHandler(SteamApps::class.java) ?: return
        val games =
            if (playing) {
                listOf(
                    GamePlayedInfo(
                        gameId = APP_ID.toLong(),
                        processId = android.os.Process.myPid(),
                        ownerId = c.steamID?.accountID?.toInt() ?: 0,
                        gameBuildId = 0,
                    ),
                )
            } else {
                emptyList()
            }
        apps.notifyGamesPlayed(games, EOSType.AndroidUnknown)
        Log.i(TAG, if (playing) "Now playing CrossCode" else "Stopped playing")
    }

    // ---- achievements ----

    private fun scheduleFlush(delayMs: Long = FLUSH_DELAY_MS) {
        if (!loggedOn) return
        // Restarting the timer batches the burst of unlocks the game replays from the save on load
        flushJob?.cancel()
        flushJob =
            scope.launch {
                delay(delayMs)
                flush()
            }
    }

    private suspend fun flush() {
        val pending = prefs.getStringSet("pending_unlocks", emptySet())!!.toSet()
        if (pending.isEmpty() || !loggedOn) return
        val c = client ?: return
        val handler = stats ?: return
        val steamId = c.steamID?.convertToUInt64() ?: return

        val current = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { handler.getUserStats(APP_ID, steamId).await() }
        if (current == null || current.eresult != EResult.OK.code()) {
            Log.w(TAG, "GetUserStats failed: ${current?.eresult ?: "timeout"}")
            return
        }
        if (schema == null && current.hasSchema() && !current.schema.isEmpty) {
            schema = UserStatsHandler.parseSchema(current.schema.toByteArray())
            Log.i(TAG, "Achievement schema: ${schema!!.size} achievements")
        }
        val slots = schema
        if (slots.isNullOrEmpty()) {
            Log.w(TAG, "No achievement schema in the GetUserStats response")
            return
        }

        val values = current.statsList.associate { it.statId to it.statValue }.toMutableMap()
        val changed = HashMap<Int, Int>()
        val newlyUnlocked = ArrayList<String>()
        for (name in pending) {
            val slot = slots[name]
            if (slot == null) {
                Log.w(TAG, "Unknown achievement $name, dropping it")
                continue
            }
            val mask = 1 shl slot.bit
            val value = values[slot.statId] ?: 0
            if (value and mask == 0) {
                values[slot.statId] = value or mask
                changed[slot.statId] = value or mask
                newlyUnlocked += name
            }
        }

        if (changed.isNotEmpty()) {
            val stored = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { handler.storeUserStats(APP_ID, steamId, current.crcStats, changed).await() }
            if (stored == null || stored.eresult != EResult.OK.code()) {
                Log.w(TAG, "StoreUserStats failed: ${stored?.eresult ?: "timeout"}; keeping ${newlyUnlocked.size} unlocks queued")
                return
            }
            Log.i(TAG, "Unlocked on Steam: $newlyUnlocked")
        }

        // Only remove what we handled; unlocks queued during the upload stay for the next flush
        val now = prefs.getStringSet("pending_unlocks", emptySet())!!
        prefs.edit { putStringSet("pending_unlocks", now - pending) }
        if ((now - pending).isNotEmpty()) scheduleFlush()
    }
}
