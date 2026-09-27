package dev.jose.loltracker.core.data.riot

import dev.jose.loltracker.core.network.DATA_DRAGON_BASE_URL

/** URLs de imágenes de Data Dragon. Necesitan la versión del parche del catálogo guardado. */
object DataDragonImages {
    fun item(version: String, itemId: Int) = "${DATA_DRAGON_BASE_URL}cdn/$version/img/item/$itemId.png"

    fun profileIcon(version: String, iconId: Int) = "${DATA_DRAGON_BASE_URL}cdn/$version/img/profileicon/$iconId.png"

    /** Los hechizos de invocador tienen id numérico en la API y nombre de archivo en Data Dragon. */
    fun summonerSpell(version: String, spellId: Int): String? =
        SPELL_FILES[spellId]?.let { "${DATA_DRAGON_BASE_URL}cdn/$version/img/spell/$it.png" }

    private val SPELL_FILES = mapOf(
        1 to "SummonerBoost", 3 to "SummonerExhaust", 4 to "SummonerFlash", 6 to "SummonerHaste",
        7 to "SummonerHeal", 11 to "SummonerSmite", 12 to "SummonerTeleport", 13 to "SummonerMana",
        14 to "SummonerDot", 21 to "SummonerBarrier", 32 to "SummonerSnowball",
    )
}
