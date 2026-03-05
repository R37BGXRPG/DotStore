package com.jusdots.dotstore.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jusdots.dotstore.data.api.GitHubService
import com.jusdots.dotstore.data.notification.NotificationHelper
import com.jusdots.dotstore.data.repository.AppRepository
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val gitHubService = retrofit.create(GitHubService::class.java)
        val repository = AppRepository(gitHubService)
        val notificationHelper = NotificationHelper(applicationContext)

        repository.getApps().forEach { app ->
            val latestRelease = repository.getLatestRelease(app.repoOwner, app.repoName).firstOrNull()
            if (latestRelease != null) {
                val installedVersion = com.jusdots.dotstore.utils.AppUtils.getInstalledVersion(applicationContext, app.packageName)
                if (installedVersion != null && installedVersion != latestRelease.tagName) { 
                    notificationHelper.showUpdateNotification(app.name, latestRelease.tagName)
                }
            }
        }

        return Result.success()
    }
}
