package com.senzaproject.gamespace.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: GameSessionEntity): Long

    @Query("UPDATE game_sessions SET lastSeenAt = :now WHERE id = :id")
    suspend fun heartbeat(id: Long, now: Long)

    @Query("UPDATE game_sessions SET endedAt = :endedAt, lastSeenAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long)

    /** Closes sessions left open by a crash or a killed process, ending them when they were last seen. */
    @Query("UPDATE game_sessions SET endedAt = lastSeenAt WHERE endedAt IS NULL")
    suspend fun closeOrphans(): Int

    @Query("SELECT * FROM game_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<GameSessionEntity>>

    @Query("SELECT * FROM game_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<GameSessionEntity>

    @Query("DELETE FROM game_sessions")
    suspend fun deleteAll()
}

@Dao
interface DockDao {
    @Query("SELECT * FROM dock_apps ORDER BY position ASC")
    fun observe(): Flow<List<DockAppEntity>>

    @Query("SELECT * FROM dock_apps ORDER BY position ASC")
    suspend fun getAll(): List<DockAppEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: DockAppEntity): Long

    @Query("DELETE FROM dock_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Upsert
    suspend fun upsertAll(apps: List<DockAppEntity>)
}
