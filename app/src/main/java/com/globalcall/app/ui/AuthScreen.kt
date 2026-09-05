package com.globalcall.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.globalcall.app.R
import com.globalcall.app.data.PhoneDirectory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.TimeUnit

private fun phoneAuthMessage(t: Throwable): String {
    val raw = t.message.orEmpty()
    return when {
        raw.contains("BILLING_NOT_ENABLED", ignoreCase = true) ->
            "Phone OTP is not active for this Firebase project yet. Enable Firebase Blaze billing, then try again."
        raw.contains("INVALID_PHONE_NUMBER", ignoreCase = true) ||
            raw.contains("invalid phone", ignoreCase = true) ->
            "Enter the number in international format. For Saudi Arabia use +9665XXXXXXXX (do not include the local 0)."
        raw.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) ||
            raw.contains("quota", ignoreCase = true) ->
            "Too many OTP requests. Please wait and try again."
        raw.contains("NETWORK", ignoreCase = true) ->
            "Check your internet connection and try again."
        else -> raw.takeIf { it.isNotBlank() } ?: "Phone verification failed"
    }
}

private fun googleAuthMessage(t: Throwable): String {
    val raw = t.message.orEmpty()
    return when {
        raw.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
            raw.contains("provider", ignoreCase = true) && raw.contains("disabled", ignoreCase = true) ->
            "Google sign-in is not enabled in Firebase Authentication yet. Enable the Google provider and try again."
        raw.contains("network", ignoreCase = true) ->
            "Check your internet connection and try Google sign-in again."
        else -> raw.takeIf { it.isNotBlank() } ?: "Google sign-in could not be completed"
    }
}

@Composable
fun AuthScreen(auth: FirebaseAuth, onGuest: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? Activity
    val db = remember(auth) { FirebaseFirestore.getInstance(auth.app) }
    val scope = rememberCoroutineScope()
    var phoneMode by remember { mutableStateOf(true) }
    var createMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    suspend fun persistSignedInUser(phoneNumber: String? = auth.currentUser?.phoneNumber) {
        val user = requireNotNull(auth.currentUser)
        val normalized = phoneNumber?.let { PhoneDirectory.normalize(it) }
        val fallbackName = user.displayName?.takeIf { it.isNotBlank() }
            ?: normalized?.let { "User ${PhoneDirectory.last4(it)}" }
            ?: user.email?.substringBefore('@')
            ?: "GlobalCall user"
        if (user.displayName.isNullOrBlank()) {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(fallbackName).build()).await()
        }
        db.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to fallbackName,
                "email" to user.email.orEmpty().lowercase(Locale.ROOT),
                "phoneLast4" to (normalized?.let(PhoneDirectory::last4) ?: ""),
                "phoneVerified" to (normalized != null),
                "bio" to "",
                "locale" to Locale.getDefault().toLanguageTag(),
                "online" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge()
        ).await()
        if (normalized != null) {
            db.collection("phoneDirectory").document(PhoneDirectory.key(normalized)).set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to fallbackName,
                    "photoUrl" to (user.photoUrl?.toString() ?: ""),
                    "phoneLast4" to PhoneDirectory.last4(normalized),
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge()
            ).await()
        }
    }

    val googleClient = remember(context, auth) {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, options)
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.data == null) return@rememberLauncherForActivityResult
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                error = if (e.statusCode == 10) {
                    "Google sign-in configuration does not match this app signing key. Check the Firebase OAuth client."
                } else {
                    "Google sign-in failed (${e.statusCode}). Please try again."
                }
            }
            null
        }
        if (account?.idToken != null) {
            scope.launch {
                loading = true
                error = null
                info = null
                runCatching {
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential).await()
                    persistSignedInUser(null)
                }.onSuccess {
                    info = "Google account connected"
                }.onFailure {
                    error = googleAuthMessage(it)
                }
                loading = false
            }
        }
    }

    fun completePhoneCredential(credential: PhoneAuthCredential) {
        scope.launch {
            loading = true
            error = null
            runCatching {
                auth.signInWithCredential(credential).await()
                persistSignedInUser()
            }.onFailure { error = phoneAuthMessage(it) }
            loading = false
        }
    }

    val callbacks = remember(auth) {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                completePhoneCredential(credential)
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                loading = false
                error = phoneAuthMessage(exception)
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = id
                loading = false
                info = "Verification code sent"
            }
        }
    }

    fun sendOtp() {
        if (activity == null) {
            error = "Unable to start phone verification on this device"
            return
        }
        val normalized = runCatching { PhoneDirectory.normalize(phone) }
            .getOrElse { error = it.message; return }
        loading = true
        error = null
        info = null
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalized)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("GlobalCall", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Create your account and connect instantly", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        error = null
                        info = null
                        googleLauncher.launch(googleClient.signInIntent)
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", fontWeight = FontWeight.Bold)
                }
                Text(
                    "New users are registered automatically with their Google name and email.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 7.dp)
                )

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(Modifier.weight(1f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = phoneMode,
                        onClick = { phoneMode = true; error = null; info = null },
                        label = { Text("Phone") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !phoneMode,
                        onClick = { phoneMode = false; error = null; info = null },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (phoneMode) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(18) },
                        label = { Text("Phone with country code") },
                        placeholder = { Text("+8801... / +9665...") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Saudi example: 05XXXXXXXX → +9665XXXXXXXX",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    if (verificationId != null) {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-digit verification code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = !createMode,
                            onClick = { createMode = false; error = null },
                            label = { Text("Sign in") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = createMode,
                            onClick = { createMode = true; error = null },
                            label = { Text("Create account") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (createMode) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(50) },
                            label = { Text("Display name") },
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
                        label = { Text("Email") },
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
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

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
                        if (phoneMode) {
                            val id = verificationId
                            if (id == null) sendOtp()
                            else completePhoneCredential(PhoneAuthProvider.getCredential(id, otp))
                        } else {
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    if (createMode) {
                                        require(displayName.trim().length >= 2) { "Enter your name" }
                                        val result = auth.createUserWithEmailAndPassword(email, password).await()
                                        val user = requireNotNull(result.user)
                                        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()).await()
                                        persistSignedInUser(null)
                                    } else {
                                        auth.signInWithEmailAndPassword(email, password).await()
                                        persistSignedInUser(null)
                                    }
                                } catch (t: Throwable) {
                                    error = t.message ?: "Unable to continue"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    },
                    enabled = !loading && if (phoneMode) {
                        phone.isNotBlank() && (verificationId == null || otp.length == 6)
                    } else {
                        email.isNotBlank() && password.length >= 6 && (!createMode || displayName.trim().length >= 2)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text(
                        if (phoneMode) {
                            if (verificationId == null) "Send OTP" else "Verify & continue"
                        } else if (createMode) {
                            "Create account"
                        } else {
                            "Sign in"
                        }
                    )
                }

                if (phoneMode && verificationId != null) {
                    TextButton(onClick = { verificationId = null; otp = ""; info = null }) {
                        Text("Change number")
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedButton(
                    onClick = onGuest,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Videocam, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Continue to instant calling")
                }
            }
        }
    }
}
