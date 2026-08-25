package com.hinnka.mycamera.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hinnka.mycamera.R
import com.hinnka.mycamera.model.CameraExperience

/** First-run, persistent chooser for the two deliberately different cameras. */
@Composable
fun CameraExperienceWizard(
    onExperienceSelected: (CameraExperience) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.camera_experience_wizard_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = stringResource(R.string.camera_experience_wizard_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 440.dp),
            )
            Spacer(Modifier.padding(top = 28.dp))
            CameraExperienceOption(
                title = stringResource(R.string.camera_experience_beginner),
                description = stringResource(R.string.camera_experience_beginner_description),
                action = stringResource(R.string.camera_experience_choose_beginner),
                emphasized = true,
                onClick = { onExperienceSelected(CameraExperience.BEGINNER) },
            )
            Spacer(Modifier.padding(top = 14.dp))
            CameraExperienceOption(
                title = stringResource(R.string.camera_experience_pro),
                description = stringResource(R.string.camera_experience_pro_description),
                action = stringResource(R.string.camera_experience_choose_pro),
                emphasized = false,
                onClick = { onExperienceSelected(CameraExperience.PRO) },
            )
        }
    }
}

@Composable
private fun CameraExperienceOption(
    title: String,
    description: String,
    action: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.padding(top = 6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 18.dp))
            Button(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                colors = if (emphasized) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                },
            ) {
                Text(action)
            }
        }
    }
}

/** Small, non-modal way to return from the full Pro surface to the simple camera. */
@Composable
fun ProExperienceSwitcher(
    onSwitchToBeginner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .wrapContentWidth()
            .clickable(onClick = onSwitchToBeginner),
        color = Color.Black.copy(alpha = 0.56f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = stringResource(R.string.camera_experience_beginner_switch),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
