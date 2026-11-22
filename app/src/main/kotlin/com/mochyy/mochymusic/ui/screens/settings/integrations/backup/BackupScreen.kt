// mochy → settings → integrations → backup: which cloud, back up now, the schedule, and bringing a backup back

package com.mochyy.mochymusic.ui.screens.settings.integrations.backup

import com.mochyy.mochymusic.ui.screens.library.showConfirm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.backup.CloudAccount
import com.mochyy.mochymusic.backup.CloudBackupStep
import com.mochyy.mochymusic.backup.CloudKind
import com.mochyy.mochymusic.backup.CloudLogin
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationPageSubtitle
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageDanger
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageMuted
import com.mochyy.mochymusic.ui.screens.settings.storage.formatSize
import com.mochyy.mochymusic.ui.screens.settings.storage.storageText
import com.mochyy.mochymusic.viewmodels.CloudBackupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BackupRoute = "integrations_settings/backup"

// onedrive stays off the page until its microsoft app is registered, the code behind it is ready
private val ShownClouds = listOf(CloudKind.GoogleDrive, CloudKind.Dropbox, CloudKind.WebDav)

@Composable
fun BackupScreen(
    navController: NavController,
    viewModel: CloudBackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlay = LocalZoomOutOverlayState.current
    val account by viewModel.account.collectAsState()
    val login by viewModel.login.collectAsState()
    val step by viewModel.step.collectAsState()
    val cleaningOld by viewModel.cleaningOld.collectAsState()

    LaunchedEffect(login) {
        (login as? CloudLogin.Failed)?.let {
            Toast.makeText(context, viewModel.messageOf(it.problem), Toast.LENGTH_LONG).show()
            viewModel.dismissLoginProblem()
        }
    }
    LaunchedEffect(step) {
        when (val now = step) {
            CloudBackupStep.BackedUp -> {
                Toast.makeText(context, context.getString(R.string.backup_done_toast, account?.kind?.label.orEmpty()), Toast.LENGTH_SHORT).show()
                viewModel.acknowledge()
            }
            is CloudBackupStep.Failed -> {
                Toast.makeText(context, now.message, Toast.LENGTH_LONG).show()
                viewModel.acknowledge()
            }
            else -> Unit
        }
    }
    // back from the browser without an answer, give the redirect a moment to land before the rows go back to sign in
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(1_500)
            viewModel.cancelWaiting()
        }
    }

    fun openWebDav() {
        var sheet: PopupSheetHandle? = null
        sheet = overlay.showWithHandle {
            WebDavSignInContent(
                viewModel = viewModel,
                current = account?.takeIf { it.kind == CloudKind.WebDav },
                onConnected = { host ->
                    sheet?.dismiss()
                    Toast.makeText(context, context.getString(R.string.backup_connected_toast, host), Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    fun openGoogleDrive() {
        var sheet: PopupSheetHandle? = null
        sheet = overlay.showWithHandle {
            GoogleDriveSignInContent(
                viewModel = viewModel,
                onLaunch = { uri ->
                    sheet?.dismiss()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        viewModel.cancelWaiting()
                        Toast.makeText(context, context.getString(R.string.backup_no_browser), Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
    }

    fun signIn(kind: CloudKind) {
        if (kind == CloudKind.WebDav) {
            openWebDav()
            return
        }
        if (kind == CloudKind.GoogleDrive) {
            if (viewModel.hasBuiltInGoogleDriveCredentials()) {
                scope.launch {
                    runCatching { viewModel.startBuiltInGoogleDriveSignIn() }
                        .onSuccess { uri ->
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: ActivityNotFoundException) {
                                viewModel.cancelWaiting()
                                Toast.makeText(context, context.getString(R.string.backup_no_browser), Toast.LENGTH_LONG).show()
                            }
                        }
                        .onFailure {
                            viewModel.cancelWaiting()
                            Toast.makeText(context, it.message ?: "Could not start Google sign-in", Toast.LENGTH_LONG).show()
                        }
                }
            } else {
                openGoogleDrive()
            }
            return
        }
        scope.launch {
            val uri = viewModel.loginUri(kind)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                viewModel.cancelWaiting()
                Toast.makeText(context, context.getString(R.string.backup_no_browser), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openRestore() {
        viewModel.loadBackups()
        overlay.show { RestoreBackupContent(viewModel) }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.integration_backup_name),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.integrations),
    ) {
        IntegrationPageSubtitle(stringResource(R.string.backup_page_subtitle))
        CloudGroup(
            account = account,
            login = login,
            isConfigured = viewModel::isConfigured,
            onSignIn = ::signIn,
            onDisconnect = viewModel::disconnect,
        )
        Spacer(Modifier.height(16.dp))
        BackupsGroup(
            account = account,
            step = step,
            cleaningOld = cleaningOld,
            onBackUp = viewModel::backUpNow,
            onRestore = ::openRestore,
            onDeleteOld = {
                overlay.showConfirm(
                    title = "Delete old backups?",
                    message = "All older backups in your cloud will be permanently deleted. Only the most recent backup will be kept.",
                    action = "Delete old backups",
                ) {
                    viewModel.deleteOldBackups { result ->
                        result.onSuccess { count ->
                            val msg = when (count) {
                                0 -> "No old backups to delete"
                                1 -> "Deleted 1 old backup"
                                else -> "Deleted $count old backups"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, viewModel.messageOf(err), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )
        account?.lastProblem?.takeIf { step == CloudBackupStep.Idle }?.let { problem ->
            Text(
                stringResource(R.string.backup_last_failed, viewModel.messageOf(problem)),
                style = storageText(13f, FontWeight.Medium, StorageDanger, -0.2f),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        BackupScheduleCards(connected = account != null)
        Spacer(Modifier.height(140.dp))
    }
}

/** a row per cloud, the connected one ticked, and a way to disconnect */
@Composable
private fun CloudGroup(
    account: CloudAccount?,
    login: CloudLogin,
    isConfigured: (CloudKind) -> Boolean,
    onSignIn: (CloudKind) -> Unit,
    onDisconnect: () -> Unit,
) {
    val clouds = ShownClouds.map { kind ->
        val connected = account?.kind == kind
        val configured = isConfigured(kind)
        SettingsItem(
            title = { Text(kind.label) },
            highlightKey = kind.label,
            searchText = kind.label,
            descriptionText = when {
                login is CloudLogin.InBrowser && login.kind == kind -> stringResource(R.string.backup_waiting_browser)
                login is CloudLogin.Finishing && login.kind == kind -> stringResource(R.string.backup_finishing_sign_in)
                connected && kind == CloudKind.WebDav -> stringResource(R.string.backup_webdav_connected, account?.name.orEmpty())
                connected -> stringResource(R.string.backup_signed_in_as, account?.name.orEmpty())
                !configured -> stringResource(R.string.backup_not_set_up)
                else -> stringResource(
                    when (kind) {
                        CloudKind.GoogleDrive -> R.string.backup_gdrive_desc
                        CloudKind.Dropbox -> R.string.backup_dropbox_desc
                        CloudKind.OneDrive -> R.string.backup_onedrive_desc
                        CloudKind.WebDav -> R.string.backup_webdav_desc
                    },
                )
            },
            icon = painterResource(if (kind == CloudKind.WebDav) R.drawable.storage else R.drawable.cloud),
            iconShape = CircleShape,
            enabled = configured,
            // tapping the connected one signs in again, to switch accounts or fix a login that stopped working
            onClick = { onSignIn(kind) },
            trailingContent = if (connected) {
                { Icon(painterResource(R.drawable.check), contentDescription = null) }
            } else {
                null
            },
        )
    }
    val disconnect = account?.let {
        SettingsItem(
            title = { Text(stringResource(R.string.backup_disconnect)) },
            descriptionText = stringResource(R.string.backup_disconnect_desc, it.kind.label),
            icon = painterResource(R.drawable.logout),
            iconShape = CircleShape,
            onClick = onDisconnect,
        )
    }
    SettingsGroup(
        title = stringResource(R.string.backup_group_where),
        style = SettingsGroupStyle.Grouped,
        items = clouds + listOfNotNull(disconnect),
    )
    // dropbox's app is still in development mode, so new sign-ins can stop; someone already connected keeps working
    if (account?.kind != CloudKind.Dropbox && isConfigured(CloudKind.Dropbox)) {
        Text(
            stringResource(R.string.backup_dropbox_notice),
            style = storageText(13f, FontWeight.Medium, StorageMuted, -0.2f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
    }
}

/** back up now, with how the last one went or how far this one is, and restore */
@Composable
private fun BackupsGroup(
    account: CloudAccount?,
    step: CloudBackupStep,
    cleaningOld: Boolean,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onDeleteOld: () -> Unit,
) {
    val resting = step == CloudBackupStep.Idle && !cleaningOld
    val status = stepText(step) ?: when {
        account == null -> stringResource(R.string.backup_connect_first)
        account.lastBackupAt > 0 -> stringResource(
            R.string.backup_last,
            DateUtils.getRelativeTimeSpanString(account.lastBackupAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
            formatSize(account.lastBackupBytes),
        )
        else -> stringResource(R.string.backup_never)
    }
    SettingsGroup(
        title = stringResource(R.string.backup_group_backups),
        style = SettingsGroupStyle.Grouped,
        items = listOf(
            SettingsItem(
                title = { Text(stringResource(R.string.backup_now)) },
                highlightKey = "Back up now",
                descriptionText = status,
                icon = painterResource(R.drawable.backup),
                iconShape = CircleShape,
                enabled = account != null && resting,
                onClick = onBackUp,
            ),
            SettingsItem(
                title = { Text(stringResource(R.string.backup_restore_title)) },
                highlightKey = "Restore a backup",
                descriptionText = stringResource(R.string.backup_restore_desc),
                icon = painterResource(R.drawable.restore),
                iconShape = CircleShape,
                enabled = account != null && resting,
                onClick = onRestore,
            ),
            SettingsItem(
                title = { Text("Delete old backups") },
                highlightKey = "Delete old backups",
                descriptionText = if (cleaningOld) "Deleting old backups..." else "Keep only the most recent backup",
                icon = painterResource(R.drawable.delete),
                iconShape = CircleShape,
                enabled = account != null && resting,
                onClick = onDeleteOld,
            ),
        ),
    )
}

/** how far a backup or restore has got, or null while nothing is running */
@Composable
internal fun stepText(step: CloudBackupStep): String? = when (step) {
    is CloudBackupStep.Packing -> stringResource(R.string.backup_packing, percent(step.progress))
    is CloudBackupStep.Uploading -> stringResource(R.string.backup_uploading, percent(step.progress))
    is CloudBackupStep.Downloading -> stringResource(R.string.backup_downloading, percent(step.progress))
    is CloudBackupStep.Unpacking -> stringResource(R.string.backup_unpacking, percent(step.progress))
    CloudBackupStep.Restored -> stringResource(R.string.backup_restored)
    else -> null
}

private fun percent(progress: Float): Int = (progress * 100).toInt().coerceIn(0, 100)


@Composable
private fun GoogleDriveSignInContent(
    viewModel: CloudBackupViewModel,
    onLaunch: (android.net.Uri) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val saved = remember { viewModel.savedGoogleDriveCredentials() }
    val clientIdState = remember { mutableStateOf(saved.first) }
    val clientSecretState = remember { mutableStateOf(saved.second) }
    val errorState = remember { mutableStateOf<String?>(null) }
    val busyState = remember { mutableStateOf(false) }

    val clientId = clientIdState.value
    val clientSecret = clientSecretState.value
    val errorText = errorState.value
    val isBusy = busyState.value

    val wp = com.mochyy.mochymusic.ui.component.LocalWallpaperPalette.current
    val fieldBg = wp?.surface ?: Color(0xFF171717)
    val borderCol = wp?.primary?.copy(alpha = 0.24f) ?: Color.White.copy(alpha = 0.14f)
    val btnBg = wp?.primary ?: Color.White
    val btnFg = wp?.onPrimary ?: Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Connect Google Drive",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Create a free Desktop app OAuth Client in Google Cloud Console (enable Google Drive API and add your Gmail as a Test user), then paste your credentials below.",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(14.dp))

        Text("Client ID", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(fieldBg)
                .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (clientId.isEmpty()) {
                Text("xxxx.apps.googleusercontent.com", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
            }
            BasicTextField(
                value = clientId,
                onValueChange = { clientIdState.value = it; errorState.value = null },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Client Secret", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(fieldBg)
                .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (clientSecret.isEmpty()) {
                Text("GOCSPX-...", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
            }
            BasicTextField(
                value = clientSecret,
                onValueChange = { clientSecretState.value = it; errorState.value = null },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText, color = StorageDanger, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        val canSubmit = !isBusy && clientId.isNotBlank()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(if (canSubmit) btnBg else btnBg.copy(alpha = 0.35f))
                .clickable(enabled = canSubmit) {
                    busyState.value = true
                    scope.launch {
                        runCatching { viewModel.startGoogleDriveSignIn(clientId, clientSecret) }
                            .onSuccess { onLaunch(it) }
                            .onFailure {
                                busyState.value = false
                                errorState.value = it.message ?: "Could not start Google sign-in"
                            }
                    }
                },
        ) {
            Text(
                text = if (isBusy) "Opening browser..." else "Sign in with Google",
                color = btnFg,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
