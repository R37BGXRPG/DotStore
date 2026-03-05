package com.jusdots.dotstore.data.repository

import com.jusdots.dotstore.data.api.GitHubService
import com.jusdots.dotstore.data.model.AppInfo
import com.jusdots.dotstore.data.model.GitHubRelease
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AppRepository(private val api: GitHubService) {

    private val apps = listOf(
        AppInfo(
            id = "dotnotes",
            name = "DotNotes",
            developer = "JusDots",
            repoOwner = "shubh72010",
            repoName = "DotNotes",
            packageName = "com.jusdots.dotnotes",
            description = "A simple and elegant note-taking app."
        ),
        AppInfo(
            id = "jusbrowse",
            name = "JusBrowse",
            developer = "JusDots",
            repoOwner = "shubh72010",
            repoName = "JusBrowse",
            packageName = "com.jusdots.jusbrowse",
            description = "A fast and secure web browser with privacy features."
        ),
        AppInfo(
            id = "juschatz",
            name = "JusChatz",
            developer = "JusDots",
            repoOwner = "R37BGXRPG",
            repoName = "JusChatz_by_JusDots",
            packageName = "com.jusdots.juschatz",
            iconResId = com.jusdots.dotstore.R.drawable.juschatz,
            description = "A real-time messaging app with custom themes."
        )
    )

    fun getApps(): List<AppInfo> = apps

    fun getLatestRelease(owner: String, repo: String): Flow<GitHubRelease?> = flow {
        try {
            val releases = api.getReleases(owner, repo)
            emit(releases.firstOrNull())
        } catch (e: Exception) {
            emit(null)
        }
    }
}
