package com.darki.cloud.data.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darki.cloud.BuildConfig
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class TransferWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_TRANSFER_ID) ?: return@withContext Result.failure()
        val db = CloudDatabase.create(applicationContext)
        val dao = db.cloudDao()
        val transfer = dao.findTransferById(id) ?: return@withContext Result.success()
        val store = SessionStore(applicationContext)
        val token = store.token ?: return@withContext Result.failure()
        val api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient())
        val repository = CloudRepository(api, dao)
        dao.upsertTransfer(transfer.copy(status = "running", attempts = transfer.attempts + 1, updatedAt = System.currentTimeMillis()))
        var temporaryDownload: File? = null
        try {
            when (transfer.type) {
                "upload" -> {
                    val uri = transfer.uri?.let(android.net.Uri::parse) ?: error("Missing upload URI")
                    val folderId = transfer.folderId ?: error("Missing upload folder")
                    val name = transfer.name ?: error("Missing upload name")
                    val size = transfer.sizeBytes ?: error("Missing upload size")
                    val input = applicationContext.contentResolver.openInputStream(uri) ?: error("Unable to open upload")
                    input.use { repository.uploadFile(token, folderId, name, transfer.mimeType, size, store.deviceId ?: error("Missing device"), transfer.id, it) }
                }
                "download" -> {
                    val fileId = transfer.fileId ?: error("Missing download file")
                    val destination = transfer.destinationUri?.let(android.net.Uri::parse) ?: error("Missing download destination")
                    temporaryDownload = File.createTempFile("darki-download-", ".part", applicationContext.cacheDir)
                    FileOutputStream(temporaryDownload).use { repository.downloadFile(token, fileId, it) }
                    applicationContext.contentResolver.openOutputStream(destination)?.use { output ->
                        temporaryDownload!!.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("Unable to open download destination")
                }
                else -> error("Unknown transfer type")
            }
            dao.deleteTransfer(id)
            Result.success()
        } catch (error: Exception) {
            val attempts = transfer.attempts + 1
            val terminal = attempts >= MAX_ATTEMPTS
            dao.upsertTransfer(transfer.copy(status = if (terminal) "failed" else "queued", attempts = attempts, lastError = error.message, updatedAt = System.currentTimeMillis()))
            if (terminal) Result.failure() else Result.retry()
        } finally {
            temporaryDownload?.delete()
            db.close()
        }
    }

    companion object {
        const val KEY_TRANSFER_ID = "transfer_id"
        private const val MAX_ATTEMPTS = 5
        fun id(): String = UUID.randomUUID().toString()
    }
}
