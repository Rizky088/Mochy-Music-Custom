// last.fm or libre.fm sign-in right inside mochy, the password goes to the service once and only the key it sends back is kept

package com.mochyy.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetTextField
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.IntegrationsViewModel
import com.music.lastfm.AudioscrobblerException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

private val ProblemColor = Color(0xFFFF8A80)

@Composable
fun ScrobbleSignInContent(
    site: ScrobbleSite,
    viewModel: IntegrationsViewModel,
    onSignedIn: (username: String) -> Unit,
) {
    if (site == ScrobbleSite.LISTENBRAINZ) {
        ListenBrainzSignInContent(viewModel = viewModel, onSignedIn = onSignedIn)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var username by rememberSaveable { mutableStateOf("") }
    // plain remember on purpose, a saveable password would be written into the saved screen state
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val canSubmit = username.isNotBlank() && password.isNotEmpty() && !busy

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        busy = true
        problem = null
        scope.launch {
            try {
                onSignedIn(viewModel.signIn(site, username, password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioscrobblerException) {
                Timber.tag("Scrobble").w(e, "${site.label} sign-in turned down")
                problem = if (e.isWrongLogin) {
                    context.getString(R.string.scrobble_sign_in_wrong)
                } else {
                    context.getString(R.string.scrobble_sign_in_busy, site.label)
                }
            } catch (e: Exception) {
                problem = context.getString(R.string.scrobble_sign_in_offline, site.label)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            stringResource(R.string.scrobble_sign_in_title, site.label),
            color = SearchColors.Primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.scrobble_sign_in_body, site.label),
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(22.dp))

        SheetLabel(stringResource(R.string.username))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = username,
            onValueChange = {
                username = it
                problem = null
            },
            placeholder = stringResource(R.string.scrobble_username_hint, site.label),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.semantics { contentType = ContentType.Username },
        )
        Spacer(Modifier.height(18.dp))

        SheetLabel(stringResource(R.string.password))
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = password,
            onValueChange = {
                password = it
                problem = null
            },
            placeholder = stringResource(R.string.scrobble_password_hint, site.label),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.semantics { contentType = ContentType.Password },
        )

        problem?.let {
            Text(
                it,
                color = ProblemColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
        Spacer(Modifier.height(22.dp))

        SheetPillButton(
            text = stringResource(if (busy) R.string.scrobble_signing_in else R.string.scrobble_sign_in_button),
            onClick = ::submit,
            enabled = canSubmit,
        )
        Text(
            stringResource(R.string.scrobble_sign_up, site.label),
            color = SearchColors.Secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable { uriHandler.openUri(site.signUpUrl) }
                .padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun ListenBrainzSignInContent(
    viewModel: IntegrationsViewModel,
    onSignedIn: (username: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf(com.mochyy.mochymusic.scrobble.ListenBrainzClient.DefaultServerUrl) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val canSubmit = token.isNotBlank() && !busy

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        busy = true
        problem = null
        scope.launch {
            try {
                onSignedIn(viewModel.signInListenBrainz(token, serverUrl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioscrobblerException) {
                problem = if (e.isWrongLogin) "Invalid ListenBrainz user token." else e.message ?: "ListenBrainz rejected the request."
            } catch (e: Exception) {
                problem = "Couldn't reach ListenBrainz. Check your connection or server URL."
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            "Sign in to ListenBrainz",
            color = SearchColors.Primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Paste your free User Token from ListenBrainz settings. No paid API key required.",
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))

        SheetLabel("User Token")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = token,
            onValueChange = {
                token = it
                problem = null
            },
            placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.height(14.dp))

        SheetLabel("Server URL (optional)")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                problem = null
            },
            placeholder = com.mochyy.mochymusic.scrobble.ListenBrainzClient.DefaultServerUrl,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        )

        problem?.let {
            Text(
                it,
                color = ProblemColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp),
            )
        }
        Spacer(Modifier.height(18.dp))

        SheetPillButton(
            text = if (busy) "Verifying token..." else "Connect ListenBrainz",
            onClick = ::submit,
            enabled = canSubmit,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Get your token at listenbrainz.org/settings",
            color = SearchColors.Secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(com.mochyy.mochymusic.scrobble.ListenBrainzClient.SettingsUrl) }
                .padding(vertical = 8.dp),
        )
    }
}

@Composable
fun LastFmCustomKeySheetContent(
    viewModel: IntegrationsViewModel,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var apiKey by rememberSaveable { mutableStateOf(ScrobbleSite.customLastFmKey) }
    var secret by remember { mutableStateOf(ScrobbleSite.customLastFmSecret) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            "Custom Last.fm API Key",
            color = SearchColors.Primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Last.fm requires a Last.fm Pro subscription for API keys. If you have your own key and shared secret, enter them here to unlock direct Last.fm sign-in.",
            color = SearchColors.Secondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(18.dp))

        SheetLabel("API Key")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = "32-character Last.fm API key",
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(14.dp))

        SheetLabel("Shared Secret")
        Spacer(Modifier.height(8.dp))
        SheetTextField(
            value = secret,
            onValueChange = { secret = it },
            placeholder = "32-character Last.fm shared secret",
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(18.dp))

        SheetPillButton(
            text = if (busy) "Saving..." else if (apiKey.isBlank() && secret.isBlank()) "Clear custom key" else "Save API key",
            enabled = !busy && ((apiKey.isNotBlank() && secret.isNotBlank()) || (apiKey.isBlank() && secret.isBlank())),
            onClick = {
                keyboard?.hide()
                busy = true
                scope.launch {
                    viewModel.saveLastFmCustomCredentials(apiKey, secret)
                    busy = false
                    onSaved()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Manage keys at last.fm/api/account/create",
            color = SearchColors.Secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri("https://www.last.fm/api/account/create") }
                .padding(vertical = 8.dp),
        )
    }
}
