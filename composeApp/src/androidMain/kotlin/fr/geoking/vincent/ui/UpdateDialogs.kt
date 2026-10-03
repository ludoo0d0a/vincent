package fr.geoking.vincent.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import vincent.composeapp.generated.resources.Res
import vincent.composeapp.generated.resources.action_update
import vincent.composeapp.generated.resources.cellar_action_cancel
import vincent.composeapp.generated.resources.update_available_message
import vincent.composeapp.generated.resources.update_available_title
import vincent.composeapp.generated.resources.update_check_error_title
import vincent.composeapp.generated.resources.update_check_ok
import vincent.composeapp.generated.resources.update_check_up_to_date

@Composable
fun UpdateAvailableDialog(
    onCancel: () -> Unit,
    onUpdate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.update_available_title)) },
        text = { Text(stringResource(Res.string.update_available_message)) },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(Res.string.action_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.cellar_action_cancel))
            }
        },
    )
}

/** Feedback after Settings → Check for updates when already up to date or on error. */
@Composable
fun UpdateCheckFeedbackDialog(
    isError: Boolean,
    errorMessage: String = "",
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isError) {
                    stringResource(Res.string.update_check_error_title)
                } else {
                    stringResource(Res.string.update_check_up_to_date)
                },
            )
        },
        text = if (isError && errorMessage.isNotEmpty()) {
            { Text(errorMessage) }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.update_check_ok))
            }
        },
    )
}
