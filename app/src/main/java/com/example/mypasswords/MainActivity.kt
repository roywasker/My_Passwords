package com.example.mypasswords

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.auth.AuthRepository
import com.example.mypasswords.navigation.Routes
import com.example.mypasswords.screens.finger.FingerPrintAuthScreen
import com.example.mypasswords.screens.password.AddPasswordScreen
import com.example.mypasswords.screens.password.PasswordScreen
import com.example.mypasswords.screens.welcome.WelcomeScreen
import com.example.mypasswords.ui.theme.MyPasswordsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Security: Prevent screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        enableEdgeToEdge()
        
        setContent {
            MyPasswordsTheme {
                val navController = rememberNavController()
                val isAuthenticated by authRepository.isAuthenticated.collectAsStateWithLifecycle()

                // Security: Navigation guard
                LaunchedEffect(isAuthenticated) {
                    if (!isAuthenticated && navController.currentDestination?.route == Routes.PASSWORD) {
                        navController.navigate(Routes.WELCOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                NavHost(
                    navController = navController, 
                    startDestination = Routes.WELCOME
                ) {
                    composable(Routes.WELCOME) {
                        WelcomeScreen(onClick = { navController.navigate(Routes.FINGERPRINT_AUTH) })
                    }
                    
                    composable(Routes.FINGERPRINT_AUTH) {
                        FingerPrintAuthScreen(
                            onSuccess = {
                                navController.navigate(Routes.PASSWORD) {
                                    popUpTo(Routes.WELCOME) { inclusive = false }
                                }
                            },
                            onCancelClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                    
                    composable(Routes.PASSWORD) {
                        if (isAuthenticated) {
                            PasswordScreen(
                                onBackClick = {
                                    navController.navigate(Routes.WELCOME) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onAddClick = {
                                    navController.navigate(Routes.ADD_PASSWORD)
                                }
                            )
                        } else {
                            // This case is handled by the LaunchedEffect guard, 
                            // but adding a fallback for safety.
                            LaunchedEffect(Unit) {
                                navController.navigate(Routes.WELCOME) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    }

                    composable(Routes.ADD_PASSWORD) {
                        if (isAuthenticated) {
                            AddPasswordScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Security: Lock the app when it goes to background, but not on rotation
        if (!isChangingConfigurations) {
            authRepository.setAuthenticated(false)
        }
    }
}
