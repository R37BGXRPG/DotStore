package com.jusdots.dotstore.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jusdots.dotstore.ui.theme.JetBrainsMonoFont
import com.jusdots.dotstore.data.model.AppInfo
import com.jusdots.dotstore.data.model.GitHubRelease
import com.jusdots.dotstore.data.repository.AppRepository
import com.jusdots.dotstore.utils.AppUtils
import com.jusdots.dotstore.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppState(
    val info: AppInfo,
    val latestRelease: GitHubRelease? = null,
    val isLoading: Boolean = true,
    val isInstalled: Boolean = false,
    val installedVersion: String? = null,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val icon: Any? = null
)

class HomeViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<List<AppState>>(emptyList())
    val uiState: StateFlow<List<AppState>> = _uiState.asStateFlow()
    
    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    init {
        refresh()
    }

    fun refresh() {
        loadApps()
    }

    private fun loadApps() {
        val apps = repository.getApps()
        _uiState.value = apps.map { app ->
            val isInstalled = AppUtils.isAppInstalled(getApplication(), app.packageName)
            val installedVersion = AppUtils.getInstalledVersion(getApplication(), app.packageName)
            val icon = if (app.iconResId != null) app.iconResId else guessIconUrl(app.repoOwner, app.repoName)
            AppState(
                info = app, 
                isInstalled = isInstalled, 
                installedVersion = installedVersion,
                icon = icon
            )
        }

        apps.forEachIndexed { index, app ->
            viewModelScope.launch {
                repository.getLatestRelease(app.repoOwner, app.repoName).collectLatest { release ->
                    updateAppState(index, release)
                }
            }
        }
    }

    private fun updateAppState(index: Int, release: GitHubRelease?) {
        val currentList = _uiState.value.toMutableList()
        if (index < currentList.size) {
            val currentState = currentList[index]
            val branch = release?.targetCommitish ?: "main"
            val icon = if (currentState.info.iconResId != null) {
                currentState.info.iconResId
            } else {
                guessIconUrl(currentState.info.repoOwner, currentState.info.repoName, branch)
            }
            
            currentList[index] = currentState.copy(
                latestRelease = release,
                isLoading = false,
                icon = icon
            )
            _uiState.value = currentList
        }
    }

    private fun guessIconUrl(owner: String, repo: String, branch: String = "main"): String? {
        if (repo == "JusChatz_by_JusDots") return null
        val targetBranch = if (repo == "JusBrowse") "master" else branch
        return "https://raw.githubusercontent.com/$owner/$repo/$targetBranch/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"
    }

    fun startTrackingDownload(appId: String, downloadId: Long) {
        // Set initial downloading state
        updateDownloadStatus(appId, 0f, true)
        
        viewModelScope.launch {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            updateDownloadStatus(appId, 1f, false)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            updateDownloadStatus(appId, 0f, false)
                        }
                        else -> {
                            val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            
                            val downloaded = cursor.getLong(downloadedIndex)
                            val total = cursor.getLong(totalIndex)
                            
                            if (total > 0) {
                                val progress = downloaded.toFloat() / total.toFloat()
                                updateDownloadStatus(appId, if (progress > 0.99f) 0.99f else progress, true)
                            } else {
                                updateDownloadStatus(appId, 0f, true)
                            }
                        }
                    }
                } else {
                    // If cursor is gone, it usually means it finished or was cancelled
                    downloading = false
                    // We don't force false here because we want the install receiver to handle success
                    // But if it's been a while, we should reset. For now, let's just stop polling.
                }
                cursor?.close()
                if (downloading) delay(500)
            }
        }
    }

    private fun updateDownloadStatus(appId: String, progress: Float, isDownloading: Boolean) {
        val currentList = _uiState.value.toMutableList()
        val index = currentList.indexOfFirst { it.info.id == appId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(
                downloadProgress = progress,
                isDownloading = isDownloading
            )
            _uiState.value = currentList
        }
    }

    fun refreshInstallStatuses() {
        val currentList = _uiState.value.toMutableList()
        val updated = currentList.map { state ->
            val isInstalled = AppUtils.isAppInstalled(getApplication(), state.info.packageName)
            val installedVersion = AppUtils.getInstalledVersion(getApplication(), state.info.packageName)
            state.copy(isInstalled = isInstalled, installedVersion = installedVersion)
        }
        _uiState.value = updated
    }
}
