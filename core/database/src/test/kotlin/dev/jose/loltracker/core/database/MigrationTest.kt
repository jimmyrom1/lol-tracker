package dev.jose.loltracker.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Comprueba la migración generada contra los esquemas versionados en `schemas/`. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = LolDatabase::class,
    )

    @Test
    fun v1ToV2KeepsManualMatchesAndAddsTheRiotId() {
        helper.createDatabase(1).apply {
            execSQL(
                """
                INSERT INTO matches (id, champion_id, champion_name, role, queue, result, kills, deaths, assists,
                                     creep_score, duration_seconds, played_at, notes)
                VALUES (7, 'Ahri', 'Ahri', 'MID', 'RANKED_SOLO', 'WIN', 9, 2, 11, 231, 1860, 1790000000000, 'nota')
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(2)

        db.prepare("SELECT champion_id, notes, riot_match_id FROM matches WHERE id = 7").use {
            assertTrue(it.step())
            assertEquals("Ahri", it.getText(0))
            assertEquals("nota", it.getText(1))
            assertTrue("las partidas antiguas no tienen id de Riot", it.isNull(2))
        }
        db.close()
    }

    @Test
    fun v2ToV3AddsTheNewTablesAndMarksTheCatalogAsIncomplete() {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO champions (id, name, title, icon_url, tags, patch_version) " +
                    "VALUES ('Ahri', 'Ahri', '', '', 'Mage', '16.18.1')",
            )
            execSQL(
                """
                INSERT INTO matches (champion_id, champion_name, role, queue, result, kills, deaths, assists,
                                     creep_score, duration_seconds, played_at, notes, riot_match_id)
                VALUES ('Ahri', 'Ahri', 'MID', 'RANKED_SOLO', 'WIN', 9, 2, 11, 231, 1860, 1790000000000, '', 'EUW1_1')
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(3)

        db.prepare("SELECT `key` FROM champions WHERE id = 'Ahri'").use {
            assertTrue(it.step())
            // Vacío: el repositorio lo detecta y vuelve a descargar el catálogo con los ids numéricos.
            assertEquals("", it.getText(0))
        }
        db.prepare("SELECT count(*) FROM match_details").use {
            assertTrue(it.step())
            assertEquals(0L, it.getLong(0))
        }
        db.prepare("SELECT riot_match_id FROM matches").use {
            assertTrue(it.step())
            assertEquals("EUW1_1", it.getText(0))
        }
        db.close()
    }
}
