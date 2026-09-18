package com.darki.cloud.data.transfer

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.darki.cloud.data.local.TransferEntity
import java.util.concurrent.TimeUnit

object TransferScheduler {
    private fun request(transfer: TransferEntity) = OneTimeWorkRequestBuilder<TransferWorker>()
        .setInputData(workDataOf(TransferWorker.KEY_TRANSFER_ID to transfer.id))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()

    fun enqueue(context: Context, transfer: TransferEntity) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "darki-transfer-${transfer.id}",
            ExistingWorkPolicy.KEEP,
            request(transfer),
        )
    }

    fun cancel(context: Context, transfer: TransferEntity) {
        WorkManager.getInstance(context).cancelUniqueWork("darki-transfer-${transfer.id}")
    }

    fun retry(context: Context, transfer: TransferEntity) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "darki-transfer-${transfer.id}",
            ExistingWorkPolicy.REPLACE,
            request(transfer),
        )
    }
}
