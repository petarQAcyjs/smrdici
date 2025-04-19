package com.petar.smrdici.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseUser

/**
 * Komponenta za zaglavlje aplikacije.
 * 
 * @param title Naslov koji će biti prikazan.
 * @param user Prijavljeni korisnik (opciono).
 * @param navController Navigacioni kontroler.
 * @param showBackButton Da li prikazati dugme za nazad.
 * @param onMenuClick Akcija koja se izvršava prilikom klika na meni, ako je meni prikazan.
 * @param showMenu Da li prikazati dugme za meni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    user: FirebaseUser?,
    navController: NavController,
    showBackButton: Boolean = false,
    onMenuClick: () -> Unit = {},
    showMenu: Boolean = false
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Nazad"
                    )
                }
            } else if (showMenu) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Meni"
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        },
        actions = {
            // Ovde možemo dodati akcije za desnu stranu (npr. profil, podešavanja)
            Spacer(modifier = Modifier.width(48.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
} 