package com.senzaproject.gamespace.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator (`./gradlew connectedDebugAndroidTest`). */
@RunWith(AndroidJUnit4::class)
class AssistantDatabaseTest {
    private lateinit var db: GameMasterDatabase
    private val sessions get() = db.sessionDao()
    private val dock get() = db.dockDao()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GameMasterDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private fun session(pkg: String, start: Long) =
        GameSessionEntity(packageName = pkg, label = pkg, startedAt = start, lastSeenAt = start)

    @Test fun finishSetsTheEndTime() = runBlocking {
        val id = sessions.insert(session("a", 1_000))
        sessions.finish(id, 5_000)
        val row = sessions.getAll().single()
        assertEquals(5_000L, row.endedAt)
    }

    @Test fun heartbeatMovesLastSeenForward() = runBlocking {
        val id = sessions.insert(session("a", 1_000))
        sessions.heartbeat(id, 9_000)
        assertEquals(9_000L, sessions.getAll().single().lastSeenAt)
        assertNull(sessions.getAll().single().endedAt)
    }

    @Test fun orphanSessionsAreClosedWhereTheyWereLastSeen() = runBlocking {
        val open = sessions.insert(session("a", 1_000))
        sessions.heartbeat(open, 7_000)
        val done = sessions.insert(session("b", 2_000))
        sessions.finish(done, 3_000)
        assertEquals(1, sessions.closeOrphans())
        val byPackage = sessions.getAll().associateBy { it.packageName }
        assertEquals(7_000L, byPackage.getValue("a").endedAt)
        assertEquals(3_000L, byPackage.getValue("b").endedAt)
    }

    @Test fun sessionsSurviveRemovingTheGameFromTheLibrary() = runBlocking {
        db.gameDao().upsertOne(GameEntity("a", "A", com.senzaproject.gamespace.core.gaming.GameSource.AUTO, addedAt = 1L))
        sessions.insert(session("a", 1_000))
        db.gameDao().deleteAll(listOf("a"))
        assertEquals(1, sessions.getAll().size)
    }

    @Test fun deleteAllClearsHistory() = runBlocking {
        sessions.insert(session("a", 1))
        sessions.insert(session("b", 2))
        sessions.deleteAll()
        assertEquals(0, sessions.observeAll().first().size)
    }

    @Test fun dockRefusesDuplicatePackages() = runBlocking {
        dock.insert(DockAppEntity("a", "A", 0))
        val second = dock.insert(DockAppEntity("a", "A again", 1))
        assertEquals(-1L, second)
        assertEquals(1, dock.getAll().size)
    }

    @Test fun dockListsInPositionOrder() = runBlocking {
        dock.insert(DockAppEntity("c", "C", 2))
        dock.insert(DockAppEntity("a", "A", 0))
        dock.insert(DockAppEntity("b", "B", 1))
        assertEquals(listOf("a", "b", "c"), dock.observe().first().map { it.packageName })
    }

    @Test fun upsertAllRewritesPositions() = runBlocking {
        dock.insert(DockAppEntity("a", "A", 0))
        dock.insert(DockAppEntity("b", "B", 1))
        dock.upsertAll(listOf(DockAppEntity("b", "B", 0), DockAppEntity("a", "A", 1)))
        assertEquals(listOf("b", "a"), dock.getAll().map { it.packageName })
    }

    @Test fun deleteRemovesOnlyThatApp() = runBlocking {
        dock.insert(DockAppEntity("a", "A", 0))
        dock.insert(DockAppEntity("b", "B", 1))
        dock.delete("a")
        assertEquals(listOf("b"), dock.getAll().map { it.packageName })
        assertNotNull(dock.getAll().firstOrNull())
    }
}

/**
 * Builds a version 1 database by hand (the exact tables phase 4 shipped), runs the real migration through Room,
 * and lets Room validate the result against the version 2 schema. A wrong CREATE TABLE fails here, not on a user's phone.
 */
@RunWith(AndroidJUnit4::class)
class MigrationV1ToV2Test {
    private val name = "migration-test.db"
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun createVersion1() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `games` (`packageName` TEXT NOT NULL, `label` TEXT NOT NULL, " +
                            "`source` TEXT NOT NULL, `hidden` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
                            "`lastLaunchedAt` INTEGER, `launchCount` INTEGER NOT NULL, PRIMARY KEY(`packageName`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `game_profiles` (`packageName` TEXT NOT NULL, `preferredMode` TEXT, " +
                            "`brightnessPercent` INTEGER, `dndEnabled` INTEGER NOT NULL, `overlayEnabled` INTEGER NOT NULL, " +
                            "`monitorLayout` TEXT NOT NULL, `trackSessions` INTEGER NOT NULL, PRIMARY KEY(`packageName`), " +
                            "FOREIGN KEY(`packageName`) REFERENCES `games`(`packageName`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL("INSERT INTO games VALUES ('kept.game', 'Kept', 'AUTO', 0, 1, NULL, 0)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).also { it.writableDatabase; it.close() }
    }

    @Test fun migrationKeepsOldDataAndCreatesNewTables() = runBlocking {
        createVersion1()
        val db = Room.databaseBuilder(context, GameMasterDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .build()
        try {
            // Room validates the schema on first access; this throws if the migration SQL does not match the entities.
            assertEquals("kept.game", db.gameDao().getAll().single().packageName)
            val id = db.sessionDao().insert(GameSessionEntity(packageName = "kept.game", label = "Kept", startedAt = 1, lastSeenAt = 1))
            assertEquals(1, db.sessionDao().getAll().size)
            assertNotNull(id)
            db.dockDao().insert(DockAppEntity("x", "X", 0))
            assertEquals(1, db.dockDao().getAll().size)
        } finally {
            db.close()
        }
    }
}
