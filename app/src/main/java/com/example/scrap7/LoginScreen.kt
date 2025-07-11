package com.example.scrap7

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    val context = LocalContext.current
    val auth = remember { Firebase.auth }
    var selectedRole by remember { mutableStateOf("rider") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Choose your role", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(16.dp))

        Row {
            RoleButton(role = "rider", selected = selectedRole == "rider") {
                selectedRole = "rider"
            }
            Spacer(Modifier.width(16.dp))
            RoleButton(role = "driver", selected = selectedRole == "driver") {
                selectedRole = "driver"
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                isLoading = true
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        val userId = it.user?.uid ?: ""
                        onLoginSuccess(userId, selectedRole)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_LONG).show()
                        isLoading = false
                    }
            },
            enabled = !isLoading
        ) {
            Text("Continue as ${selectedRole.capitalize()}")
        }
    }
}

@Composable
fun RoleButton(role: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF1E88E5) else Color.LightGray
        )
    ) {
        Text(role.capitalize())
    }
}