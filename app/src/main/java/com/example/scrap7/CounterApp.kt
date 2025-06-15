package com.example.scrap7

import android.graphics.drawable.Icon
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.scrap7.ui.theme.DisabledGray
import com.example.scrap7.ui.theme.DisabledTextGray
import com.example.scrap7.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounterScreen(
    viewModel: CounterViewModel, // = viewModel(), // injects the ViewModel
    onNavigateToDetails: () -> Unit
) {
    val red = RedPrimary
    val navController = rememberNavController()

    val count by viewModel.count.collectAsState()

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
        }
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