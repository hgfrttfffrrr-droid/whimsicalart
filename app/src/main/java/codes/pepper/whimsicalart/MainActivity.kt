package codes.pepper.whimsicalart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import codes.pepper.whimsicalart.core.common.PreferencesManager
import codes.pepper.whimsicalart.navigation.AppNavGraph
import codes.pepper.whimsicalart.navigation.ShareEntry
import codes.pepper.whimsicalart.navigation.Routes
import codes.pepper.whimsicalart.ui.theme.WhimsicalArtTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var pendingShareUri by mutableStateOf<Uri?>(null)
    private var coldShareUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        recordAppLaunch()

        enableEdgeToEdge()

        coldShareUri = ShareEntry.sharedImageUri(intent)
        pendingShareUri = coldShareUri

        val sessionKey = ShareEntry.sessionKey(coldShareUri)

        setContent {
            key(sessionKey) {
                WhimsicalArtTheme {
                    AppUiScale {
                        val navController = rememberNavController()

                        val startDestination = androidx.compose.runtime.remember(Unit) {
                            ShareEntry.startDestination(
                                coldShareUri,
                                preferencesManager.isFirstLaunch
                            )
                        }

                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination
                        )

                        LaunchedEffect(pendingShareUri) {
                            val uri = pendingShareUri ?: return@LaunchedEffect
                            pendingShareUri = null

                            if (coldShareUri == uri) {
                                if (navController.currentDestination?.route != Routes.EDITOR) {
                                    navController.navigate(Routes.editor(uri)) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                navController.navigate(Routes.editor(uri)) {
                                    launchSingleTop = true
                                }
                            }

                            coldShareUri = null
                        }
                    }
                }
            }
        }
    }

    private fun recordAppLaunch() {
        val prefs = getSharedPreferences(
            "whimsicalart_panel_stats",
            MODE_PRIVATE
        )

        val launches = prefs.getLong("launch_count", 0L) + 1L

        prefs.edit()
            .putLong("launch_count", launches)
            .putLong("last_launch", System.currentTimeMillis())
            .apply()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShareUri = ShareEntry.sharedImageUri(intent)
    }
}
