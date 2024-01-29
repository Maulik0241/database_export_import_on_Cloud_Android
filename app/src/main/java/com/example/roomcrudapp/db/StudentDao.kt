package com.example.roomcrudapp.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StudentDao {

    @Insert
    suspend fun insertStudentData(student: Student)

    @Update
    suspend fun updateStudentData(student: Student)

    @Delete
    suspend fun deleteStudentData(student: Student)

    @Query("SELECT * FROM tbl_student_details ORDER BY student_id DESC")
     fun getStudentData():LiveData<List<Student>>


}