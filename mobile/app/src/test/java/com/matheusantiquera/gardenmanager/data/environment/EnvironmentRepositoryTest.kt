package com.matheusantiquera.gardenmanager.data.environment

import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiJson
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class EnvironmentRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: EnvironmentRepository

    private fun environmentJson(
        id: String,
        name: String,
        plantCount: Int = 0,
        notes: String? = null,
        active: Boolean = true,
        overdueCount: Int = 0,
    ) = """{"id":"$id","name":"$name","notes":${notes?.let { "\"$it\"" } ?: "null"},"active":$active,""" +
            """"plant_count":$plantCount,"overdue_count":$overdueCount,""" +
            """"created_at":"2026-10-01T10:00:00Z","updated_at":"2026-10-01T10:00:00Z"}"""

    private fun pageJson(items: List<String>, page: Int, total: Int) =
        """{"data":[${items.joinToString(",")}],"page":$page,"page_size":100,"total":$total}"""

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(EnvironmentApi::class.java)
        repository = EnvironmentRepository(api)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `listAll le as contagens e envia o filtro de status`() = runTest {
        server.enqueue(
            ok(pageJson(listOf(environmentJson("1", "Sala", plantCount = 2, notes = "Luz da manhã", overdueCount = 1)), page = 1, total = 1)),
        )

        val result = repository.listAll(active = true)

        assertEquals(
            ApiResult.Success(listOf(Environment("1", "Sala", "Luz da manhã", active = true, plantCount = 2, overdueCount = 1))),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/api/v1/environments", request.url.encodedPath)
        assertEquals("true", request.url.queryParameter("active"))
        assertEquals("1", request.url.queryParameter("page"))
        assertEquals("100", request.url.queryParameter("page_size"))
    }

    @Test
    fun `listAll sem status nao envia o parametro active`() = runTest {
        server.enqueue(ok(pageJson(emptyList(), page = 1, total = 0)))

        repository.listAll(active = null)

        assertNull(server.takeRequest().url.queryParameter("active"))
    }

    @Test
    fun `listAll busca as paginas seguintes ate completar o total`() = runTest {
        val firstPage = (1..100).map { environmentJson("a$it", "Ambiente $it") }
        server.enqueue(ok(pageJson(firstPage, page = 1, total = 101)))
        server.enqueue(ok(pageJson(listOf(environmentJson("b1", "Último")), page = 2, total = 101)))

        val result = repository.listAll(active = true)

        assertEquals(101, (result as ApiResult.Success).value.size)
        assertEquals("Último", result.value.last().name)
        assertEquals("1", server.takeRequest().url.queryParameter("page"))
        assertEquals("2", server.takeRequest().url.queryParameter("page"))
    }

    @Test
    fun `create nao envia active, para o ambiente nascer ativo`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body(environmentJson("1", "Varanda")).build())

        repository.create(name = "Varanda", notes = null)

        val body = server.takeRequest().body!!.utf8()
        assertEquals("""{"name":"Varanda"}""", body)
    }

    @Test
    fun `update envia nome, observacoes e status`() = runTest {
        server.enqueue(ok(environmentJson("1", "Sala", notes = "Janela", active = false)))

        val result = repository.update(id = "1", name = "Sala", notes = "Janela", active = false)

        assertFalse((result as ApiResult.Success).value.active)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/environments/1", request.url.encodedPath)
        assertEquals("""{"name":"Sala","notes":"Janela","active":false}""", request.body!!.utf8())
    }

    @Test
    fun `delete com 204 e sucesso e 404 vira erro com a mensagem da API`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":"ambiente não encontrado"}""").build())

        assertTrue(repository.delete("1") is ApiResult.Success)
        assertEquals(
            ApiResult.Failure(ApiError.Http(404, "ambiente não encontrado")),
            repository.delete("1"),
        )
        assertEquals("DELETE", server.takeRequest().method)
    }
}
