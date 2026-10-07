package com.matheusantiquera.gardenmanager.data.maintenance

import com.matheusantiquera.gardenmanager.core.network.ApiError
import com.matheusantiquera.gardenmanager.core.network.ApiJson
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import java.time.LocalDateTime
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

class MaintenanceRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: MaintenanceRepository

    private val plantJson = """{"id":"p1","display_name":"Monstrinha"}"""
    private val scheduleJson =
        """{"id":"m1","plant":$plantJson,"type":{"id":"t1","name":"Rega"},"due_at":"2026-10-08 08:00:00",""" +
            """"notes":"Meio litro","status":"pending","created_at":"2026-10-01T10:00:00Z","updated_at":"2026-10-01T10:00:00Z"}"""
    private val logJson =
        """{"id":"l1","plant":$plantJson,"type":{"id":"t1","name":"Rega"},"created_from_schedule":true,""" +
            """"performed_at":"2026-10-07 08:40:00","notes":null}"""

    private fun ok(body: String, code: Int = 200) = MockResponse.Builder().code(code).body(body).build()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
        repository = MaintenanceRepository(retrofit.create(MaintenanceApi::class.java))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `tipos sao buscados uma vez so`() = runTest {
        server.enqueue(ok("""{"data":[{"id":"t1","name":"Adubação"},{"id":"t2","name":"Rega"}]}"""))

        val first = (repository.types() as ApiResult.Success).value
        val second = (repository.types() as ApiResult.Success).value

        assertEquals(listOf(MaintenanceType("t1", "Adubação"), MaintenanceType("t2", "Rega")), first)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `falha ao buscar tipos nao fica em cache`() = runTest {
        server.enqueue(ok("""{"error":"erro interno"}""", code = 500))
        server.enqueue(ok("""{"data":[{"id":"t1","name":"Rega"}]}"""))

        assertTrue(repository.types() is ApiResult.Failure)
        assertEquals(listOf(MaintenanceType("t1", "Rega")), (repository.types() as ApiResult.Success).value)
    }

    @Test
    fun `schedules le a pagina com planta, tipo, prazo e observacoes`() = runTest {
        server.enqueue(ok("""{"data":[$scheduleJson],"page":1,"page_size":20,"total":21}"""))

        val page = (repository.schedules(page = 1) as ApiResult.Success).value

        assertEquals(
            MaintenanceSchedule("m1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 8, 8, 0), overdue = false, notes = "Meio litro"),
            page.items.single(),
        )
        assertTrue(page.hasMore)
        val url = server.takeRequest().url
        assertEquals("/api/v1/maintenance-schedules", url.encodedPath)
        assertNull(url.queryParameter("status"))
        // A Agenda só mostra plantas ativas: o filtro vai para a API, que mantém o total e a paginação certos.
        assertEquals("true", url.queryParameter("plant_active"))
        assertEquals("20", url.queryParameter("page_size"))
    }

    @Test
    fun `overdueCount pede so o total dos atrasados`() = runTest {
        server.enqueue(ok("""{"data":[],"page":1,"page_size":1,"total":42}"""))

        assertEquals(42, (repository.overdueCount() as ApiResult.Success).value)

        val url = server.takeRequest().url
        assertEquals("overdue", url.queryParameter("status"))
        assertEquals("true", url.queryParameter("plant_active"))
        assertEquals("1", url.queryParameter("page_size"))
    }

    @Test
    fun `logs le a execucao e se nasceu de agendamento`() = runTest {
        server.enqueue(ok("""{"data":[$logJson],"page":1,"page_size":20,"total":1}"""))

        val page = (repository.logs(page = 1) as ApiResult.Success).value

        assertEquals(
            MaintenanceLog("l1", "p1", "Monstrinha", "t1", "Rega", LocalDateTime.of(2026, 10, 7, 8, 40), fromSchedule = true),
            page.items.single(),
        )
        assertFalse(page.hasMore)
        assertEquals("true", server.takeRequest().url.queryParameter("plant_active"))
    }

    @Test
    fun `createSchedule envia o prazo no formato da API e deixa observacoes nulas fora`() = runTest {
        server.enqueue(ok(scheduleJson, code = 201))

        repository.createSchedule(ScheduleInput("p1", "t1", LocalDateTime.of(2026, 10, 8, 8, 0), notes = null))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/maintenance-schedules", request.url.encodedPath)
        assertEquals("""{"plant_id":"p1","type_id":"t1","due_at":"2026-10-08 08:00:00"}""", request.body!!.utf8())
    }

    @Test
    fun `updateSchedule substitui tudo pelo id`() = runTest {
        server.enqueue(ok(scheduleJson))

        repository.updateSchedule("m1", ScheduleInput("p1", "t2", LocalDateTime.of(2026, 10, 9, 9, 30), notes = "Luz"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/maintenance-schedules/m1", request.url.encodedPath)
        assertEquals("""{"plant_id":"p1","type_id":"t2","due_at":"2026-10-09 09:30:00","notes":"Luz"}""", request.body!!.utf8())
    }

    @Test
    fun `createLog a partir de agendamento envia o schedule_id`() = runTest {
        server.enqueue(ok(logJson, code = 201))

        repository.createLog(LogInput("p1", "t1", LocalDateTime.of(2026, 10, 7, 8, 40), notes = null, scheduleId = "m1"))

        assertEquals(
            """{"plant_id":"p1","type_id":"t1","schedule_id":"m1","performed_at":"2026-10-07 08:40:00"}""",
            server.takeRequest().body!!.utf8(),
        )
    }

    @Test
    fun `updateLog nao envia schedule_id`() = runTest {
        server.enqueue(ok(logJson))

        repository.updateLog("l1", LogInput("p1", "t1", LocalDateTime.of(2026, 10, 7, 8, 40), notes = "Ok", scheduleId = "ignorado"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("""{"plant_id":"p1","type_id":"t1","performed_at":"2026-10-07 08:40:00","notes":"Ok"}""", request.body!!.utf8())
    }

    @Test
    fun `excluir agendamento e execucao responde 204 como sucesso`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        assertTrue(repository.deleteSchedule("m1") is ApiResult.Success)
        assertTrue(repository.deleteLog("l1") is ApiResult.Success)

        assertEquals("DELETE /api/v1/maintenance-schedules/m1", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals("DELETE /api/v1/maintenance-logs/l1", server.takeRequest().let { "${it.method} ${it.url.encodedPath}" })
    }

    @Test
    fun `erro 422 da API vira falha com a mensagem`() = runTest {
        server.enqueue(ok("""{"error":"a data de execução não pode estar no futuro"}""", code = 422))

        val result = repository.createLog(LogInput("p1", "t1", LocalDateTime.of(2030, 1, 1, 0, 0), null))

        assertEquals(ApiError.Http(422, "a data de execução não pode estar no futuro"), (result as ApiResult.Failure).error)
    }
}
