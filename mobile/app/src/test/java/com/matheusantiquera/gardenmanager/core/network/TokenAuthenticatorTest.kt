package com.matheusantiquera.gardenmanager.core.network

import com.matheusantiquera.gardenmanager.core.datastore.AuthTokens
import com.matheusantiquera.gardenmanager.core.datastore.TokenStore
import com.matheusantiquera.gardenmanager.core.session.SessionManager
import com.matheusantiquera.gardenmanager.core.session.SessionState
import com.matheusantiquera.gardenmanager.data.auth.AuthApi
import com.matheusantiquera.gardenmanager.data.auth.UserApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TokenAuthenticatorTest {

    private class FakeTokenStore(initial: AuthTokens?) : TokenStore {
        private val state = MutableStateFlow(initial)
        override val tokens: Flow<AuthTokens?> = state
        override suspend fun save(tokens: AuthTokens) { state.value = tokens }
        override suspend fun clear() { state.value = null }
        val current: AuthTokens? get() = state.value
    }

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var tokenStore: FakeTokenStore
    private lateinit var sessionManager: SessionManager
    private lateinit var userApi: UserApi

    private fun tokensJson(access: String, refresh: String) =
        """{"access_token":"$access","refresh_token":"$refresh","token_type":"Bearer","expires_in":900}"""

    private val userJson =
        """{"id":"1","name":"Ana","email":"ana@email.com","birth_date":"1994-03-14","active":true,"created_at":"2026-01-01T00:00:00Z"}"""

    private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/api/v1/"))
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        tokenStore = FakeTokenStore(AuthTokens(accessToken = "old-access", refreshToken = "old-refresh"))
        sessionManager = SessionManager(tokenStore, scope)

        val authApi = retrofit(OkHttpClient()).create(AuthApi::class.java)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, authApi, sessionManager))
            .build()
        userApi = retrofit(client).create(UserApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
        scope.cancel()
    }

    @Test
    fun `renova os tokens e repete a requisicao quando recebe 401`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"token de acesso inválido ou expirado"}""").build())
        server.enqueue(MockResponse.Builder().code(200).body(tokensJson("new-access", "new-refresh")).build())
        server.enqueue(MockResponse.Builder().code(200).body(userJson).build())

        val user = userApi.me()

        assertEquals("Ana", user.name)
        assertEquals(AuthTokens("new-access", "new-refresh"), tokenStore.current)

        assertEquals("Bearer old-access", server.takeRequest().headers["Authorization"])
        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.url.encodedPath)
        assertTrue(refresh.body!!.utf8().contains("old-refresh"))
        assertEquals("Bearer new-access", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `encerra a sessao quando o refresh token e recusado`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"refresh token inválido ou expirado"}""").build())

        val failure = runCatching { userApi.me() }.exceptionOrNull()

        assertTrue(failure is HttpException && failure.code() == 401)
        assertNull(tokenStore.current)
        assertEquals(SessionState.LoggedOut, sessionManager.state.value)
        assertTrue(sessionManager.notice.value != null)
    }

    @Test
    fun `requisicoes simultaneas com 401 geram uma unica renovacao`() {
        val dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/auth/refresh") ->
                    MockResponse.Builder().code(200).body(tokensJson("new-access", "new-refresh")).build()
                request.headers["Authorization"] == "Bearer new-access" ->
                    MockResponse.Builder().code(200).body(userJson).build()
                else -> MockResponse.Builder().code(401).build()
            }
        }
        server.dispatcher = dispatcher

        val calls = 4
        val pool = Executors.newFixedThreadPool(calls)
        val done = CountDownLatch(calls)
        val successes = java.util.concurrent.atomic.AtomicInteger()
        repeat(calls) {
            pool.execute {
                runCatching { runBlocking { userApi.me() } }.onSuccess { successes.incrementAndGet() }
                done.countDown()
            }
        }
        assertTrue(done.await(20, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(calls, successes.get())
        val refreshCalls = generateSequence { runCatching { server.takeRequest(10, TimeUnit.MILLISECONDS) }.getOrNull() }
            .count { it.url.encodedPath.endsWith("/auth/refresh") }
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `sem rede durante a renovacao mantem a sessao`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(401).build())
        // Sem resposta para o refresh: o servidor derruba a conexao.
        server.enqueue(MockResponse.Builder().onRequestStart(mockwebserver3.SocketEffect.CloseSocket()).build())

        runCatching { userApi.me() }

        assertEquals(AuthTokens("old-access", "old-refresh"), tokenStore.current)
        assertEquals(SessionState.LoggedIn, sessionManager.state.value)
    }
}
