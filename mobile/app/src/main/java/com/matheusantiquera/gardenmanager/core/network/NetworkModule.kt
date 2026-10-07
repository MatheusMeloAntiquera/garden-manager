package com.matheusantiquera.gardenmanager.core.network

import com.matheusantiquera.gardenmanager.BuildConfig
import com.matheusantiquera.gardenmanager.data.auth.AuthApi
import com.matheusantiquera.gardenmanager.data.auth.UserApi
import com.matheusantiquera.gardenmanager.data.environment.EnvironmentApi
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceApi
import com.matheusantiquera.gardenmanager.data.plant.PlantApi
import com.matheusantiquera.gardenmanager.data.species.SpeciesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val PLAIN = "plain"
private const val AUTHENTICATED = "authenticated"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC: só método, URL e status. Nunca logar corpo, que tem senha e tokens.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    /** Cliente sem token: rotas de autenticação, inclusive a renovação feita pelo [TokenAuthenticator]. */
    @Provides
    @Singleton
    @Named(PLAIN)
    fun providePlainClient(): OkHttpClient = baseClient().build()

    /** Cliente das rotas protegidas: envia o token e renova a sessão quando recebe 401. */
    @Provides
    @Singleton
    @Named(AUTHENTICATED)
    fun provideAuthenticatedClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = baseClient()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(@Named(PLAIN) client: OkHttpClient): AuthApi =
        retrofit(client).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(@Named(AUTHENTICATED) client: OkHttpClient): UserApi =
        retrofit(client).create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideEnvironmentApi(@Named(AUTHENTICATED) client: OkHttpClient): EnvironmentApi =
        retrofit(client).create(EnvironmentApi::class.java)

    @Provides
    @Singleton
    fun providePlantApi(@Named(AUTHENTICATED) client: OkHttpClient): PlantApi =
        retrofit(client).create(PlantApi::class.java)

    @Provides
    @Singleton
    fun provideSpeciesApi(@Named(AUTHENTICATED) client: OkHttpClient): SpeciesApi =
        retrofit(client).create(SpeciesApi::class.java)

    @Provides
    @Singleton
    fun provideMaintenanceApi(@Named(AUTHENTICATED) client: OkHttpClient): MaintenanceApi =
        retrofit(client).create(MaintenanceApi::class.java)
}
