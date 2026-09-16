package com.darki.cloud.data.repository

import android.content.Context
import com.darki.cloud.data.api.DarkiCloudApi
import com.darki.cloud.data.local.CloudDatabase
import com.darki.cloud.data.local.SessionStore
import okhttp3.OkHttpClient

class CloudContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = CloudDatabase.create(appContext)
    private val sessionStore = SessionStore(appContext)
    private val api = DarkiCloudApi(
        baseUrl = "http://10.0.2.2:8080",
        client = OkHttpClient(),
    )

    val repository = CloudRepository(api, database.cloudDao())
    val session = sessionStore
}
