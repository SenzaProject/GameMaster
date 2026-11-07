package com.senzaproject.gamespace.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [GameEntity::class, GameProfileEntity::class, GameSessionEntity::class, DockAppEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class GameMasterDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun profileDao(): GameProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun dockDao(): DockDao
}

/** Version 1 to 2 (phase 5): adds play sessions and the floating dock. Existing tables are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, `lastSeenAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `dock_apps` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // No destructive fallback: a missing migration must fail loudly instead of wiping user data.
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): GameMasterDatabase =
        Room.databaseBuilder(context, GameMasterDatabase::class.java, "game_master.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun gameDao(db: GameMasterDatabase): GameDao = db.gameDao()
    @Provides fun profileDao(db: GameMasterDatabase): GameProfileDao = db.profileDao()
    @Provides fun sessionDao(db: GameMasterDatabase): SessionDao = db.sessionDao()
    @Provides fun dockDao(db: GameMasterDatabase): DockDao = db.dockDao()
}
