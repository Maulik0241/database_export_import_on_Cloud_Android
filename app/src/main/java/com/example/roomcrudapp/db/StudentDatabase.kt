package com.example.roomcrudapp.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom


@Database(entities = [Student::class], version = 1)
abstract class StudentDatabase : RoomDatabase() {


    abstract fun studentDao(): StudentDao


    companion object {

        private const val DATABASE_NAME = "student_details_database.db"
        private const val TAG = "StudentDatabase"
        private var passphrase: ByteArray? = null
        private var factory: SupportFactory? = null

    @Volatile
    private var INSTANCE: StudentDatabase? = null
    fun getInstance(context: Context, password: String): StudentDatabase {
        return INSTANCE ?: synchronized(this) {
            val supportFactory = SupportFactory(SQLiteDatabase.getBytes(password.toCharArray()))
            val instance = Room.databaseBuilder(
                context.applicationContext,
                StudentDatabase::class.java,
                DATABASE_NAME,
            )
                .openHelperFactory(supportFactory)
                .allowMainThreadQueries()
                .build()
            INSTANCE = instance
            instance
        }
    }
    }
}



/*        @Volatile
        private var INSTANCE: StudentDatabase? = null

        fun getInstance(context: Context): StudentDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    SQLiteDatabase.loadLibs(context)
                    try {
                        /*if (passphrase == null) {
                            // Generate passphrase only if not already generated
                            passphrase = generatePassphrase()
                            factory = SupportFactory(passphrase!!)
                           Log.d(TAG, "Generated passphrase: ${passphrase!!.joinToString("") { "%02x".format(it) }}")

                           // Log.d(TAG, "Generated passphrase: ${passphrase!!}")
                        }*/

                        // Log the passphrase
                        Log.d(TAG, "Database path: ${context.getDatabasePath(DATABASE_NAME).path}")

                        // Do not delete the database here unless you have a specific use case
//                         context.deleteDatabase(DATABASE_NAME)

                        factory = SupportFactory(SQLiteDatabase.getBytes("1234".toCharArray()))

                        instance = Room.databaseBuilder(
                            context.applicationContext,
                            StudentDatabase::class.java,
                            DATABASE_NAME
                        )
                            .openHelperFactory(factory)
                            .fallbackToDestructiveMigration()
                            .build()
                    } catch (e: Exception) {
                            // Log any exceptions during database initialization
                            Log.e(TAG, "Error initializing database", e)
                            throw e
                    }
//                    finally {
//                        instance?.close()
//                    }
                }
                return instance
            }
    }*/
