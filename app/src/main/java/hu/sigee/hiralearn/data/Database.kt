package hu.sigee.hiralearn.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import hu.sigee.hiralearn.core.QuestionType
import hu.sigee.hiralearn.core.SessionMode
import hu.sigee.hiralearn.core.Skill
import kotlinx.coroutines.flow.Flow

/** Current spaced-repetition state of one (kana, skill). */
@Entity(tableName = "cards", primaryKeys = ["kanaId", "skill"])
data class CardEntity(
    val kanaId: String,
    val skill: Skill,
    val level: Int,
    val dueAt: Long,
    val introducedAt: Long,
    val lastReviewedAt: Long?,
    val timesCorrect: Int,
    val timesWrong: Int,
    val lapses: Int,
    /** For future sync: last-writer-wins per card. */
    val updatedAt: Long,
)

/**
 * Append-only log of every answer. Ids are UUIDs, so logs from several devices can be merged
 * without conflicts; [syncedAt] stays null until a future sync uploads the event.
 */
@Entity(tableName = "review_events", indices = [Index("answeredAt"), Index("syncedAt")])
data class ReviewEventEntity(
    @PrimaryKey val id: String,
    val kanaId: String,
    val skill: Skill,
    val questionType: QuestionType,
    val correct: Boolean,
    val confusedWithKanaId: String?,
    val mode: SessionMode,
    val answeredAt: Long,
    val syncedAt: Long? = null,
)

/** The columns of a review event that matter for confusions. */
data class MixUpRow(val kanaId: String, val confusedWithKanaId: String, val answeredAt: Long)

/** A right answer in a drill; these clear confusion pairs. */
data class DrillAnswerRow(val kanaId: String, val skill: Skill, val answeredAt: Long)

@Dao
abstract class ProgressDao {
    @Query("SELECT * FROM cards")
    abstract fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards")
    abstract suspend fun cards(): List<CardEntity>

    @Upsert
    abstract suspend fun upsertCards(cards: List<CardEntity>)

    /** Adds cards that don't exist yet; an existing card (maybe just answered) is never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCardsIfAbsent(cards: List<CardEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertEvents(events: List<ReviewEventEntity>)

    @Query("SELECT kanaId, confusedWithKanaId, answeredAt FROM review_events WHERE confusedWithKanaId IS NOT NULL")
    abstract fun observeMixUps(): Flow<List<MixUpRow>>

    @Query("SELECT kanaId, confusedWithKanaId, answeredAt FROM review_events WHERE confusedWithKanaId IS NOT NULL")
    abstract suspend fun mixUps(): List<MixUpRow>

    @Query("SELECT kanaId, skill, answeredAt FROM review_events WHERE mode = 'DRILL' AND correct = 1")
    abstract fun observeDrillAnswers(): Flow<List<DrillAnswerRow>>

    @Query("SELECT kanaId, skill, answeredAt FROM review_events WHERE mode = 'DRILL' AND correct = 1")
    abstract suspend fun drillAnswers(): List<DrillAnswerRow>

    @Query("SELECT COUNT(*) FROM review_events WHERE answeredAt >= :since")
    abstract fun observeAnswerCountSince(since: Long): Flow<Int>

    /** Debug helper: every card becomes due at [now]. Returns the number of cards changed. */
    @Query("UPDATE cards SET dueAt = :now, updatedAt = :now")
    abstract suspend fun makeAllDue(now: Long): Int

    @Query("DELETE FROM cards")
    abstract suspend fun deleteCards()

    @Query("DELETE FROM review_events")
    abstract suspend fun deleteEvents()

    @Transaction
    open suspend fun save(cards: List<CardEntity>, events: List<ReviewEventEntity>) {
        if (cards.isNotEmpty()) upsertCards(cards)
        if (events.isNotEmpty()) insertEvents(events)
    }

    @Transaction
    open suspend fun deleteAll() {
        deleteCards()
        deleteEvents()
    }
}

@Database(entities = [CardEntity::class, ReviewEventEntity::class], version = 1, exportSchema = true)
abstract class HiraLearnDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        fun create(context: Context): HiraLearnDatabase =
            Room.databaseBuilder(context, HiraLearnDatabase::class.java, "hiralearn.db").build()
    }
}
