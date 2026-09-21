package com.darki.cloud.data.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darki.cloud.BuildConfig
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val store = SessionStore(applicationContext)
        val token = store.token ?: return@withContext Result.success()
        val treeUri = store.backupTreeUri ?: return@withContext Result.success()
        if (!store.backupEnabled) return@withContext Result.success()

        val tree = DocumentFile.fromTreeUri(applicationContext, android.net.Uri.parse(treeUri))
            ?: return@withContext Result.failure()
        val db = CloudDatabase.create(applicationContext)
        try {
            val repository = CloudRepository(DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient()), db.cloudDao())
            val deviceId = store.deviceId ?: repository.registerDevice(
                token,
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                "Android ${android.os.Build.VERSION.RELEASE}",
            ).also { store.deviceId = it.id }.id
            val root = repository.loadRoot(token)
            val backupFolderId = store.backupFolderId ?: repository.createFolder(
                token, root.id, "DARKI Cloud Backup", deviceId,
            ).also { store.backupFolderId = it.id }.id
            backupTree(tree, repository, token, backupFolderId, deviceId, store)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }

    private suspend fun backupTree(
        tree: DocumentFile,
        repository: CloudRepository,
        token: String,
        folderId: String,
        deviceId: String,
        store: SessionStore,
    ) {
        for (file in tree.listFiles()) {
            if (!file.isFile) continue
            val uri = file.uri.toString()
            val size = file.length().coerceAtLeast(0L)
            val modified = file.lastModified()
            val fingerprint = "$size:$modified"
            if (store.getBackupFingerprint(uri) == fingerprint) continue
            val input = applicationContext.contentResolver.openInputStream(file.uri) ?: continue
            input.use {
                repository.uploadFile(
                    token, folderId, file.name ?: "Unnamed file",
                    applicationContext.contentResolver.getType(file.uri),
                    size, deviceId, "backup:$uri", it,
                )
            }
            store.setBackupFingerprint(uri, fingerprint)
        }
    }
}
