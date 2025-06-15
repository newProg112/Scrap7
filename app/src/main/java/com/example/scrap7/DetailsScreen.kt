package com.example.scrap7

import android.content.Intent
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scrap7.ui.theme.RedPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    viewModel: CounterViewModel,// = viewModel(),/////// check here!!!
    onBack: () -> Unit
) {
    val count by viewModel.count.collectAsState()
    val history by viewModel.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details Screen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RedPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        /*
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("Current count is $count", fontSize = 24.sp)
        }

         */
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Current Count: $count", style = MaterialTheme.typography.headlineMedium)

            Button(onClick = viewModel::increment) {
                Text("Increment Again")
            }

            Button(
                onClick = viewModel::reset,
                enabled = count > 0,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.Gray,
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Reset")
            }

            val context = LocalContext.current

            Button(onClick = {
                val file = viewModel.saveHistoryCsvToFile(context)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider", // <- must match manifest
                    file
                )

                val csvText = history.joinToString(separator = "\n") { it }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, csvText)
                    putExtra(Intent.EXTRA_SUBJECT, "Exported Data")
                }

                val chooser = Intent.createChooser(intent, "Share CSV via...")

                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(chooser)
                } else {
                    Toast.makeText(context, "No apps available to share the file", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Save & Share CSV")
            }

            Spacer(Modifier.height(16.dp))
            Text("History:", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(history.reversed()) { entry ->
                    Text("• $entry")
                }
            }
        }
    }
}