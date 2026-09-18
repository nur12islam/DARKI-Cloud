package com.darki.cloud.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.darki.cloud.BuildConfig
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import com.darki.cloud.data.repository.CloudRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = syncMutex.withLock {
        withContext(Dispatchers.IO) {
        val store = SessionStore(applicationContext)
        val token = store.token ?: return@withContext Result.success()

        val db = CloudDatabase.create(applicationContext)
        try {
            val dao = db.cloudDao()
            val api = DarkiCloudApi(BuildConfig.DARKI_CLOUD_BASE_URL, OkHttpClient())
            val repository = CloudRepository(api, dao)

            val deviceId = store.deviceId ?: repository
                .registerDevice(
                    token,
                    "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    "Android ${android.os.Build.VERSION.RELEASE}",
                )
                .also { store.deviceId = it.id }
                .id

            val nextCursor = repository.sync(token, deviceId, store.cursor)
            store.cursor = nextCursor
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }

    companion object {
        private val syncMutex = Mutex()
    }
}
