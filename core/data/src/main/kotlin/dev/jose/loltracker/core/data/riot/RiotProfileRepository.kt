package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.database.CacheDao
import dev.jose.loltracker.core.database.CacheEntity
import dev.jose.loltracker.core.database.ChampionDao
import dev.jose.loltracker.core.model.ChampionMastery
import dev.jose.loltracker.core.model.LiveGame
import dev.jose.loltracker.core.model.LivePlayer
import dev.jose.loltracker.core.model.PlayerProfile
import dev.jose.loltracker.core.model.RankEntry
import dev.jose.loltracker.core.model.RankedQueue
import dev.jose.loltracker.core.network.LeagueEntryDto
import dev.jose.loltracker.core.network.RiotPlatformApi
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

interface RiotProfileRepository {
    /** Perfil de la cuenta guardada. Se reutiliza durante [PROFILE_TTL] salvo que se fuerce. */
    suspend fun profile(forceRefresh: Boolean = false): RiotResult<PlayerProfile>

    /** Partida en curso de la cuenta guardada, con rango y maestría de los 10; null si no está jugando. */
    suspend fun liveGame(): RiotResult<LiveGame?>

    companion object {
        val PROFILE_TTL: Duration = Duration.ofMinutes(10)
        val RANK_TTL: Duration = Duration.ofMinutes(30)
        val MASTERY_TTL: Duration = Duration.ofHours(12)
    }
}

@Serializable
internal data class ProfileSnapshot(
    val profileIconId: Int,
    val level: Long,
    val ranks: List<RankEntry>,
    val masteries: List<ChampionMastery>,
)

internal class DefaultRiotProfileRepository @Inject constructor(
    private val api: RiotPlatformApi,
    private val importer: RiotImportRepository,
    private val settings: RiotSettings,
    private val cache: CacheDao,
    private val champions: ChampionDao,
    private val clock: Clock,
) : RiotProfileRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun profile(forceRefresh: Boolean): RiotResult<PlayerProfile> {
        val riotId = settings.riotId ?: return RiotResult.Failure(RiotError.NOT_CONFIGURED)
        val puuid = ensurePuuid() ?: return RiotResult.Failure(RiotError.NOT_CONFIGURED)
        val ttl = if (forceRefresh) Duration.ZERO else RiotProfileRepository.PROFILE_TTL
        return riotCall {
            val (snapshot, fetchedAt) = cached("profile:$puuid", ttl, ProfileSnapshot.serializer()) {
                val summoner = api.summoner(puuid)
                ProfileSnapshot(
                    profileIconId = summoner.profileIconId,
                    level = summoner.summonerLevel,
                    ranks = api.leagueEntries(puuid).mapNotNull { it.toModel() },
                    masteries = api.topMasteries(puuid, count = 5).map {
                        ChampionMastery(it.championId.toString(), it.championLevel, it.championPoints)
                    },
                )
            }
            PlayerProfile(
                riotId = riotId,
                profileIconUrl = champions.cachedPatchVersion()?.let { DataDragonImages.profileIcon(it, snapshot.profileIconId) },
                summonerLevel = snapshot.level,
                ranks = snapshot.ranks.sortedBy { it.queue },
                topMasteries = snapshot.masteries,
                fetchedAt = fetchedAt,
            )
        }
    }

    override suspend fun liveGame(): RiotResult<LiveGame?> {
        val puuid = ensurePuuid() ?: return RiotResult.Failure(RiotError.NOT_CONFIGURED)
        return riotCall {
            val game = try {
                api.activeGame(puuid)
            } catch (e: HttpException) {
                if (e.isNotFound()) return@riotCall null else throw e
            }
            // 1 petición por la partida y hasta 20 más (rango y maestría de cada jugador). El
            // limitador las reparte en el tiempo y la caché evita repetirlas si se vuelve a mirar.
            val players = coroutineScope {
                game.participants.map { p ->
                    async {
                        val playerPuuid = p.puuid
                        LivePlayer(
                            puuid = playerPuuid,
                            riotId = p.riotId,
                            teamId = p.teamId,
                            championKey = p.championId.toString(),
                            soloRank = playerPuuid?.let { soloRank(it) },
                            mastery = playerPuuid?.let { mastery(it, p.championId) },
                            isMe = playerPuuid == puuid,
                        )
                    }
                }.awaitAll()
            }
            LiveGame(
                gameId = game.gameId,
                queueId = game.gameQueueConfigId,
                startedAt = game.gameStartTime.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                players = players,
            )
        }
    }

    private suspend fun soloRank(puuid: String): RankEntry? = cached(
        "league:$puuid",
        RiotProfileRepository.RANK_TTL,
        ListSerializer(RankEntry.serializer()),
    ) { api.leagueEntries(puuid).mapNotNull { it.toModel() } }.first.firstOrNull { it.queue == RankedQueue.SOLO }

    private suspend fun mastery(puuid: String, championId: Long): ChampionMastery? = cached(
        "mastery:$puuid:$championId",
        RiotProfileRepository.MASTERY_TTL,
        ChampionMastery.serializer().nullable,
    ) {
        try {
            val dto = api.mastery(puuid, championId)
            ChampionMastery(dto.championId.toString(), dto.championLevel, dto.championPoints)
        } catch (e: HttpException) {
            // 404: nunca ha jugado ese campeón.
            if (e.isNotFound()) null else throw e
        }
    }.first

    /** Devuelve lo guardado si tiene menos de [ttl]; si no, lo pide y lo guarda. */
    private suspend fun <T> cached(key: String, ttl: Duration, serializer: KSerializer<T>, fetch: suspend () -> T): Pair<T, Instant> {
        val now = clock.instant()
        cache.get(key)?.let { entry ->
            if (Duration.between(entry.fetchedAt, now) < ttl) {
                return json.decodeFromString(serializer, entry.json) to entry.fetchedAt
            }
        }
        val value = fetch()
        cache.put(CacheEntity(key, json.encodeToString(serializer, value), now))
        return value to now
    }

    /** El PUUID se guarda al importar; si aún no se ha importado nada, se obtiene sincronizando. */
    private suspend fun ensurePuuid(): String? {
        settings.puuid?.let { return it }
        if (settings.riotId == null) return null
        importer.syncSaved()
        return settings.puuid
    }

    private fun LeagueEntryDto.toModel(): RankEntry? {
        val queue = when (queueType) {
            "RANKED_SOLO_5x5" -> RankedQueue.SOLO
            "RANKED_FLEX_SR" -> RankedQueue.FLEX
            else -> return null
        }
        return RankEntry(queue, tier, rank, leaguePoints, wins, losses)
    }
}
