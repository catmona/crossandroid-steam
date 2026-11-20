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
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientGetUserStatsResponse
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSession
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.handlers.steamapps.GamePlayedInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStatesCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.PlayingSessionStateCallback
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

/**
 * The app's own Steam connection: sign-in (QR code, or account name and password with Steam Guard),
 * "playing CrossCode" while the game is in front (that is what counts playtime and shows on the
 * friends list), and achievement unlocks.
 *
 * Every unlock is checked: after storing, the stats are read back from Steam and an unlock only
 * leaves the queue once its bit is set there. [health] reports what has been confirmed this way.
 *
 * All state changes run on one serial coroutine dispatcher; stats requests also hold [statsMutex],
 * because coroutines on that dispatcher still interleave at suspension points.
 * The refresh token never leaves the app's private "steam" preferences, which backups exclude.
 * The password is only passed to Steam's auth call and never kept.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object SteamBridge {
    const val APP_ID = 368340
    private const val TAG = "CrossAndroidSteam"

    // EClientPersonaStateFlag: Status 1 | PlayerName 2 | Presence 16 | GameExtraInfo 256 | GameDataBlob 512
    private const val PERSONA_FLAGS_WITH_GAME = 1 or 2 or 16 or 256 or 512
    private const val FLUSH_DELAY_MS = 3_000L
    private const val LIVE_FLUSH_DELAY_MS = 300L
    private const val RETRY_DELAY_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS = 20_000L

    enum class Status { SIGNED_OUT, CONNECTING, WAITING_FOR_QR, SIGNING_IN, NEEDS_CODE, WAITING_FOR_APPROVAL, ONLINE, OFFLINE }

    /** What Steam Guard asks for during a password sign-in. */
    enum class CodeKind { AUTHENTICATOR, EMAIL }

    data class CodePrompt(
        val kind: CodeKind,
        val email: String? = null,
        val previousWasWrong: Boolean = false,
    )

    data class State(
        val status: Status,
        val accountName: String? = null,
        val qrUrl: String? = null,
        val message: String? = null,
        val codePrompt: CodePrompt? = null,
    )

    /** What has been confirmed by Steam itself, for the status panel. Times are epoch millis. */
    data class Health(
        val playingSentAt: Long? = null,
        val playingConfirmedAt: Long? = null,
        val playingNote: String? = null,
        /** Achievements set on the account, out of [schemaSize]; null until stats were read. */
        val unlockedOnSteam: Int? = null,
        val schemaSize: Int? = null,
        /** Achievements the game reported (live or from the save) and how many of them Steam has. */
        val reported: Int = 0,
        val verified: Int = 0,
        val pending: Int = 0,
        val lastCheckAt: Long? = null,
        val lastCheckNote: String? = null,
    )

    /** A corner pop-up for the game screen. */
    sealed class Popup {
        /** [onSteam]: true = confirmed on Steam, false = Steam didn't take it yet, null = not online. */
        data class Unlocked(
            val apiName: String,
            val title: String,
            val description: String?,
            val iconUrl: String?,
            val onSteam: Boolean?,
            val signedIn: Boolean,
        ) : Popup()

        /** Earlier unlocks from the save that just reached Steam, shown as one line. */
        data class CaughtUp(
            val count: Int,
        ) : Popup()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _state = MutableStateFlow(State(Status.SIGNED_OUT))
    val state: StateFlow<State> = _state.asStateFlow()
    private val _health = MutableStateFlow(Health())
    val health: StateFlow<Health> = _health.asStateFlow()
    private val _popups = MutableSharedFlow<Popup>(extraBufferCapacity = 16)
    val popups: SharedFlow<Popup> = _popups.asSharedFlow()

    private lateinit var prefs: SharedPreferences
    private val statsMutex = Mutex()

    private var client: SteamClient? = null
    private var stats: UserStatsHandler? = null
    private var pumpThread: Thread? = null

    @Volatile private var pumping = false
    private var loggedOn = false
    private var wantQr = false
    private var pendingCredentials: Pair<String, String>? = null
    private var playing = false
    private var reconnectDelayMs = 5_000L
    private var authJob: Job? = null
    private var flushDelayJob: Job? = null
    private var reconnectJob: Job? = null
    private var schema: Map<String, UserStatsHandler.AchievementSlot>? = null

    @Volatile private var codeFuture: CompletableFuture<String>? = null

    /** Unlocks earned right now (not replayed from the save); they get their own pop-up. */
    private val liveUnlocks = HashSet<String>()
    private val gameNames = HashMap<String, String>()

    private val accountName get() = prefs.getString("account_name", null)
    private val refreshToken get() = prefs.getString("refresh_token", null)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("steam", Context.MODE_PRIVATE)
        if (refreshToken != null) {
            _state.value = State(Status.OFFLINE, accountName)
        }
        _health.update { it.copy(reported = reportedSet().size, pending = pendingSet().size) }
    }

    val isSignedIn get() = ::prefs.isInitialized && refreshToken != null

    // ---- public API, safe to call from any thread ----

    /** Starts a QR sign-in; the code appears in [state] as [State.qrUrl]. */
    fun startQrLogin() =
        scope.launch {
            wantQr = true
            pendingCredentials = null
            val c = client
            if (c == null) {
                connect()
            } else if (c.isConnected && !loggedOn) {
                beginQr()
            }
        }

    /**
     * Signs in with account name and password. Steam Guard then asks for approval in the Steam app,
     * or for a code, which shows up in [state] as [State.codePrompt]; answer it with [submitCode].
     */
    fun startCredentialsLogin(
        username: String,
        password: String,
    ) = scope.launch {
        wantQr = false
        pendingCredentials = username.trim() to password
        _state.value = State(Status.SIGNING_IN, username.trim())
        val c = client
        if (c == null) {
            connect()
        } else if (c.isConnected && !loggedOn) {
            beginCredentials()
        }
    }

    /** Answers a Steam Guard [State.codePrompt]. */
    fun submitCode(code: String) {
        codeFuture?.complete(code.trim().uppercase())
    }

    /** Abandons a QR or password sign-in that hasn't finished. */
    fun cancelLogin() =
        scope.launch {
            if (!wantQr && pendingCredentials == null && authJob?.isActive != true) return@launch
            wantQr = false
            pendingCredentials = null
            authJob?.cancel()
            codeFuture?.cancel(true)
            codeFuture = null
            if (refreshToken == null) disconnect(State(Status.SIGNED_OUT))
        }

    fun signOut() =
        scope.launch {
            prefs.edit {
                remove("account_name")
                remove("refresh_token")
            }
            disconnect(State(Status.SIGNED_OUT))
            _health.value = Health(reported = reportedSet().size, pending = pendingSet().size)
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

    /**
     * Queues an achievement by its Steam API name; uploads and verifies it as soon as we are online.
     * [live] is true when it was earned just now (it gets a pop-up), false for replays from the save.
     * [gameName] is the game's own title for it, used when the Steam schema isn't loaded.
     */
    fun unlock(
        apiName: String,
        live: Boolean = false,
        gameName: String? = null,
    ) = scope.launch {
        if (gameName != null) gameNames[apiName] = gameName
        val reported = reportedSet()
        if (apiName !in reported) prefs.edit { putStringSet("reported_unlocks", reported + apiName) }

        val slot = schema?.get(apiName)
        val alreadyOnSteam = slot != null && lastValues?.let { slot.isSetIn(it) } == true
        if (alreadyOnSteam && !live) {
            // The boot replay of something Steam already has
            Log.i(TAG, "Already on Steam: $apiName")
            return@launch
        }

        val pending = pendingSet()
        if (apiName !in pending) prefs.edit { putStringSet("pending_unlocks", pending + apiName) }
        _health.update { it.copy(reported = reportedSet().size, pending = pendingSet().size) }

        if (live) {
            liveUnlocks += apiName
            if (!loggedOn) {
                // No Steam answer to wait for; show it now and say it will sync later
                emitUnlocked(apiName, onSteam = null)
                liveUnlocks -= apiName
            }
            scheduleFlush(LIVE_FLUSH_DELAY_MS)
        } else {
            scheduleFlush()
        }
    }

    /**
     * Compares what the game reported with what Steam has: re-sends anything missing, re-reads the
     * stats, and re-announces "playing" while the game is in front. The result lands in [health].
     */
    fun checkNow() =
        scope.launch {
            if (!loggedOn) {
                _health.update { it.copy(lastCheckAt = System.currentTimeMillis(), lastCheckNote = "Not online with Steam yet") }
                if (refreshToken != null && client == null) connect()
                return@launch
            }
            if (playing) sendPlaying() else requestOwnPersona()
            statsMutex.withLock {
                val current = readStats()
                if (current == null) {
                    _health.update { it.copy(lastCheckAt = System.currentTimeMillis(), lastCheckNote = "Steam didn't answer the stats request") }
                    return@withLock
                }
                val values = current.valueMap()
                val slots = schema.orEmpty()
                val missing = reportedSet().filter { name -> slots[name]?.isSetIn(values) == false }
                if (missing.isNotEmpty()) {
                    prefs.edit { putStringSet("pending_unlocks", pendingSet() + missing) }
                }
                updateHealth(values)
                val note =
                    when {
                        slots.isEmpty() -> "Steam sent no achievement list"
                        missing.isEmpty() -> "All ${_health.value.verified} achievements from your save are on Steam"
                        else -> "${missing.size} not on Steam yet, sending them now"
                    }
                Log.i(TAG, "Check: $note")
                _health.update { it.copy(lastCheckAt = System.currentTimeMillis(), lastCheckNote = note) }
                if (missing.isNotEmpty()) flush()
            }
        }

    // ---- connection ----

    private fun connect() {
        if (client != null) return
        if (_state.value.status != Status.SIGNING_IN) _state.value = State(Status.CONNECTING, accountName)

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
        manager.subscribe(PlayingSessionStateCallback::class.java) { cb ->
            scope.launch { onPlayingSessionState(cb.playingAppID, cb.isPlayingBlocked) }
        }
        manager.subscribe(PersonaStatesCallback::class.java) { cb ->
            if (cb.friendID == steamClient.steamID) scope.launch { onOwnPersona(cb.gameAppID) }
        }

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
        authJob?.cancel()
        flushDelayJob?.cancel()
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
        when {
            pendingCredentials != null -> beginCredentials()
            token != null -> logOn(accountName!!, token)
            wantQr -> beginQr()
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
            pendingCredentials = null
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
            // Reads the stats once even with nothing queued, so the status panel has real numbers
            scheduleFlush(0)
            return
        }

        Log.w(TAG, "Logon failed: ${cb.result} / ${cb.extendedResult}")
        when (cb.result) {
            EResult.InvalidPassword, EResult.AccessDenied, EResult.Expired, EResult.Revoked, EResult.InvalidSignature -> {
                // The saved token no longer works; the user has to sign in again
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

    // ---- sign-in ----

    private fun authDetails() =
        AuthSessionDetails().apply {
            deviceFriendlyName = "CrossAndroid (${Build.MODEL})"
            persistentSession = true
        }

    private fun beginQr() {
        val c = client ?: return
        authJob?.cancel()
        authJob =
            scope.launch {
                runAuth {
                    val session = c.authentication.beginAuthSessionViaQR(authDetails()).await()
                    session.challengeUrlChanged =
                        IChallengeUrlChanged { changed ->
                            changed?.let { s -> scope.launch { if (wantQr) _state.value = State(Status.WAITING_FOR_QR, qrUrl = s.challengeUrl) } }
                        }
                    _state.value = State(Status.WAITING_FOR_QR, qrUrl = session.challengeUrl)
                    session
                }
            }
    }

    private fun beginCredentials() {
        val c = client ?: return
        val (username, password) = pendingCredentials ?: return
        pendingCredentials = null // the password lives on only inside Steam's auth call
        authJob?.cancel()
        authJob =
            scope.launch {
                runAuth {
                    val details =
                        authDetails().apply {
                            this.username = username
                            this.password = password
                            authenticator = guardPrompter(username)
                        }
                    _state.value = State(Status.SIGNING_IN, username)
                    c.authentication.beginAuthSessionViaCredentials(details).await()
                }
            }
    }

    /** Asks the UI for Steam Guard input. JavaSteam calls these from its own threads. */
    private fun guardPrompter(username: String) =
        object : IAuthenticator {
            override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
                askCode(username, CodePrompt(CodeKind.AUTHENTICATOR, previousWasWrong = previousCodeWasIncorrect))

            override fun getEmailCode(
                email: String?,
                previousCodeWasIncorrect: Boolean,
            ): CompletableFuture<String> = askCode(username, CodePrompt(CodeKind.EMAIL, email, previousCodeWasIncorrect))

            override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> {
                // Approving in the Steam app is the easiest path; the session then polls until approved
                _state.value = State(Status.WAITING_FOR_APPROVAL, username)
                return CompletableFuture.completedFuture(true)
            }
        }

    private fun askCode(
        username: String,
        prompt: CodePrompt,
    ): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        codeFuture = future
        _state.value = State(Status.NEEDS_CODE, username, codePrompt = prompt)
        return future.whenComplete { _, _ -> if (codeFuture === future) codeFuture = null }
    }

    /** Runs one sign-in: [start] begins the session, then this waits for Steam's answer and logs on. */
    private suspend fun runAuth(start: suspend () -> AuthSession) {
        try {
            val result: AuthPollResult = start().pollingWaitForResult().await()
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
            Log.e(TAG, "Sign-in failed: ${e.javaClass.simpleName}: ${e.message}")
            wantQr = false
            val cause = (e as? AuthenticationException) ?: (e.cause as? AuthenticationException)
            val text =
                when (cause?.result) {
                    EResult.InvalidPassword -> "Wrong account name or password"
                    EResult.RateLimitExceeded, EResult.AccountLoginDeniedThrottle -> "Too many attempts, wait a while and try again"
                    EResult.Expired, EResult.Timeout -> "The sign-in timed out, please try again"
                    else -> "Sign-in failed: ${cause?.result ?: e.message}"
                }
            disconnect(State(Status.SIGNED_OUT, message = text))
        } finally {
            codeFuture = null
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
        if (playing) {
            _health.update { it.copy(playingSentAt = System.currentTimeMillis(), playingConfirmedAt = null, playingNote = "Waiting for Steam") }
        } else {
            _health.update { it.copy(playingNote = "Not playing (game not in front)") }
        }
        requestOwnPersona()
    }

    private fun requestOwnPersona() {
        val c = client ?: return
        val id = c.steamID ?: return
        // Steam answers with a PersonaStatesCallback carrying the game friends see us in
        scope.launch {
            delay(2_000L)
            // Ask for the game fields explicitly: with the default (0) Steam leaves them out and the
            // answer reads as "in game 0" even while friends see us playing
            if (client === c) c.getHandler(SteamFriends::class.java)?.requestFriendInfo(id, PERSONA_FLAGS_WITH_GAME)
        }
    }

    private fun onPlayingSessionState(
        appId: Int,
        blocked: Boolean,
    ) {
        Log.i(TAG, "Playing session: app $appId, blocked=$blocked")
        when {
            blocked -> _health.update { it.copy(playingConfirmedAt = null, playingNote = "Blocked: this account is playing on another device") }
            appId == APP_ID && playing -> confirmPlaying("Steam session")
            else -> Unit
        }
    }

    private fun onOwnPersona(gameAppId: Int) {
        Log.i(TAG, "Own persona: in game $gameAppId")
        if (gameAppId == APP_ID && playing) confirmPlaying("Friends see Playing CrossCode")
    }

    private fun confirmPlaying(how: String) {
        val now = System.currentTimeMillis()
        Log.i(TAG, "Playing confirmed: $how")
        _health.update { it.copy(playingConfirmedAt = now, playingNote = how) }
    }

    // ---- achievements ----

    private var lastValues: Map<Int, Int>? = null

    private fun pendingSet(): Set<String> = prefs.getStringSet("pending_unlocks", emptySet())!!.toSet()

    private fun reportedSet(): Set<String> = prefs.getStringSet("reported_unlocks", emptySet())!!.toSet()

    private fun CMsgClientGetUserStatsResponse.valueMap(): Map<Int, Int> = statsList.associate { it.statId to it.statValue }

    private fun scheduleFlush(delayMs: Long = FLUSH_DELAY_MS) {
        if (!loggedOn) return
        // Restarting the timer batches the burst of unlocks the game replays from the save on load.
        // Only the wait is cancelled; a flush already talking to Steam runs to the end.
        flushDelayJob?.cancel()
        flushDelayJob =
            scope.launch {
                delay(delayMs)
                scope.launch { statsMutex.withLock { flush() } }
            }
    }

    /** Reads the stats (and the schema, the first time). Call with [statsMutex] held. */
    private suspend fun readStats(): CMsgClientGetUserStatsResponse? {
        val c = client ?: return null
        val handler = stats ?: return null
        val steamId = c.steamID?.convertToUInt64() ?: return null
        val current =
            try {
                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { handler.getUserStats(APP_ID, steamId).await() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                null // the request was dropped by a disconnect
            }
        if (current == null || current.eresult != EResult.OK.code()) {
            Log.w(TAG, "GetUserStats failed: ${current?.eresult ?: "timeout"}")
            return null
        }
        if (schema == null && current.hasSchema() && !current.schema.isEmpty) {
            schema = UserStatsHandler.parseSchema(current.schema.toByteArray())
            Log.i(TAG, "Achievement schema: ${schema!!.size} achievements")
        }
        lastValues = current.valueMap()
        return current
    }

    private fun updateHealth(values: Map<Int, Int>) {
        val slots = schema.orEmpty()
        val reported = reportedSet()
        _health.update {
            it.copy(
                unlockedOnSteam = slots.values.count { s -> s.isSetIn(values) },
                schemaSize = slots.size.takeIf { n -> n > 0 },
                reported = reported.size,
                verified = reported.count { name -> slots[name]?.isSetIn(values) == true },
                pending = pendingSet().size,
            )
        }
    }

    /** Uploads queued unlocks, then reads the stats back to confirm them. Call with [statsMutex] held. */
    private suspend fun flush() {
        if (!loggedOn) return
        val pending = pendingSet()
        val current = readStats() ?: return retryLater()
        val slots = schema
        if (slots.isNullOrEmpty()) {
            Log.w(TAG, "No achievement schema in the GetUserStats response")
            return
        }
        val values = current.valueMap().toMutableMap()
        if (pending.isEmpty()) {
            updateHealth(values)
            return
        }

        val changed = HashMap<Int, Int>()
        val toSet = ArrayList<String>()
        val handled = HashSet<String>()
        for (name in pending) {
            val slot = slots[name]
            if (slot == null) {
                Log.w(TAG, "Unknown achievement $name, dropping it")
                handled += name
                continue
            }
            if (!slot.isSetIn(values)) {
                val v = (values[slot.statId] ?: 0) or (1 shl slot.bit)
                values[slot.statId] = v
                changed[slot.statId] = v
                toSet += name
            } else {
                handled += name // already on Steam
            }
        }

        var confirmed = emptyList<String>()
        var notTaken = emptyList<String>()
        var after: Map<Int, Int> = values
        if (changed.isNotEmpty()) {
            val handler = stats ?: return
            val steamId = client?.steamID?.convertToUInt64() ?: return
            val stored =
                try {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) { handler.storeUserStats(APP_ID, steamId, current.crcStats, changed).await() }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    null
                }
            if (stored == null || stored.eresult != EResult.OK.code()) {
                Log.w(TAG, "StoreUserStats failed: ${stored?.eresult ?: "timeout"}; keeping ${toSet.size} unlocks queued")
                announceLive(toSet, onSteam = false)
                return retryLater()
            }

            // Don't trust the store's OK alone: read the stats back and look at the bits
            val reread = readStats() ?: return retryLater()
            after = reread.valueMap()
            confirmed = toSet.filter { slots.getValue(it).isSetIn(after) }
            notTaken = toSet - confirmed.toSet()
            if (confirmed.isNotEmpty()) Log.i(TAG, "Unlocked on Steam: $confirmed (verified by reading the stats back)")
            if (notTaken.isNotEmpty()) Log.w(TAG, "Steam accepted the store but these bits are still unset: $notTaken")
            handled += confirmed
        }

        prefs.edit { putStringSet("pending_unlocks", pendingSet() - handled) }
        updateHealth(after)

        // Pop-ups: one each for unlocks earned just now, one summary line for catch-up from the save
        announceLive(handled.filter { it in liveUnlocks }, onSteam = true)
        announceLive(notTaken, onSteam = false)
        val caughtUp = confirmed.count { it !in liveUnlocks && it !in notTaken }
        if (caughtUp > 0) _popups.tryEmit(Popup.CaughtUp(caughtUp))
        liveUnlocks.removeAll(handled)

        if (notTaken.isNotEmpty()) retryLater() else if (pendingSet().isNotEmpty()) scheduleFlush()
    }

    private fun retryLater() = scheduleFlush(RETRY_DELAY_MS)

    private fun announceLive(
        names: Collection<String>,
        onSteam: Boolean,
    ) {
        for (name in names) {
            if (name !in liveUnlocks) continue
            emitUnlocked(name, onSteam)
            if (!onSteam) liveUnlocks -= name // one pop-up per unlock; the retry stays silent
        }
    }

    /**
     * Debug builds only: shows the pop-up for [apiName] as if just unlocked, without telling Steam
     * anything. Only for achievements Steam already has, so nothing is faked on the profile.
     */
    fun previewPopup(apiName: String): String {
        val slot = schema?.get(apiName) ?: return "Unknown achievement $apiName (schema not read yet?)"
        val values = lastValues ?: return "Stats not read yet"
        if (!slot.isSetIn(values)) return "$apiName is not unlocked on Steam; no preview for it"
        emitUnlocked(apiName, onSteam = true)
        return "Previewing $apiName"
    }

    /** Debug builds only: the API names Steam has unlocked, for [previewPopup]. */
    fun unlockedOnSteam(): List<String> {
        val values = lastValues ?: return emptyList()
        return schema.orEmpty().filterValues { it.isSetIn(values) }.keys.sorted()
    }

    private fun emitUnlocked(
        apiName: String,
        onSteam: Boolean?,
    ) {
        val slot = schema?.get(apiName)
        _popups.tryEmit(
            Popup.Unlocked(
                apiName = apiName,
                title = slot?.displayName ?: gameNames[apiName] ?: apiName,
                description = slot?.description,
                iconUrl = slot?.iconUrl(APP_ID),
                onSteam = onSteam,
                signedIn = isSignedIn,
            ),
        )
    }
}
