package com.openlistmobile.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import com.openlistmobile.app.ui.home.HomeScreen
import com.openlistmobile.app.ui.components.clearFocusOnTap
import com.openlistmobile.app.ui.theme.OpenListTheme
import com.openlistmobile.app.ui.sync.SyncScreen
import com.openlistmobile.app.ui.transfer.TransferScreen
import com.openlistmobile.app.utils.ShareManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareManager: ShareManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleShareIntent(intent)

        setContent {
            OpenListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().clearFocusOnTap(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300)
                            ) + fadeIn(animationSpec = tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(300))
                        }
                    ) {
                        composable("home") {
                            HomeScreen(
                                onNavigateToSync = { navController.navigate("sync") },
                                onNavigateToTransfer = { navController.navigate("transfer") }
                            )
                        }
                        composable("transfer") {
                            TransferScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("sync") {
                            SyncScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }
        if (uris.isNotEmpty()) {
            shareManager.setSharedUris(uris)
        }
    }
}
