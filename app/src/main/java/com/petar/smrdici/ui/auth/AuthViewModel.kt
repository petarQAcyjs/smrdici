@file:Suppress("DEPRECATION")
package com.petar.smrdici.ui.auth

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    init {
        checkCurrentUser()
    }

    fun initGoogleSignIn(context: Context) {
        oneTapClient = Identity.getSignInClient(context)
        
        // Конфигурација захтева за пријаву
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId("683999597671-0l004ln7l16meoo26ogk4mvcojndnsk7.apps.googleusercontent.com") // OAuth Client ID
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()
    }

    fun beginSignIn(onSuccess: (IntentSender) -> Unit, onFailure: (Exception) -> Unit) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Користимо нови метод за започињање пријаве
                val result = oneTapClient.beginSignIn(signInRequest).await()
                onSuccess(result.pendingIntent.intentSender)
            } catch (e: Exception) {
                onFailure(e)
                _authState.value = AuthState.Error(e.message ?: "Грешка приликом покретања пријаве")
            }
        }
    }

    fun handleGoogleSignInResult(result: ActivityResult) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Извлачимо кориснички акредитив из резултата активности
                val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
                val idToken = credential.googleIdToken
                
                if (idToken != null) {
                    // Пријава на Firebase са Google токеном
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = auth.signInWithCredential(firebaseCredential).await()
                    _authState.value = AuthState.Authenticated(authResult.user!!)
                } else {
                    _authState.value = AuthState.Error("Недостаје ID токен")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Грешка приликом пријаве")
            }
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.signInWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Authenticated(result.user!!)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Грешка приликом пријаве")
            }
        }
    }

    fun createUserWithEmailAndPassword(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Authenticated(result.user!!)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Грешка приликом регистрације")
            }
        }
    }

    fun signOut() {
        android.util.Log.d("AuthViewModel", "Почетак одјављивања...")

        // Прво постављамо стање на NotAuthenticated да обезбедимо да UI реагује
        _authState.value = AuthState.NotAuthenticated
        android.util.Log.d("AuthViewModel", "Стање промењено на NotAuthenticated одмах")
        
        // Одјављујемо се из Firebase
        try {
            auth.signOut()
            android.util.Log.d("AuthViewModel", "Firebase одјава успешна")
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Грешка приликом одјаве из Firebase: ${e.message}", e)
        }
        
        // Одјављујемо се из OneTap-а
        try {
            if (::oneTapClient.isInitialized) {
                android.util.Log.d("AuthViewModel", "OneTapClient иницијализован, одјављујем се")
                oneTapClient.signOut()
                android.util.Log.d("AuthViewModel", "OneTapClient одјава успешна")
            } else {
                android.util.Log.d("AuthViewModel", "OneTapClient није иницијализован")
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "Грешка приликом Google одјаве: ${e.message}", e)
        }
    }

    private fun checkCurrentUser() {
        android.util.Log.d("AuthViewModel", "Проверавам тренутног корисника...")
        val currentUser = auth.currentUser
        if (currentUser != null) {
            android.util.Log.d("AuthViewModel", "Корисник је пријављен: ${currentUser.email}")
            _authState.value = AuthState.Authenticated(currentUser)
        } else {
            android.util.Log.d("AuthViewModel", "Није пронађен пријављени корисник")
            _authState.value = AuthState.NotAuthenticated
        }
        android.util.Log.d("AuthViewModel", "Стање постављено на: ${_authState.value}")
    }
}

sealed class AuthState {
    data object Initial : AuthState()
    data object Loading : AuthState()
    data object NotAuthenticated : AuthState()
    data class Authenticated(val user: FirebaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}