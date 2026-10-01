package com.matheusantiquera.gardenmanager.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiResultTest {

    @Test
    fun `erro simples da API traz so a mensagem`() {
        val error = parseApiError(401, """{"error":"e-mail ou senha inválidos"}""")

        assertEquals(401, error.code)
        assertEquals("e-mail ou senha inválidos", error.message)
        assertEquals(emptyList<ValidationDetail>(), error.details)
    }

    @Test
    fun `erro de validacao separa campo e regra`() {
        val error = parseApiError(
            400,
            """{"error":"dados inválidos","details":["Password: strongpassword","BirthDate: birthdate"]}""",
        )

        assertEquals("dados inválidos", error.message)
        assertEquals(
            listOf(ValidationDetail("Password", "strongpassword"), ValidationDetail("BirthDate", "birthdate")),
            error.details,
        )
    }

    @Test
    fun `corpo vazio ou invalido nao quebra`() {
        assertNull(parseApiError(500, null).message)
        assertNull(parseApiError(500, "").message)
        assertNull(parseApiError(502, "<html>Bad Gateway</html>").message)
    }

    @Test
    fun `details que nao e lista de textos e ignorado`() {
        val error = parseApiError(400, """{"error":"x","details":{"a":1}}""")

        assertEquals("x", error.message)
        assertEquals(emptyList<ValidationDetail>(), error.details)
    }
}
