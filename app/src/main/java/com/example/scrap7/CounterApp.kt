package com.example.scrap7

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scrap7.ui.theme.DisabledGray
import com.example.scrap7.ui.theme.DisabledTextGray
import com.example.scrap7.ui.theme.RedPrimary

//private const val CHANNEL_ID = "scrap7_channel"
//private const val NOTIFICATION_ID = 1001

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    viewModel: CounterViewModel, // = viewModel(), // injects the ViewModel
    onNavigateToDetails: () -> Unit
) {
    RequestNotificationPermission()

    val red = RedPrimary
    val navController = rememberNavController()

    val count by viewModel.count.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    // Check for reset flag
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("scrap7_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("reset_requested", false)) {
            viewModel.reset() // <- This clears the counter and history
            prefs.edit().putBoolean("reset_requested", false).apply()

            snackbarHostState.showSnackbar("Counter reset from notification")
        }
    }

    /*
    NavHost(navController = navController, startDestination = "counter") {
        composable("counter") {
            CounterScreen(
                onNavigateToDetails = {
                    navController.navigate("details")
                }
            )
        }

        composable("details") {
            DetailsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }

     */

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Counter App",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                /*
                navigationIcon = {
                    IconButton(onClick = { /* Handle back logic */navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },

                 */
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = red,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        )
            //var count by remember { mutableStateOf(0) }
            {
                Text("Count: $count", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.increment() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Increase")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.reset() },
                    enabled = count != 0,
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White,
                        disabledContentColor = DisabledTextGray,
                        disabledContainerColor = DisabledGray,
                        containerColor = RedPrimary,
                    )
                ) {
                    Text("Reset")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = onNavigateToDetails) {
                    Text("Go to Details")
                }

                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return@Button
                        }
                    }

                    // Intent to open the app
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("FROM_NOTIFICATION", true)
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                    )

                    // Intent for the reset button
                    val resetIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = "com.example.scrap7.ACTION_RESET"
                    }
                    val resetPendingIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        resetIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )

                    // Build the notification
                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_ready_version)
                        .setContentTitle("Scrap7 Notification")
                        .setContentText("Count updated! Tap to open.")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .addAction(
                            R.drawable.ic_notification_ready_version,  // Icon for button
                            "Reset",                                   // Button text
                            resetPendingIntent                         // What happens when tapped
                        )
                        .setAutoCancel(true)
                        .build()

                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

                    Toast.makeText(context, "Sending notification...", Toast.LENGTH_SHORT).show()
                    Log.d("Scrap7", "Send Notification button clicked")
                }) {
                    Text("Send Notification")
                }
            }
        }
}

@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (!isGranted) {
                    Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        )
        LaunchedEffect(Unit){
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/*
@Preview
@Composable
fun CounterScreenPreview() {
    CounterScreen {  }
}

 */