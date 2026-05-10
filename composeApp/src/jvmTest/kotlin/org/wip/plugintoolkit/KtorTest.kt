package org.wip.plugintoolkit

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking


import org.junit.Test

class KtorTest {
    @Test
    fun testKtorCio() = runBlocking {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        try {
            val url = "https://raw.githubusercontent.com/WindsOf/plugin-toolkit-plugins/master/dist/index.json"
            val response = client.get(url)
            println("Status: ${response.status}")
            println("Body: ${response.bodyAsText()}")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            client.close()
        }
    }
}

