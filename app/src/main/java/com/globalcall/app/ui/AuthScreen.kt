package com.globalcall.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.globalcall.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@Composable
fun AuthScreen(auth: FirebaseAuth) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    var createMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("GlobalCall", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    androidx.compose.ui.res.stringResource(R.string.welcome),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = !createMode,
                        onClick = { createMode = false; error = null },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.sign_in)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = createMode,
                        onClick = { createMode = true; error = null },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.create_account)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (createMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(50) },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.display_name)) },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.email)) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                info?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            info = null
                            try {
                                if (createMode) {
                                    require(displayName.trim().length >= 2) { "Enter your name" }
                                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                                    val user = requireNotNull(result.user)
                                    user.updateProfile(
                                        UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()
                                    ).await()
                                    db.collection("users").document(user.uid).set(
                                        mapOf(
                                            "uid" to user.uid,
                                            "displayName" to displayName.trim(),
                                            "email" to email.lowercase(Locale.ROOT),
                                            "bio" to "",
                                            "locale" to Locale.getDefault().toLanguageTag(),
                                            "online" to true,
                                            "createdAt" to FieldValue.serverTimestamp(),
                                            "lastSeen" to FieldValue.serverTimestamp()
                                        )
                                    ).await()
                                } else {
                                    auth.signInWithEmailAndPassword(email, password).await()
                                }
                            } catch (t: Throwable) {
                                error = t.message ?: "Unable to continue"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading && email.isNotBlank() && password.length >= 6 &&
                        (!createMode || displayName.trim().length >= 2),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (createMode) androidx.compose.ui.res.stringResource(R.string.create_account)
                            else androidx.compose.ui.res.stringResource(R.string.sign_in)
                        )
                    }
                }

                if (!createMode) {
                    TextButton(onClick = {
                        if (email.isBlank()) {
                            error = "Enter your email first"
                        } else {
                            scope.launch {
                                runCatching { auth.sendPasswordResetEmail(email).await() }
                                    .onSuccess { info = "Password reset email sent" }
                                    .onFailure { error = it.message }
                            }
                        }
                    }) {
                        Text(androidx.compose.ui.res.stringResource(R.string.forgot_password))
                    }
                }
            }
        }
    }
}
