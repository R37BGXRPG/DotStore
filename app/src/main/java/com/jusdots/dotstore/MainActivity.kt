package com.jusdots.dotstore

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jusdots.dotstore.data.api.GitHubService
import com.jusdots.dotstore.data.download.AppDownloadManager
import com.jusdots.dotstore.data.repository.AppRepository
import com.jusdots.dotstore.ui.screens.AppDetailScreen
import com.jusdots.dotstore.ui.screens.HomeScreen
import com.jusdots.dotstore.ui.theme.DotStoreTheme
import com.jusdots.dotstore.ui.viewmodel.HomeViewModel
import com.jusdots.dotstore.ui.viewmodel.AppState
import com.jusdots.dotstore.utils.AppInstaller
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    
    private lateinit var downloadManager: AppDownloadManager

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != -1L) {
                val query = DownloadManager.Query().setFilterById(id)
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val cursor = manager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val uri = downloadManager.getUriForDownloadedFile(id)
                        
                        if (uri != null) {
                            Toast.makeText(context, "Download Complete! Opening installer...", Toast.LENGTH_LONG).show()
                            com.jusdots.dotstore.utils.AppInstaller.installApkFromUri(this@MainActivity, uri)
                        } else {
                            // Fallback to older method if URI is null
                            val fileUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val fileUriString = cursor.getString(fileUriIndex)
                            if (fileUriString != null) {
                                val fileUri = Uri.parse(fileUriString)
                                val file = fileUri.path?.let { File(it) }
                                if (file != null && file.exists()) {
                                    com.jusdots.dotstore.utils.AppInstaller.installApk(this@MainActivity, file)
                                }
                            }
                        }
                    }
                }
                cursor.close()
            }
        }
    }

    private lateinit var homeViewModel: HomeViewModel

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::homeViewModel.isInitialized) {
                homeViewModel.refreshInstallStatuses()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            this,
            onDownloadComplete,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Package changed receiver
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            packageFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        // Schedule UpdateWorker
        val updateWorkRequest = PeriodicWorkRequestBuilder<com.jusdots.dotstore.worker.UpdateWorker>(
            12, TimeUnit.HOURS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateWorkRequest
        )

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        val gitHubService = retrofit.create(GitHubService::class.java)
        val repository = AppRepository(gitHubService)
        downloadManager = AppDownloadManager(this)

        setContent {
            DotStoreTheme {
                val navController = rememberNavController()
                homeViewModel = viewModel(
                    factory = GenericViewModelFactory { HomeViewModel(application, repository) }
                )
                val uiState by homeViewModel.uiState.collectAsState()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onAppClick = { appId ->
                                navController.navigate("detail/$appId")
                            },
                            onDownloadClick = { url, name, id ->
                                val downloadId = downloadManager.downloadApk(url, name)
                                homeViewModel.startTrackingDownload(id, downloadId)
                                Toast.makeText(this@MainActivity, "Started downloading $name", Toast.LENGTH_SHORT).show()
                            },
                            onOpenClick = { packageName ->
                                com.jusdots.dotstore.utils.AppUtils.openApp(this@MainActivity, packageName)
                            },
                            onAboutClick = {
                                navController.navigate("about")
                            }
                        )
                    }
                    composable("about") {
                        com.jusdots.dotstore.ui.screens.AboutScreen(
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "detail/{appId}",
                        arguments = listOf(navArgument("appId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val appId = backStackEntry.arguments?.getString("appId")
                        val appState = uiState.find { state -> state.info.id == appId }
                        
                        if (appState != null) {
                            AppDetailScreen(
                                appState = appState,
                                onBackClick = { navController.popBackStack() },
                                onDownloadClick = { url, name, id ->
                                    val downloadId = downloadManager.downloadApk(url, name)
                                    homeViewModel.startTrackingDownload(id, downloadId)
                                    Toast.makeText(this@MainActivity, "Started downloading $name", Toast.LENGTH_SHORT).show()
                                },
                                onOpenClick = { packageName ->
                                    com.jusdots.dotstore.utils.AppUtils.openApp(this@MainActivity, packageName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
        unregisterReceiver(packageReceiver)
    }
}

// Simple factory for manual DI
class GenericViewModelFactory<T : androidx.lifecycle.ViewModel>(
    private val creator: () -> T
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return creator() as T
    }
}
