package com.senzaproject.gamespace.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One play session. The row is written when the game comes to the foreground and [lastSeenAt] is refreshed
 * while it stays there. If the process dies, [endedAt] stays null and the next start closes it at [lastSeenAt].
 * No foreign key to games: history must survive when a game is removed from the library or uninstalled.
 */
@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lastSeenAt: Long,
)

/** An app shortcut in the floating dock. The primary key makes duplicates impossible. */
@Entity(tableName = "dock_apps")
data class DockAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val position: Int,
)
