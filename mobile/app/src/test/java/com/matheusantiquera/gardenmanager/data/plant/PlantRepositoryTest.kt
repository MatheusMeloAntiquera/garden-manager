package com.matheusantiquera.gardenmanager.data.plant

import com.matheusantiquera.gardenmanager.core.network.ApiJson
import com.matheusantiquera.gardenmanager.core.network.ApiResult
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceApi
import com.matheusantiquera.gardenmanager.data.maintenance.MaintenanceRepository
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class PlantRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit
    private lateinit var repository: PlantRepository

    private val plantJson =
        """{"id":"p1","display_name":"Monstrinha","nickname":"Monstrinha","notes":null,"active":true,""" +
            """"species":{"id":"s1","scientific_name":"Monstera deliciosa","common_name":"Costela-de-adão"},""" +
            """"environment":{"id":"e1","name":"Sala"},"created_at":"2026-10-01T10:00:00Z","updated_at":"2026-10-01T10:00:00Z"}"""

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient())
            .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
        repository = PlantRepository(retrofit.create(PlantApi::class.java))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `list envia busca e filtros e le a planta com especie e ambiente`() = runTest {
        server.enqueue(ok("""{"data":[$plantJson],"page":1,"page_size":20,"total":21}"""))

        val result = repository.list(query = " mon ", environmentId = "e1", active = true, page = 1)

        val page = (result as ApiResult.Success).value
        assertEquals(
            Plant("p1", "Monstrinha", "Monstrinha", null, true, PlantSpecies("s1", "Monstera deliciosa", "Costela-de-adão"), PlantEnvironment("e1", "Sala")),
            page.plants.single(),
        )
        assertTrue(page.hasMore)
        val url = server.takeRequest().url
        assertEquals("mon", url.queryParameter("q"))
        assertEquals("e1", url.queryParameter("environment_id"))
        assertEquals("true", url.queryParameter("active"))
        assertEquals("20", url.queryParameter("page_size"))
    }

    @Test
    fun `list sem busca nem ambiente nao envia esses parametros`() = runTest {
        server.enqueue(ok("""{"data":[],"page":1,"page_size":20,"total":0}"""))

        repository.list(query = "  ", environmentId = null, active = false, page = 1)

        val url = server.takeRequest().url
        assertNull(url.queryParameter("q"))
        assertNull(url.queryParameter("environment_id"))
        assertEquals("false", url.queryParameter("active"))
    }

    @Test
    fun `create deixa nulos e active fora do JSON`() = runTest {
        server.enqueue(MockResponse.Builder().code(201).body(plantJson).build())

        repository.create(PlantInput(nickname = null, speciesId = "s1", environmentId = null, notes = null))

        assertEquals("""{"species_id":"s1"}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun `update envia tudo, inclusive o status`() = runTest {
        server.enqueue(ok(plantJson))

        repository.update("p1", PlantInput("Monstrinha", "s1", "e1", "Luz"), active = false)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals(
            """{"species_id":"s1","environment_id":"e1","nickname":"Monstrinha","notes":"Luz","active":false}""",
            request.body!!.utf8(),
        )
    }

    @Test
    fun `manutencoes da planta trazem agendamentos e historico com datas e totais`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("/maintenance-schedules") -> ok(
                    """{"data":[{"id":"m1","plant":{"id":"p1","display_name":"Monstrinha"},"type":{"id":"t1","name":"Rega"},""" +
                        """"due_at":"2026-09-29 09:00:00","notes":null,"status":"overdue"}],"page":1,"page_size":100,"total":1}""",
                )
                else -> ok(
                    """{"data":[{"id":"l1","plant":{"id":"p1","display_name":"Monstrinha"},"type":{"id":"t2","name":"Poda"},""" +
                        """"created_from_schedule":false,"performed_at":"2026-09-10 08:40:00","notes":null}],"page":1,"page_size":100,"total":7}""",
                )
            }
        }
        val maintenance = MaintenanceRepository(retrofit.create(MaintenanceApi::class.java))

        val result = (maintenance.forPlant("p1") as ApiResult.Success).value

        val schedule = result.schedules.single()
        assertEquals("Rega", schedule.typeName)
        assertEquals(LocalDateTime.of(2026, 9, 29, 9, 0), schedule.dueAt)
        assertTrue(schedule.overdue)
        assertEquals(1, result.scheduleTotal)
        assertEquals(LocalDateTime.of(2026, 9, 10, 8, 40), result.logs.single().performedAt)
        assertEquals(7, result.logTotal)
    }
}
