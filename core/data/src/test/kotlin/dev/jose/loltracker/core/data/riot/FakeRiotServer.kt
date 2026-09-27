package dev.jose.loltracker.core.data.riot

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/**
 * Imita las rutas de Riot que usa la app con respuestas recortadas de las reales. Guarda qué
 * rutas se piden para comprobar que la app no gasta peticiones de más.
 */
class FakeRiotServer(val puuid: String = "puuid-jimmy") {

    val server = MockWebServer()
    val paths = mutableListOf<String>()

    var accountStatus = 200
    var matchIds = listOf("EUW1_3", "EUW1_2", "EUW1_1")
    var inGame = false

    /** EUW1_1 es un remake de 3 minutos; EUW1_2 una ARAM; el resto, clasificatorias de jungla. */
    private fun matchJson(id: String): String {
        val (queue, seconds, position, champion) = when (id) {
            "EUW1_1" -> listOf("420", "190", "MIDDLE", "Ahri")
            "EUW1_2" -> listOf("450", "1100", "", "FiddleSticks")
            else -> listOf("420", "1834", "JUNGLE", "MonkeyKing")
        }
        return """
            {"metadata":{"matchId":"$id"},
             "info":{"gameCreation":1790000000000,"gameDuration":$seconds,"gameVersion":"16.19.1","queueId":$queue,
                     "participants":[
                       {"puuid":"$puuid","participantId":1,"teamId":100,"riotIdGameName":"jimmyrom","riotIdTagline":"uarra",
                        "championName":"$champion","teamPosition":"$position","champLevel":16,
                        "kills":7,"deaths":3,"assists":12,"totalMinionsKilled":40,"neutralMinionsKilled":150,
                        "goldEarned":12000,"totalDamageDealtToChampions":18000,"visionScore":30,
                        "item0":6676,"item6":3364,"summoner1Id":11,"summoner2Id":4,"win":true,
                        "challenges":{"killParticipation":0.61}},
                       {"puuid":"rival","participantId":6,"teamId":200,"riotIdGameName":"Rival","riotIdTagline":"EUW",
                        "championName":"LeeSin","teamPosition":"$position","kills":2,"deaths":7,"assists":3,
                        "totalMinionsKilled":20,"neutralMinionsKilled":120,"win":false}],
                     "teams":[{"teamId":100,"win":true,"objectives":{"tower":{"kills":9},"dragon":{"kills":3},"baron":{"kills":1}}},
                              {"teamId":200,"win":false,"objectives":{"tower":{"kills":2}}}]}}
        """.trimIndent()
    }

    /** 16 minutos: al 10 vas +10 de CS y al 15, +800 de oro frente al rival. */
    private val timelineJson: String = buildString {
        append("""{"info":{"frames":[""")
        append(
            (0..16).joinToString(",") { minute ->
                """{"timestamp":${minute * 60_000},"participantFrames":{
                    "1":{"participantId":1,"totalGold":${500 + minute * 400},"minionsKilled":${minute * 7},"jungleMinionsKilled":0},
                    "6":{"participantId":6,"totalGold":${500 + minute * 350 - (if (minute >= 15) 50 else 0)},"minionsKilled":${minute * 6},"jungleMinionsKilled":0}}}"""
            },
        )
        append("]}}")
    }

    fun start(): FakeRiotServer {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                paths += path
                return when {
                    path.startsWith("/riot/account") -> json("""{"puuid":"$puuid"}""", accountStatus)
                    path.endsWith("/ids") -> json(matchIds.joinToString(",", "[", "]") { "\"$it\"" })
                    path.endsWith("/timeline") -> json(timelineJson)
                    path.startsWith("/lol/match/v5/matches/") -> json(matchJson(path.substringAfterLast('/')))
                    path.startsWith("/lol/summoner/") -> json("""{"profileIconId":29,"summonerLevel":312}""")
                    path.startsWith("/lol/league/") -> json(
                        """[{"queueType":"RANKED_SOLO_5x5","tier":"SILVER","rank":"IV","leaguePoints":0,"wins":226,"losses":233},
                            {"queueType":"CHERRY","tier":"","rank":"","leaguePoints":0,"wins":1,"losses":1}]""",
                    )
                    path.contains("/by-champion/64") -> json("", 404) // nunca ha jugado Lee Sin
                    path.contains("/by-champion/") -> json("""{"championId":887,"championLevel":38,"championPoints":390649}""")
                    path.contains("/champion-masteries/") -> json("""[{"championId":887,"championLevel":38,"championPoints":390649}]""")
                    path.startsWith("/lol/spectator/") ->
                        if (!inGame) {
                            json("", 404)
                        } else {
                            json(
                                """{"gameId":1,"gameQueueConfigId":420,"gameStartTime":1790000000000,"participants":[
                                    {"puuid":"$puuid","teamId":100,"championId":887,"riotId":"jimmyrom#uarra"},
                                    {"puuid":"rival","teamId":200,"championId":64,"riotId":"Rival#EUW"}]}""",
                            )
                        }
                    else -> json("", 404)
                }
            }
        }
        server.start()
        return this
    }

    fun url(): String = server.url("/").toString()

    fun close() = server.close()

    private fun json(body: String, status: Int = 200) =
        MockResponse.Builder().code(status).addHeader("Content-Type", "application/json").body(body).build()
}
