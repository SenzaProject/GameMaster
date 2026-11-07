package com.senzaproject.gamespace.core.assistant

import androidx.room.withTransaction
import com.senzaproject.gamespace.core.database.DockAppEntity
import com.senzaproject.gamespace.core.database.DockDao
import com.senzaproject.gamespace.core.database.GameMasterDatabase
import com.senzaproject.gamespace.core.gaming.InstalledApp
import com.senzaproject.gamespace.core.gaming.InstalledAppSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The floating dock: up to [MAX_DOCK_APPS] app shortcuts with a fixed order, stored in Room. */
@Singleton
class DockRepository @Inject constructor(
    private val db: GameMasterDatabase,
    private val dao: DockDao,
    private val appSource: InstalledAppSource,
) {
    val apps: Flow<List<DockAppEntity>> = dao.observe()

    /** Every launchable app, for the picker. */
    suspend fun launchableApps(): List<InstalledApp> = appSource.launchableApps().sortedBy { it.label.lowercase() }

    suspend fun add(app: InstalledApp): DockAddResult = db.withTransaction {
        val current = dao.getAll()
        val plan = planDockAdd(current.map { it.packageName }, app.packageName)
        if (plan == DockAddResult.ADDED) dao.insert(DockAppEntity(app.packageName, app.label, current.size))
        plan
    }

    suspend fun remove(packageName: String) = db.withTransaction {
        dao.delete(packageName)
        dao.upsertAll(dao.getAll().mapIndexed { index, app -> app.copy(position = index) })
    }

    /** Saves a new order. Packages that are not in the dock are ignored; none are added or lost. */
    suspend fun reorder(order: List<String>) = db.withTransaction {
        val byPackage = dao.getAll().associateBy { it.packageName }
        val reordered = order.distinct().mapNotNull { byPackage[it] }
        val missing = byPackage.values.filter { it.packageName !in order }.sortedBy { it.position }
        dao.upsertAll((reordered + missing).mapIndexed { index, app -> app.copy(position = index) })
    }
}
