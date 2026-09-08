package com.turnus.rota.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftTypeDao {

    /**
     * `id` breaks the tie, and is not decoration.
     *
     * `sort_order` is only unique because the code that assigns it is careful,
     * and a restored backup is not written by that code — it is a file, and a
     * file can be hand-edited, merged, or produced by a future version with a
     * different idea of ordering. SQLite makes no promise about the order of
     * rows that tie, so two shifts sharing a `sort_order` could come back in
     * either order on any given read: the shift list would reshuffle between
     * visits, the same rota would encode to two different share codes, and
     * neither would look like a bug worth reporting. A second, always-unique
     * key costs nothing and makes every read of this table repeatable.
     */
    @Query("SELECT * FROM shift_type ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<ShiftTypeEntity>>

    @Query("SELECT * FROM shift_type ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<ShiftTypeEntity>

    @Query("SELECT * FROM shift_type WHERE id = :id")
    suspend fun getById(id: String): ShiftTypeEntity?

    @Query("SELECT COUNT(*) FROM shift_type")
    suspend fun count(): Int

    /** Null when the table is empty. Used to append rather than reusing an index. */
    @Query("SELECT MAX(sort_order) FROM shift_type")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shiftType: ShiftTypeEntity)

    @Upsert
    suspend fun upsert(shiftType: ShiftTypeEntity)

    /**
     * IGNORE, not ABORT: seeding can be attempted from more than one place at
     * startup, and losing that race must be a no-op rather than a crash.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(shiftTypes: List<ShiftTypeEntity>)

    /**
     * Restore only. ABORT, not IGNORE: a backup with two shifts sharing a
     * letter must fail the whole restore, not quietly drop one of them and
     * leave a pattern pointing at a shift that no longer exists.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(shiftTypes: List<ShiftTypeEntity>)

    @Query("DELETE FROM shift_type")
    suspend fun deleteAll()

    @Update
    suspend fun update(shiftType: ShiftTypeEntity)

    @Delete
    suspend fun delete(shiftType: ShiftTypeEntity)
}

@Dao
interface PatternDao {

    @Query("SELECT * FROM pattern WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<PatternEntity?>

    @Query("SELECT * FROM pattern WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): PatternEntity?

    /** `id` for the same reason [ShiftTypeDao.getAll] has it: this feeds a backup. */
    @Query("SELECT * FROM pattern ORDER BY created_at ASC, id ASC")
    suspend fun getAll(): List<PatternEntity>

    @Query("SELECT * FROM pattern WHERE id = :id")
    suspend fun getById(id: String): PatternEntity?

    @Query("UPDATE pattern SET is_active = 0 WHERE is_active = 1")
    suspend fun deactivateAll()

    @Upsert
    suspend fun upsert(pattern: PatternEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(patterns: List<PatternEntity>)

    @Query("DELETE FROM pattern WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pattern")
    suspend fun deleteAll()
}

@Dao
interface DayOverrideDao {

    @Query(
        "SELECT * FROM day_override WHERE pattern_id = :patternId " +
            "AND day BETWEEN :fromDay AND :toDay ORDER BY day ASC",
    )
    fun observeRange(patternId: String, fromDay: Long, toDay: Long): Flow<List<DayOverrideEntity>>

    @Query(
        "SELECT * FROM day_override WHERE pattern_id = :patternId " +
            "AND day BETWEEN :fromDay AND :toDay ORDER BY day ASC",
    )
    suspend fun getRange(patternId: String, fromDay: Long, toDay: Long): List<DayOverrideEntity>

    @Query("SELECT * FROM day_override WHERE pattern_id = :patternId ORDER BY day ASC")
    fun observeAllFor(patternId: String): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM day_override ORDER BY pattern_id, day")
    suspend fun getAll(): List<DayOverrideEntity>

    @Query("SELECT * FROM day_override WHERE pattern_id = :patternId AND day = :day")
    suspend fun get(patternId: String, day: Long): DayOverrideEntity?

    @Query("SELECT COUNT(*) FROM day_override WHERE shift_type_id = :shiftTypeId")
    suspend fun countUsing(shiftTypeId: String): Int

    @Upsert
    suspend fun upsert(override: DayOverrideEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(overrides: List<DayOverrideEntity>)

    @Query("DELETE FROM day_override")
    suspend fun deleteAll()

    @Query("DELETE FROM day_override WHERE pattern_id = :patternId AND day = :day")
    suspend fun delete(patternId: String, day: Long)

    @Query("DELETE FROM day_override WHERE pattern_id = :patternId")
    suspend fun deleteAllFor(patternId: String)
}

@Dao
interface AppMetaDao {

    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(meta: AppMetaEntity)

    @Upsert
    suspend fun putAll(meta: List<AppMetaEntity>)

    @Query("DELETE FROM app_meta")
    suspend fun deleteAll()

    @Query("SELECT * FROM app_meta")
    suspend fun getAll(): List<AppMetaEntity>

    /** Adding a query changes no schema, so settings need no migration. */
    @Query("SELECT * FROM app_meta")
    fun observeAll(): Flow<List<AppMetaEntity>>
}
