package com.example.alist.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var serverUrl by remember { mutableStateOf("https://al.chirmyram.com") }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("123456") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenList Test") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.testLoginAndFetch(serverUrl, username, password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login & Fetch Root")
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is UiState.Idle -> Text("Enter details and press button.")
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Success -> Text("Result:\n${state.result}", color = MaterialTheme.colorScheme.primary)
                is UiState.Error -> Text("Error:\n${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}