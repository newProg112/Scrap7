package com.example.scrap7

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.os.postDelayed
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.scrap7.ui.theme.Scrap7Theme
import com.google.android.libraries.places.api.Places
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import java.util.logging.Handler

class MainActivity : ComponentActivity()/*FragmentActivity() */ {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the Places SDK
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyA3vVBo46hVzhCKM-LDK_4KMEhfsFQeRwI")
        }

        Log.d("FirebaseTest", "FirebaseApp initialized: ${FirebaseApp.getApps(this).isNotEmpty()}")

        Log.d("FirebaseTest", "Attempting test write to Firebase")


        android.os.Handler(Looper.getMainLooper()).postDelayed({
            Log.d("FirebaseTest", "Attempting delayed write to Firebase")

            FirebaseDatabase.getInstance()
                .getReference("testWrite")
                .setValue("Hello Firebase!")
                .addOnSuccessListener {
                    Log.d("FirebaseTest", "Test write success")
                }
                .addOnFailureListener {
                    Log.e("FirebaseTest", "Test write failed", it)
                }
        }, 1000)

        setContent {
            Scrap7Theme {
                MyApp()
            }
        }
    }
}

        /*
        // Call biometric prompt before showing the UI
        if (BiometricHelper.isAvailable(this)) {
            BiometricHelper(
                context = this,
                activity = this@MainActivity,
                onSuccess = {
                    // Biometric success - show the UI
                    createNotificationChannel(this)
                    setContent {
                        Scrap7Theme {
                            MyApp()
                        }
                    }
                },
                onFailure = {
                    // Biometric failed - close the app or show locked state
                    finish()
                }
            ).authenticate()
        } else {
            // No biometric available - just load app
            setContent {
                Scrap7Theme {
                    MyApp()
                }
            }
        }
    }
}

         */

/*
@Composable
fun MyApp() {
    val context = LocalContext.current
    val loginStatus by LoginPreferences.getLoginStatus(context).collectAsState(initial = false)
    val startDestination = if (loginStatus) "counter" else "login"
    val navController = rememberNavController()

    // Create ViewModel once here and pass it down
    val viewModel: CounterViewModel = viewModel()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreenCounter(onLoginSuccess = {
                navController.navigate("counter") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }

        composable("counter") {
            CounterScreen(
                viewModel = viewModel,
                onNavigateToDetails = {
                    navController.navigate("details")
                }
            )
        }

        composable("details") {
            DetailsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

 */

@Composable
fun MyApp() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "login") {
        composable("login") {
            LoginScreen { userId, role ->
                navController.navigate("map/$userId/$role")
            }
        }

        composable(
            "map/{userId}/{role}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val role = backStackEntry.arguments?.getString("role") ?: "rider"
            MapScreen(userId = userId, role = role, navController = navController)
        }

        composable(
            "chat/{tripId}/{userId}",
            arguments = listOf(
                navArgument("tripId") { type = NavType.StringType },
                navArgument("userId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ChatScreen(tripId = tripId, userId = userId)
        }

    }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Default Channel"
        val descriptionText = "Used for Scrap7 basic notifications"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

/*
@Preview(showBackground = true)
@Composable
fun CounterAppPreview() {
    Scrap7Theme {
        MyApp()
    }
}

 */