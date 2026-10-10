package com.example.mypasswords.screens.finger

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compose_ui.components.CText
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.CircleIconBackGroundColor
import com.example.mypasswords.ui.theme.CircleIconColor
import com.example.mypasswords.ui.theme.ShieldBlurColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor
import com.example.mypasswords.ui.theme.VaultBadge
import com.example.mypasswords.ui.theme.vaultBackdrop
import com.example.mypasswords.util.toMessage

@Composable
fun FingerPrintAuthScreen(
    viewModel: FingerPrintViewModel = hiltViewModel(),
    onSuccess: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var showRecovery by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelAuthentication() } }

    val authState by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(activity) {
        activity?.let {
            viewModel.prepareAuthentication(it)
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Success -> {
                onSuccess()
                viewModel.resetState()
            }
            is AuthState.Error -> {
                val message = state.error.toMessage(context)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (!state.isFatal) {
                    onCancelClick()
                    viewModel.resetState()
                }
            }
            else -> {}
        }
    }

    if (com.example.compose_ui.components.isVaultLandscape()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxSize()
                .vaultBackdrop()
                .systemBarsPadding()
                .displayCutoutPadding()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VaultBadge(stringResource(R.string.vault_auth_badge))
                Spacer(Modifier.height(16.dp))
                FingerprintGraphic(compact = true)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                FingerprintInstructions(authState) { showRecovery = true }
            }
        }
    } else {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .vaultBackdrop()
                    .systemBarsPadding()
                    .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VaultBadge(stringResource(R.string.vault_auth_badge))

            Spacer(modifier = Modifier.height(28.dp))

            FingerprintGraphic()

            Spacer(modifier = Modifier.height(28.dp))

            FingerprintInstructions(authState) { showRecovery = true }
        }
    }
    if (showRecovery) {
        com.example.mypasswords.screens.password.RecoveryInputDialog(
            onRecover = { code ->
                showRecovery = false
                activity?.let { viewModel.prepareRecoveryAuthentication(it, code) }
            },
            onDismiss = { showRecovery = false },
        )
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
private fun FingerprintGraphic(compact: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(if (compact) 180.dp else 272.dp),
    ) {
        val ringColor = ShieldBlurColor
        Box(
            modifier =
                Modifier.size(if (compact) 180.dp else 272.dp).drawBehind {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.8f),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
        )
        Box(
            modifier =
                Modifier.size(if (compact) 148.dp else 218.dp).drawBehind {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.6f),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
        )
        Box(
            modifier =
                Modifier.size(if (compact) 118.dp else 164.dp).drawBehind {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.4f),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(if (compact) 96.dp else 130.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(CircleIconBackGroundColor),
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Fingerprint",
                tint = CircleIconColor,
                modifier = Modifier.size(if (compact) 48.dp else 64.dp),
            )
        }
    }
}

@Composable
private fun FingerprintInstructions(authState: AuthState, onRecovery: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        if (authState is AuthState.ScanNotRecognized) {
            CText(
                text = stringResource(R.string.error_biometric_auth_failed),
                color = SubTitleTextColor,
                fontSize = 14.sp,
            )
        }
        if ((authState as? AuthState.Error)?.isFatal == true) {
            androidx.compose.material3.TextButton(onClick = { onRecovery() }) {
                androidx.compose.material3.Text(stringResource(R.string.recovery_start))
            }
        }
        CText(
            text = stringResource(id = R.string.fingerPrint_screen_title),
            color = TitleTextColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        CText(
            text = stringResource(id = R.string.fingerPrint_screen_subtitle),
            color = TitleTextColor.copy(alpha = 0.9f),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        CText(
            text = stringResource(id = R.string.fingerPrint_screen_third_title),
            color = SubTitleTextColor,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}
