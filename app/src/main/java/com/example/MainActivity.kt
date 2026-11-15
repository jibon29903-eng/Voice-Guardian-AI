package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.auth.attemptAutoSignIn
import com.example.core.NetworkGuardian
import com.example.core.configureAppCheck
import com.example.ui.navigation.JarvisAppRoot
import com.example.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    private lateinit var networkGuardian: NetworkGuardian

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        networkGuardian = NetworkGuardian(applicationContext)

        val credentialManager = CredentialManager.create(this)
        attemptAutoSignIn(
            context = this,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = lifecycleScope
        )

        setContent {
            JarvisTheme {
                JarvisAppRoot(
                    networkGuardian = networkGuardian,
                    initialRouteExtra = intent?.getStringExtra("open_route")
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::networkGuardian.isInitialized) {
            networkGuardian.release()
        }
    }
}
