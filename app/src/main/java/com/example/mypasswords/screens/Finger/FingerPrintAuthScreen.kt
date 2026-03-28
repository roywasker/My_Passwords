package com.example.mypasswords.screens.finger

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
import com.example.compose_ui.components.CFloatingIcon
import com.example.compose_ui.components.CText
import com.example.mypasswords.R
import com.example.mypasswords.ui.theme.BackgroundColor
import com.example.mypasswords.ui.theme.ButtonColor
import com.example.mypasswords.ui.theme.CircleIconBackGroundColor
import com.example.mypasswords.ui.theme.CircleIconBorderColor
import com.example.mypasswords.ui.theme.CircleIconColor
import com.example.mypasswords.ui.theme.ShieldBlurColor
import com.example.mypasswords.ui.theme.SubTitleTextColor
import com.example.mypasswords.ui.theme.TitleTextColor
import com.example.mypasswords.util.toMessage

@Composable
fun FingerPrintAuthScreen(
    viewModel: FingerPrintViewModel = hiltViewModel(),
    onSuccess: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp)) {
            CFloatingIcon(
                icon = Icons.Default.Security,
                iconSize = 20.dp,
                size = 40.dp,
                iconBackGroundColor = CircleIconBackGroundColor,
                borderColor = CircleIconBorderColor,
                borderWidth = 1.dp,
                iconTint = CircleIconColor,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
            val ringColor = ShieldBlurColor
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .drawBehind {
                        drawCircle(
                            color = ringColor.copy(alpha = 0.8f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .drawBehind {
                        drawCircle(
                            color = ringColor.copy(alpha = 0.6f),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .drawBehind {
                        drawCircle(
                            color = ringColor.copy(alpha = 0.4f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(ButtonColor)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Fingerprint",
                    tint = Color.White,
                    modifier = Modifier.size(70.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            CText(
                text = stringResource(id = R.string.fingerPrint_screen_title),
                color = TitleTextColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            CText(
                text = stringResource(id = R.string.fingerPrint_screen_subtitle),
                color = TitleTextColor.copy(alpha = 0.9f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            CText(
                text = stringResource(id = R.string.fingerPrint_screen_third_title),
                color = SubTitleTextColor,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
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
