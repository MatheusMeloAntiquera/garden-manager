package com.matheusantiquera.gardenmanager.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matheusantiquera.gardenmanager.R
import com.matheusantiquera.gardenmanager.core.designsystem.BannerKind
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTextField
import com.matheusantiquera.gardenmanager.core.designsystem.GardenTheme
import com.matheusantiquera.gardenmanager.core.designsystem.MessageBanner
import com.matheusantiquera.gardenmanager.core.designsystem.PrimaryButton
import com.matheusantiquera.gardenmanager.core.designsystem.RuleRow
import com.matheusantiquera.gardenmanager.core.ui.DateMaskTransformation
import com.matheusantiquera.gardenmanager.core.ui.asString

@Composable
fun SignupScreen(
    onBack: () -> Unit,
    onAccountCreatedWithoutLogin: () -> Unit,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignupEvent.AccountCreatedWithoutLogin -> onAccountCreatedWithoutLogin()
            }
        }
    }

    SignupContent(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onBirthDateChange = viewModel::onBirthDateChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::onSubmit,
    )
}

@Composable
internal fun SignupContent(
    state: SignupUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.signup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.signup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(28.dp))

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    state.error?.let { MessageBanner(text = it.asString(), kind = BannerKind.Error) }

                    GardenTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        label = stringResource(R.string.field_name),
                        placeholder = stringResource(R.string.field_name_placeholder),
                        errorText = state.nameError?.asString(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    )
                    GardenTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = stringResource(R.string.field_email),
                        placeholder = stringResource(R.string.field_email_placeholder),
                        errorText = state.emailError?.asString(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    GardenTextField(
                        value = state.birthDateDigits,
                        onValueChange = onBirthDateChange,
                        label = stringResource(R.string.field_birth_date),
                        placeholder = stringResource(R.string.field_birth_date_placeholder),
                        errorText = state.birthDateError?.asString(),
                        visualTransformation = DateMaskTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GardenTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = stringResource(R.string.field_password),
                            placeholder = stringResource(R.string.field_password_placeholder_signup),
                            errorText = state.passwordError?.asString(),
                            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            trailingContent = {
                                IconButton(onClick = onTogglePasswordVisibility) {
                                    Icon(
                                        painter = painterResource(if (state.passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                                        contentDescription = stringResource(
                                            if (state.passwordVisible) R.string.action_hide_password else R.string.action_show_password,
                                        ),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                        Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RuleRow(text = stringResource(R.string.password_rule_length), met = state.passwordRules.hasMinLength)
                            RuleRow(text = stringResource(R.string.password_rule_digit), met = state.passwordRules.hasDigit)
                            RuleRow(text = stringResource(R.string.password_rule_special), met = state.passwordRules.hasSpecial)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                PrimaryButton(
                    text = stringResource(R.string.signup_submit),
                    onClick = onSubmit,
                    loading = state.isLoading,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.signup_has_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.signup_login), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SignupContentPreview() {
    GardenTheme(darkTheme = false) {
        SignupContent(
            state = SignupUiState(name = "Ana Ribeiro", email = "ana@email.com", birthDateDigits = "1403", password = "abcdefgh1"),
            onBack = {},
            onNameChange = {},
            onEmailChange = {},
            onBirthDateChange = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmit = {},
        )
    }
}
