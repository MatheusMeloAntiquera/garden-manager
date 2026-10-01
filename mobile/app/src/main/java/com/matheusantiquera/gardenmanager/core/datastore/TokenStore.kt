package com.matheusantiquera.gardenmanager.core.datastore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AuthTokens(val accessToken: String, val refreshToken: String)

/** Guarda o par de tokens da sessão. */
interface TokenStore {
    /** Tokens atuais, ou `null` quando não há sessão (ou quando não puderam ser lidos). */
    val tokens: Flow<AuthTokens?>

    suspend fun save(tokens: AuthTokens)

    suspend fun clear()
}

/** Criptografa e descriptografa strings com AES-256-GCM (Tink), com a chave mestra no Android Keystore. */
@Singleton
class TokenCipher @Inject constructor(@ApplicationContext private val context: Context) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    fun encrypt(plain: String): String =
        Base64.encodeToString(aead.encrypt(plain.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA), Base64.NO_WRAP)

    fun decrypt(encrypted: String): String =
        String(aead.decrypt(Base64.decode(encrypted, Base64.NO_WRAP), ASSOCIATED_DATA), Charsets.UTF_8)

    private companion object {
        const val KEYSET_NAME = "garden_token_keyset"
        const val KEYSET_PREFS_FILE = "garden_token_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://garden_token_master_key"
        val ASSOCIATED_DATA = "garden-manager-tokens".toByteArray(Charsets.UTF_8)
    }
}

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "tokens")

@Singleton
class EncryptedTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: TokenCipher,
) : TokenStore {

    override val tokens: Flow<AuthTokens?> = context.tokenDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val access = prefs[ACCESS_TOKEN]
            val refresh = prefs[REFRESH_TOKEN]
            if (access == null || refresh == null) {
                null
            } else {
                // Se a chave do Keystore mudou (ex.: backup restaurado), os tokens ficam ilegíveis: sem sessão.
                runCatching { AuthTokens(cipher.decrypt(access), cipher.decrypt(refresh)) }.getOrNull()
            }
        }
        .distinctUntilChanged()

    override suspend fun save(tokens: AuthTokens) {
        context.tokenDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = cipher.encrypt(tokens.accessToken)
            prefs[REFRESH_TOKEN] = cipher.encrypt(tokens.refreshToken)
        }
    }

    override suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
