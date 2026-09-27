package dev.jose.loltracker.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton

@Database(
    entities = [MatchEntity::class, ChampionEntity::class],
    version = 2,
    exportSchema = true,
    // v2: columna riot_match_id para las partidas importadas. Room genera la migración a partir de
    // los esquemas exportados en schemas/; MigrationTest comprueba que no se pierde ningún dato.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class LolDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao
    abstract fun championDao(): ChampionDao
}

/** Los enums se guardan por nombre (por defecto en Room) y las fechas como epoch millis. */
internal class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LolDatabase =
        Room.databaseBuilder(context, LolDatabase::class.java, "lol-tracker.db").build()

    @Provides
    fun matchDao(db: LolDatabase): MatchDao = db.matchDao()

    @Provides
    fun championDao(db: LolDatabase): ChampionDao = db.championDao()
}
