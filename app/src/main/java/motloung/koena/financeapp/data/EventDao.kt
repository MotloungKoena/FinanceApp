package motloung.koena.financeapp.data

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY receivedAt DESC")
    fun all(): Flow<List<Event>>

    @Insert
    suspend fun insert(e: Event)

    @Query("DELETE FROM events")
    suspend fun clear()

    @Query("SELECT * FROM events ORDER BY receivedAt DESC")
    suspend fun allOnce(): List<Event>

    @androidx.room.Query("SELECT id, type, payload, receivedAt FROM events ORDER BY receivedAt DESC")
    fun selectAllAsCursor(): android.database.Cursor

    @androidx.room.Query("SELECT id, type, payload, receivedAt FROM events WHERE id = :id LIMIT 1")
    fun selectByIdAsCursor(id: Long): android.database.Cursor

    @Query("SELECT id, type, payload, receivedAt FROM events ORDER BY receivedAt DESC")
    fun cursorAll(): Cursor

}
