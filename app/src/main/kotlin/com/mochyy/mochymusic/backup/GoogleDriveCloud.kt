// GoogleDriveCloud.kt

package com.mochyy.mochymusic.backup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

internal class GoogleDriveSession(private val store: CloudAccountStore) {
    suspend fun send(build: (token: String) -> Request): Response {
        val token = token()
        val response = CloudHttp.send(build(token))
        if (response.code != 401) return response
        response.close()
        return CloudHttp.send(build(renew(token)))
    }

    suspend fun text(build: (token: String) -> Request): CloudHttp.Reply =
        send(build).use { CloudHttp.Reply(it.code, it.body.string()) }

    private suspend fun token(): String {
        val account = store.current() ?: throw CloudException(CloudProblem.SignedOut)
        if (account.expiresAt == 0L || System.currentTimeMillis() < account.expiresAt - RenewEarlyMs) {
            return account.accessToken
        }
        return renew(account.accessToken)
    }

    private suspend fun renew(rejected: String): String = Renewing.withLock {
        val account = store.current() ?: throw CloudException(CloudProblem.SignedOut)
        if (account.accessToken != rejected) return@withLock account.accessToken
        val refresh = account.refreshToken.ifEmpty { throw CloudException(CloudProblem.SignedOut) }
        val clientId = account.username.ifEmpty { throw CloudException(CloudProblem.SignedOut) }
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
        if (account.password.isNotEmpty()) form.add("client_secret", account.password)
        val reply = CloudHttp.text(Request.Builder().url(GoogleDriveCloud.TokenUrl).post(form.build()).build())
        if (reply.code == 400 || reply.code == 401) throw CloudException(CloudProblem.SignedOut)
        if (!reply.ok) CloudHttp.fail(reply.code)
        val json = JSONObject(reply.body)
        val access = json.optString("access_token").ifEmpty { throw CloudException(CloudProblem.Busy) }
        val expiresIn = json.optLong("expires_in")
        val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L
        val newRefresh = json.optString("refresh_token").ifEmpty { null }
        store.update {
            it.copy(
                accessToken = access,
                refreshToken = newRefresh ?: it.refreshToken,
                expiresAt = expiresAt,
            )
        }
        access
    }

    private companion object {
        const val RenewEarlyMs = 5 * 60_000L
        val Renewing = Mutex()
    }
}

/** backups inside a "Mochy Backups" folder on the user's Google Drive using Drive API v3 */
internal class GoogleDriveCloud(private val session: GoogleDriveSession) : BackupCloud {
    private var cachedFolderId: String? = null

    override suspend fun upload(file: File, name: String, onProgress: (Float) -> Unit) {
        val folderId = findOrCreateFolder()
        val size = file.length()
        val meta = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put(folderId))
        val uploadUrl = session.send { token ->
            Request.Builder()
                .url("$UploadApi/files?uploadType=resumable")
                .header("Authorization", "Bearer $token")
                .header("X-Upload-Content-Type", "application/zip")
                .header("X-Upload-Content-Length", size.toString())
                .post(meta.toString().toRequestBody(CloudHttp.Json))
                .build()
        }.use { response ->
            if (!response.isSuccessful) CloudHttp.fail(response.code)
            response.header("Location") ?: throw CloudException(CloudProblem.Busy)
        }

        val report = { sent: Long ->
            if (size > 0) onProgress((sent.toDouble() / size).toFloat().coerceIn(0f, 1f))
        }
        var offset = 0L
        while (offset < size) {
            val length = minOf(Chunk, size - offset)
            val reply = CloudHttp.text(
                Request.Builder()
                    .url(uploadUrl)
                    .header("Content-Range", "bytes $offset-${offset + length - 1}/$size")
                    .put(FileSliceBody(file, offset, length, report))
                    .build(),
            )
            if (!reply.ok && reply.code != 308) CloudHttp.fail(reply.code)
            offset += length
        }
    }

    override suspend fun list(): List<CloudBackupFile> {
        val folderId = findFolder() ?: return emptyList()
        val found = mutableListOf<CloudBackupFile>()
        var pageToken: String? = null
        while (true) {
            val urlBuilder = "$DriveApi/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$folderId' in parents and trashed=false")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("pageSize", "200")
                .addQueryParameter("fields", "nextPageToken,files(id,name,size,modifiedTime)")
            if (!pageToken.isNullOrEmpty()) urlBuilder.addQueryParameter("pageToken", pageToken)
            val url = urlBuilder.build().toString()
            val reply = session.text { token ->
                Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            }
            if (!reply.ok) CloudHttp.fail(reply.code)
            val page = JSONObject(reply.body)
            val files = page.optJSONArray("files")
            for (i in 0 until (files?.length() ?: 0)) {
                val item = files!!.getJSONObject(i)
                val name = item.optString("name")
                if (!BackupNames.isBackup(name)) continue
                found += CloudBackupFile(
                    id = item.getString("id"),
                    name = name,
                    bytes = item.optString("size").toLongOrNull() ?: item.optLong("size"),
                    modifiedAt = runCatching { Instant.parse(item.optString("modifiedTime")).toEpochMilli() }.getOrDefault(0L),
                )
            }
            pageToken = page.optString("nextPageToken").ifEmpty { null }
            if (pageToken == null) break
        }
        return found
    }

    override suspend fun download(backup: CloudBackupFile, into: File, onProgress: (Float) -> Unit) {
        session.send { token ->
            Request.Builder()
                .url("$DriveApi/files/${backup.id}?alt=media")
                .header("Authorization", "Bearer $token")
                .build()
        }.use { response ->
            if (!response.isSuccessful) CloudHttp.fail(response.code)
            response.saveTo(into, backup.bytes, onProgress)
        }
    }

    override suspend fun delete(backup: CloudBackupFile) {
        val reply = session.text { token ->
            Request.Builder()
                .url("$DriveApi/files/${backup.id}")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
        }
        if (!reply.ok && reply.code != 404) CloudHttp.fail(reply.code)
    }

    private suspend fun findFolder(): String? {
        cachedFolderId?.let { return it }
        val url = "$DriveApi/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", "name='$FolderName' and mimeType='$FolderMime' and trashed=false")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "1")
            .build()
            .toString()
        val reply = session.text { token ->
            Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        }
        if (!reply.ok) CloudHttp.fail(reply.code)
        val files = JSONObject(reply.body).optJSONArray("files")
        val id = if (files != null && files.length() > 0) files.getJSONObject(0).optString("id").ifEmpty { null } else null
        cachedFolderId = id
        return id
    }

    private suspend fun findOrCreateFolder(): String {
        findFolder()?.let { return it }
        val body = JSONObject()
            .put("name", FolderName)
            .put("mimeType", FolderMime)
        val reply = session.text { token ->
            Request.Builder()
                .url("$DriveApi/files?fields=id")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(CloudHttp.Json))
                .build()
        }
        if (!reply.ok) CloudHttp.fail(reply.code)
        val id = JSONObject(reply.body).getString("id")
        cachedFolderId = id
        return id
    }

    companion object {
        const val AuthorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TokenUrl = "https://oauth2.googleapis.com/token"
        const val Scope = "https://www.googleapis.com/auth/drive.file"
        private const val DriveApi = "https://www.googleapis.com/drive/v3"
        private const val UploadApi = "https://www.googleapis.com/upload/drive/v3"
        private const val FolderName = "Mochy Backups"
        private const val FolderMime = "application/vnd.google-apps.folder"
        // multiple of 256 KiB as required by Google Drive resumable upload
        private const val Chunk = 8L * 1024 * 1024

        suspend fun accountName(accessToken: String): String = runCatching {
            val reply = CloudHttp.text(
                Request.Builder()
                    .url("$DriveApi/about?fields=user(emailAddress,displayName)")
                    .header("Authorization", "Bearer $accessToken")
                    .build(),
            )
            check(reply.ok)
            val user = JSONObject(reply.body).optJSONObject("user")
            user?.optString("emailAddress")?.ifEmpty { user.optString("displayName") }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: CloudKind.GoogleDrive.label

        suspend fun revoke(accessToken: String) {
            if (accessToken.isEmpty()) return
            runCatching {
                CloudHttp.text(
                    Request.Builder()
                        .url("https://oauth2.googleapis.com/revoke")
                        .post(FormBody.Builder().add("token", accessToken).build())
                        .build(),
                )
            }
        }
    }
}
