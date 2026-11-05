package app.propcare.homeos.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class ProfileEntity(@PrimaryKey val id: Int = 1, val fullName: String, val phone: String, val postcode: String)

@Entity(tableName = "property")
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val addressLine: String,
    val town: String,
    val postcode: String,
    val type: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "room_item",
    foreignKeys = [ForeignKey(entity = PropertyEntity::class, parentColumns = ["id"], childColumns = ["propertyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("propertyId")],
)
data class RoomEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val propertyId: Long, val name: String)

@Entity(
    tableName = "compliance",
    foreignKeys = [ForeignKey(entity = PropertyEntity::class, parentColumns = ["id"], childColumns = ["propertyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["propertyId", "kind"], unique = true)],
)
data class ComplianceEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val propertyId: Long, val kind: String, val lastDone: String?)

@Entity(
    tableName = "job",
    foreignKeys = [ForeignKey(entity = PropertyEntity::class, parentColumns = ["id"], childColumns = ["propertyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("propertyId"), Index(value = ["ref"], unique = true)],
)
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ref: Int,
    val propertyId: Long,
    val title: String,
    val description: String,
    val trade: String,
    val urgency: String,
    val status: String = "requested",
    val triageJson: String? = null,
    val accessNotes: String = "",
    val preferredTimes: String = "",
    val quotePence: Int? = null,
    val visitAt: Long? = null,
    val staffNotes: String = "",
    val photoPath: String? = null,
    val afterPhotoPath: String? = null,
    val sentAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "job_event",
    foreignKeys = [ForeignKey(entity = JobEntity::class, parentColumns = ["id"], childColumns = ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("jobId")],
)
data class JobEventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val jobId: Long, val status: String, val note: String, val createdAt: Long = System.currentTimeMillis())

@Dao
interface AppDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun profile(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun profileNow(): ProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(p: ProfileEntity)

    @Query("SELECT * FROM property ORDER BY createdAt") fun properties(): Flow<List<PropertyEntity>>
    @Query("SELECT * FROM property ORDER BY createdAt") suspend fun propertiesNow(): List<PropertyEntity>
    @Insert suspend fun insertProperty(p: PropertyEntity): Long
    @Update suspend fun updateProperty(p: PropertyEntity)
    @Query("DELETE FROM property WHERE id = :id") suspend fun deleteProperty(id: Long)

    @Query("SELECT * FROM room_item WHERE propertyId = :propertyId ORDER BY id") fun rooms(propertyId: Long): Flow<List<RoomEntity>>
    @Query("SELECT * FROM room_item ORDER BY id") fun allRooms(): Flow<List<RoomEntity>>
    @Insert suspend fun insertRoom(r: RoomEntity)
    @Update suspend fun updateRoom(r: RoomEntity)
    @Query("DELETE FROM room_item WHERE id = :id") suspend fun deleteRoom(id: Long)

    @Query("SELECT * FROM compliance") fun compliance(): Flow<List<ComplianceEntity>>
    @Query("SELECT * FROM compliance") suspend fun complianceNow(): List<ComplianceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCompliance(c: ComplianceEntity)
    @Query("SELECT * FROM compliance WHERE propertyId = :propertyId AND kind = :kind") suspend fun complianceFor(propertyId: Long, kind: String): ComplianceEntity?

    @Query("SELECT * FROM job ORDER BY createdAt DESC") fun jobs(): Flow<List<JobEntity>>
    @Query("SELECT * FROM job ORDER BY createdAt DESC") suspend fun jobsNow(): List<JobEntity>
    @Query("SELECT * FROM job WHERE id = :id") suspend fun job(id: Long): JobEntity?
    @Query("SELECT * FROM job WHERE propertyId = :propertyId ORDER BY createdAt DESC") suspend fun jobsFor(propertyId: Long): List<JobEntity>
    @Query("SELECT COALESCE(MAX(ref), 1000) + 1 FROM job") suspend fun nextRef(): Int
    @Insert suspend fun insertJob(j: JobEntity): Long
    @Update suspend fun updateJob(j: JobEntity)

    @Query("SELECT * FROM job_event ORDER BY createdAt") fun events(): Flow<List<JobEventEntity>>
    @Query("SELECT * FROM job_event WHERE jobId = :jobId ORDER BY createdAt") suspend fun eventsFor(jobId: Long): List<JobEventEntity>
    @Insert suspend fun insertEvent(e: JobEventEntity)
}

@Database(
    entities = [ProfileEntity::class, PropertyEntity::class, RoomEntity::class, ComplianceEntity::class, JobEntity::class, JobEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "propcare.db").build().also { instance = it }
        }
    }
}
