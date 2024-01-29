package com.example.roomcrudapp.viewModel

import android.content.ContentValues
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomcrudapp.db.Student
import com.example.roomcrudapp.db.StudentDao
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
class StudentViewModel(private val dao: StudentDao):ViewModel() {
    val student: LiveData<List<Student>> = dao.getStudentData()

    fun insertStudentData(student: Student) = viewModelScope.launch {
        dao.insertStudentData(student)
    }

    fun updateStudentData(student: Student) = viewModelScope.launch {
        dao.updateStudentData(student)
    }
    fun deleteStudentData(student: Student) = viewModelScope.launch {
        dao.deleteStudentData(student)
    }



}