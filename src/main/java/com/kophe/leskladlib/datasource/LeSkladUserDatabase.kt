package com.kophe.leskladlib.datasource

import androidx.room.*
import androidx.room.OnConflictStrategy.Companion.REPLACE

@Database(entities = [LeSkladUser::class], version = 3, exportSchema = false)
abstract class LeSkladUserDatabase : RoomDatabase() {

    abstract fun userDAO(): LeSkladUserDao

}

@Dao
interface LeSkladUserDao {

    @Query("SELECT * FROM le_sklad_user LIMIT 1")
    suspend fun selectCurrentUser(): LeSkladUser?

    @Insert(onConflict = REPLACE)
    fun insertUser(userProfile: LeSkladUser)

    @Query("DELETE from le_sklad_user")
    fun clear()

}

@Entity(tableName = CACHED_USERS_TABLE)
data class LeSkladUser(
    @PrimaryKey @ColumnInfo(name = "login") val login: String,
    @ColumnInfo(name = "email") val email: String
)
