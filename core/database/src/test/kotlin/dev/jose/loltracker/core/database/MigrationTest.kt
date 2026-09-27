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
}
