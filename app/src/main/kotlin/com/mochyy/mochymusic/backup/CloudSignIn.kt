// CloudSignIn.kt

package com.mochyy.mochymusic.backup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import okhttp3.FormBody
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

import android.content.Context
import android.net.Uri
import com.mochyy.mochymusic.utils.Pkce
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** where a browser sign-in to dropbox or onedrive is, for the backup page */
sealed interface CloudLogin {
    data object Idle : CloudLogin
    data class InBrowser(val kind: CloudKind) : CloudLogin
    data class Finishing(val kind: CloudKind) : CloudLogin
    data class Failed(val problem: CloudProblem) : CloudLogin
}

/** signs in to dropbox or onedrive on their own page in the browser, then the login activity hands the answer back here */
@Singleton
class CloudSignIn @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accounts: CloudAccountStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingFile = File(context.noBackupFilesDir, PendingName)
    private val gdriveCredsFile = File(context.noBackupFilesDir, "gdrive_oauth_client.json")
    @Volatile private var loopbackServer: ServerSocket? = null

    private val _login = MutableStateFlow<CloudLogin>(CloudLogin.Idle)
    val login: StateFlow<CloudLogin> = _login.asStateFlow()

    fun isConfigured(kind: CloudKind): Boolean = OAuthApp.of(kind)?.isConfigured == true

    /** the page to send the user to; the check value and verifier go to disk first, since android may close mochy while the browser is in front */
    suspend fun loginUri(kind: CloudKind): Uri {
        val app = requireNotNull(OAuthApp.of(kind)) { "$kind doesn't sign in through the browser" }
        val state = Pkce.randomCode(24)
        val verifier = Pkce.randomCode(64)
        withContext(Dispatchers.IO) {
            pendingFile.writeText(
                JSONObject()
                    .put("kind", kind.name)
                    .put("state", state)
                    .put("verifier", verifier)
                    .put("createdAt", System.currentTimeMillis())
                    .toString(),
            )
        }
        _login.value = CloudLogin.InBrowser(kind)
        return app.authorizeUri(state, Pkce.challengeOf(verifier))
    }

    /** for the login activity, which closes straight away while this carries on */
    fun handleRedirect(redirect: Uri) {
        if (redirect.host == "gdrive-done") return
        if (redirect.scheme?.startsWith("com.googleusercontent.apps.") == true) {
            _login.value = CloudLogin.Finishing(CloudKind.GoogleDrive)
        }
        scope.launch { finish(redirect) }
    }

    /** back from the browser without an answer, the page goes back to its sign-in rows */
    fun cancelWaiting() {
        if (_login.value is CloudLogin.InBrowser) {
            _login.value = CloudLogin.Idle
            scope.launch { runCatching { pendingFile.delete() } }
        }
    }

    fun dismissProblem() {
        if (_login.value is CloudLogin.Failed) _login.value = CloudLogin.Idle
    }

    private suspend fun finish(redirect: Uri) {
        val pending = runCatching { JSONObject(pendingFile.readText()) }.getOrNull()
        val kindName = pending?.optString("kind").orEmpty()
        val verifier = pending?.optString("verifier").orEmpty()
        val expectedState = pending?.optString("state").orEmpty()
        val createdAt = pending?.optLong("createdAt", 0L) ?: 0L
        val expired = createdAt > 0L && System.currentTimeMillis() - createdAt > OAuthStateTtlMs
        val givenState = redirect.getQueryParameter("state")
        val stateMatches = expectedState.isNotEmpty() && givenState != null &&
            MessageDigest.isEqual(expectedState.toByteArray(Charsets.UTF_8), givenState.toByteArray(Charsets.UTF_8))
        if (kindName == CloudKind.GoogleDrive.name) {
            if (verifier.isEmpty() || expired || !stateMatches) {
                if (expired) pendingFile.delete()
                _login.value = CloudLogin.Failed(CloudProblem.Expired)
                return
            }
            pendingFile.delete()
            val code = redirect.getQueryParameter("code")
            if (code.isNullOrEmpty()) {
                _login.value = CloudLogin.Failed(
                    if (redirect.getQueryParameter("error") == "access_denied") CloudProblem.Denied else CloudProblem.Busy
                )
                return
            }
            val clientId = pending?.optString("clientId").orEmpty()
            val redirectUri = pending?.optString("redirectUri").orEmpty()
            if (clientId.isEmpty() || redirectUri.isEmpty()) {
                _login.value = CloudLogin.Failed(CloudProblem.Expired)
                return
            }
            _login.value = CloudLogin.Finishing(CloudKind.GoogleDrive)
            try {
                val form = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .add("code_verifier", verifier)
                    .build()
                val reply = CloudHttp.text(Request.Builder().url(GoogleDriveCloud.TokenUrl).post(form).build())
                if (reply.code == 400 || reply.code == 401) throw CloudException(CloudProblem.WrongLogin)
                if (!reply.ok) CloudHttp.fail(reply.code)
                val json = JSONObject(reply.body)
                val access = json.optString("access_token").ifEmpty { throw CloudException(CloudProblem.Busy) }
                val refresh = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in")
                val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L
                val name = GoogleDriveCloud.accountName(access)
                accounts.current()?.let { old ->
                    if (old.kind == CloudKind.GoogleDrive) GoogleDriveCloud.revoke(old.accessToken)
                    else OAuthApp.of(old.kind)?.revoke(old.accessToken)
                }
                accounts.save(
                    CloudAccount(
                        kind = CloudKind.GoogleDrive,
                        name = name,
                        accessToken = access,
                        refreshToken = refresh,
                        expiresAt = expiresAt,
                        username = clientId,
                        password = "",
                    )
                )
                _login.value = CloudLogin.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(Tag).w(e, "Google Drive sign-in didn't finish")
                _login.value = CloudLogin.Failed((e as? CloudException)?.problem ?: if (e is IOException) CloudProblem.Offline else CloudProblem.Busy)
            }
            return
        }
        val app = OAuthApp.entries.find { it.kind.name == kindName }
        // a link that doesn't belong to the login we started is ignored without aborting an active sign-in
        if (app == null || verifier.isEmpty() || expired || !stateMatches) {
            if (expired) pendingFile.delete()
            _login.value = CloudLogin.Failed(CloudProblem.Expired)
            return
        }
        pendingFile.delete()
        val code = redirect.getQueryParameter("code")
        if (code == null) {
            _login.value = CloudLogin.Failed(if (redirect.getQueryParameter("error") == "access_denied") CloudProblem.Denied else CloudProblem.Busy)
            return
        }

        _login.value = CloudLogin.Finishing(app.kind)
        try {
            val tokens = app.exchange(code, verifier)
            val name = app.accountName(tokens.access)
            // the login being replaced is told to forget its token, so it doesn't linger on dropbox's side
            accounts.current()?.let { old -> OAuthApp.of(old.kind)?.revoke(old.accessToken) }
            accounts.save(
                CloudAccount(
                    kind = app.kind,
                    name = name,
                    accessToken = tokens.access,
                    refreshToken = tokens.refresh.orEmpty(),
                    expiresAt = tokens.expiresAt,
                ),
            )
            _login.value = CloudLogin.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(Tag).w(e, "${app.kind} sign-in didn't finish")
            _login.value = CloudLogin.Failed((e as? CloudException)?.problem ?: if (e is IOException) CloudProblem.Offline else CloudProblem.Busy)
        }
    }


    fun hasBuiltInGoogleDriveCredentials(): Boolean =
        com.mochyy.mochymusic.BuildConfig.GDRIVE_CLIENT_ID.isNotBlank()

    suspend fun startBuiltInGoogleDriveSignIn(): Uri =
        startGoogleDriveCustomUriSignIn(com.mochyy.mochymusic.BuildConfig.GDRIVE_CLIENT_ID)

    suspend fun startGoogleDriveCustomUriSignIn(clientIdRaw: String): Uri {
        val clientId = clientIdRaw.trim()
        require(clientId.endsWith(".apps.googleusercontent.com")) { "Invalid Google OAuth Client ID" }
        val prefix = clientId.removeSuffix(".apps.googleusercontent.com")
        val redirectUri = "com.googleusercontent.apps.$prefix:/oauth2redirect"
        val state = Pkce.randomCode(24)
        val verifier = Pkce.randomCode(64)
        withContext(Dispatchers.IO) {
            runCatching { loopbackServer?.close() }
            pendingFile.writeText(
                JSONObject()
                    .put("kind", CloudKind.GoogleDrive.name)
                    .put("state", state)
                    .put("verifier", verifier)
                    .put("clientId", clientId)
                    .put("redirectUri", redirectUri)
                    .toString(),
            )
        }
        _login.value = CloudLogin.InBrowser(CloudKind.GoogleDrive)
        return Uri.parse(GoogleDriveCloud.AuthorizeUrl).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", GoogleDriveCloud.Scope)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent select_account")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    fun savedGoogleDriveCredentials(): Pair<String, String> = runCatching {
        if (!gdriveCredsFile.exists()) return "" to ""
        val j = JSONObject(gdriveCredsFile.readText())
        j.optString("clientId") to j.optString("clientSecret")
    }.getOrDefault("" to "")

    suspend fun startGoogleDriveSignIn(clientIdRaw: String, clientSecretRaw: String): Uri {
        val clientId = clientIdRaw.trim()
        val clientSecret = clientSecretRaw.trim()
        require(clientId.isNotEmpty()) { "Google OAuth Client ID is required" }
        withContext(Dispatchers.IO) {
            gdriveCredsFile.writeText(
                JSONObject().put("clientId", clientId).put("clientSecret", clientSecret).toString()
            )
            runCatching { loopbackServer?.close() }
        }
        val state = Pkce.randomCode(24)
        val verifier = Pkce.randomCode(64)
        val server = withContext(Dispatchers.IO) {
            ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")).apply { reuseAddress = true; soTimeout = 300_000 }
        }
        loopbackServer = server
        val redirectUri = "http://127.0.0.1:${server.localPort}/callback"
        _login.value = CloudLogin.InBrowser(CloudKind.GoogleDrive)

        scope.launch {
            awaitGoogleDriveCallback(server, clientId, clientSecret, redirectUri, state, verifier)
        }

        return Uri.parse(GoogleDriveCloud.AuthorizeUrl).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", GoogleDriveCloud.Scope)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent select_account")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    private suspend fun awaitGoogleDriveCallback(
        server: ServerSocket,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        expectedState: String,
        verifier: String,
    ) {
        try {
            val callbackDeferred = CompletableDeferred<Uri>()
            val acceptJob = scope.launch {
                try {
                    while (!server.isClosed && !callbackDeferred.isCompleted) {
                        val socket = server.accept()
                        launch {
                            try {
                                socket.use { s ->
                                    s.soTimeout = 5_000
                                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                                    val requestLine = runCatching { reader.readLine() }.getOrNull().orEmpty()
                                    if (requestLine.isBlank()) return@use
                                    while (true) {
                                        val line = runCatching { reader.readLine() }.getOrNull()
                                        if (line.isNullOrEmpty()) break
                                    }
                                    val path = requestLine.split(" ").getOrNull(1).orEmpty()
                                    val out = s.getOutputStream()
                                    if (!path.startsWith("/callback")) {
                                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                        out.write(notFound.toByteArray(StandardCharsets.UTF_8))
                                        out.flush()
                                    } else {
                                        val uri = Uri.parse("http://127.0.0.1$path")
                                        val html = "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='refresh' content='0;url=mochy-backup://gdrive-done'><style>body{background:#111;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:24px;text-align:center}a{margin-top:16px;padding:12px 24px;border-radius:999px;background:#fff;color:#111;text-decoration:none;font-weight:600}</style></head><body><h2>Signed in to Google Drive</h2><p>Returning to Mochy...</p><a href='mochy-backup://gdrive-done'>Return to Mochy</a><script>setTimeout(function(){window.location.href='mochy-backup://gdrive-done';},120);</script></body></html>"
                                        val bodyBytes = html.toByteArray(StandardCharsets.UTF_8)
                                        val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n"
                                        out.write(resp.toByteArray(StandardCharsets.UTF_8))
                                        out.write(bodyBytes)
                                        out.flush()
                                        callbackDeferred.complete(uri)
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            val cb = withTimeoutOrNull(300_000L) { callbackDeferred.await() }
            delay(250)
            acceptJob.cancel()
            runCatching { server.close() }
            if (cb == null) {
                _login.value = CloudLogin.Failed(CloudProblem.Expired)
                return
            }
            if (cb.getQueryParameter("state") != expectedState) {
                _login.value = CloudLogin.Failed(CloudProblem.Expired)
                return
            }
            val code = cb.getQueryParameter("code")
            if (code.isNullOrEmpty()) {
                _login.value = CloudLogin.Failed(
                    if (cb.getQueryParameter("error") == "access_denied") CloudProblem.Denied else CloudProblem.Busy
                )
                return
            }
            _login.value = CloudLogin.Finishing(CloudKind.GoogleDrive)
            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", verifier)
            if (clientSecret.isNotEmpty()) form.add("client_secret", clientSecret)
            val reply = CloudHttp.text(Request.Builder().url(GoogleDriveCloud.TokenUrl).post(form.build()).build())
            if (reply.code == 400 || reply.code == 401) throw CloudException(CloudProblem.WrongLogin)
            if (!reply.ok) CloudHttp.fail(reply.code)
            val json = JSONObject(reply.body)
            val access = json.optString("access_token").ifEmpty { throw CloudException(CloudProblem.Busy) }
            val refresh = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in")
            val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L
            val name = GoogleDriveCloud.accountName(access)
            accounts.current()?.let { old ->
                if (old.kind == CloudKind.GoogleDrive) GoogleDriveCloud.revoke(old.accessToken)
                else OAuthApp.of(old.kind)?.revoke(old.accessToken)
            }
            accounts.save(
                CloudAccount(
                    kind = CloudKind.GoogleDrive,
                    name = name,
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAt = expiresAt,
                    username = clientId,
                    password = clientSecret,
                )
            )
            _login.value = CloudLogin.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_login.value !is CloudLogin.Idle) {
                Timber.tag(Tag).w(e, "Google Drive sign-in didn't finish")
                _login.value = CloudLogin.Failed(
                    (e as? CloudException)?.problem ?: if (e is IOException) CloudProblem.Offline else CloudProblem.Busy
                )
            }
        } finally {
            runCatching { server.close() }
            if (loopbackServer === server) loopbackServer = null
        }
    }

    private companion object {
        const val Tag = "CloudBackup"
        const val PendingName = "cloud_backup_login.json"
        const val OAuthStateTtlMs = 15 * 60_000L
    }
}
