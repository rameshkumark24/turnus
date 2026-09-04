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

    @Query("SELECT * FROM shift_type ORDER BY sort_order ASC")
    fun observeAll(): Flow<List<ShiftTypeEntity>>

    @Query("SELECT * FROM shift_type ORDER BY sort_order ASC")
    suspend fun getAll(): List<ShiftTypeEntity>

    @Query("SELECT * FROM shift_type WHERE id = :id")
    suspend fun getById(id: String): ShiftTypeEntity?

    @Query("SELECT COUNT(*) FROM shift_type")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(shiftType: ShiftTypeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(shiftTypes: List<ShiftTypeEntity>)

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

    @Query("SELECT * FROM pattern ORDER BY created_at ASC")
    suspend fun getAll(): List<PatternEntity>

    @Query("SELECT * FROM pattern WHERE id = :id")
    suspend fun getById(id: String): PatternEntity?

    @Query("UPDATE pattern SET is_active = 0 WHERE is_active = 1")
    suspend fun deactivateAll()

    @Upsert
    suspend fun upsert(pattern: PatternEntity)

    @Query("DELETE FROM pattern WHERE id = :id")
    suspend fun deleteById(id: String)
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

    @Query("SELECT COUNT(*) FROM day_override WHERE shift_type_id = :shiftTypeId")
    suspend fun countUsing(shiftTypeId: String): Int

    @Upsert
    suspend fun upsert(override: DayOverrideEntity)

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

    @Query("SELECT * FROM app_meta")
    suspend fun getAll(): List<AppMetaEntity>
}
