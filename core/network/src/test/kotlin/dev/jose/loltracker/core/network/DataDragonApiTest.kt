package dev.jose.loltracker.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DataDragonApiTest {

    private val server = MockWebServer()
    private lateinit var api: DataDragonApi

    @Before
    fun setUp() {
        server.start()
        api = createDataDragonApi(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun parsesVersions() = runTest {
        server.enqueue(MockResponse.Builder().body("""["16.19.1","16.18.1"]""").build())
        assertEquals(listOf("16.19.1", "16.18.1"), api.versions())
        assertEquals("/api/versions.json", server.takeRequest().url.encodedPath)
    }

    @Test
    fun parsesChampionsIgnoringUnknownFieldsAndBuildsIconUrl() = runTest {
        // Recorte de la respuesta real: trae muchos más campos (stats, blurb...) que se ignoran.
        server.enqueue(
            MockResponse.Builder().body(
                """
                {"type":"champion","version":"16.19.1","data":{
                  "Ahri":{"version":"16.19.1","id":"Ahri","key":"103","name":"Ahri",
                          "title":"la Vastaya de nueve colas","tags":["Mage","Assassin"],
                          "image":{"full":"Ahri.png","sprite":"champion0.png"},"stats":{"hp":590}}
                }}
                """.trimIndent(),
            ).build(),
        )

        val response = api.champions("16.19.1", "es_ES")
        val ahri = response.data.getValue("Ahri").toModel("https://ddragon.leagueoflegends.com/", "16.19.1")

        assertEquals("/cdn/16.19.1/data/es_ES/champion.json", server.takeRequest().url.encodedPath)
        assertEquals("la Vastaya de nueve colas", ahri.title)
        assertEquals(listOf("Mage", "Assassin"), ahri.tags)
        assertEquals("https://ddragon.leagueoflegends.com/cdn/16.19.1/img/champion/Ahri.png", ahri.iconUrl)
    }
}
