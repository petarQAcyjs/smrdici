package com.petar.smrdici.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.petar.smrdici.ui.auth.AuthState
import com.petar.smrdici.ui.auth.AuthViewModel
import com.petar.smrdici.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    
    // Inicijalizacija Google Sign-In
    LaunchedEffect(Unit) {
        authViewModel.initGoogleSignIn(context)
    }
    
    // Launcher za Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authViewModel.handleGoogleSignInResult(result)
    }
    
    // Provera stanja autentikacije
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSignUp) "Registracija" else "Prijava") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Email polje
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            
            // Password polje
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Lozinka") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            
            // Dugme za prijavu/registraciju
            Button(
                onClick = {
                    if (isSignUp) {
                        authViewModel.createUserWithEmailAndPassword(email, password)
                    } else {
                        authViewModel.signInWithEmailAndPassword(email, password)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(if (isSignUp) "Registruj se" else "Prijavi se")
            }
            
            // Dugme za Google prijavu
            Button(
                onClick = {
                    googleSignInLauncher.launch(authViewModel.getGoogleSignInIntent())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Prijavi se pomoću Google naloga")
            }
            
            // Prebacivanje između prijave i registracije
            TextButton(
                onClick = { isSignUp = !isSignUp },
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(if (isSignUp) "Već imaš nalog? Prijavi se" else "Nemaš nalog? Registruj se")
            }
            
            // Prikaz greške
            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // Prikaz učitavanja
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
} 