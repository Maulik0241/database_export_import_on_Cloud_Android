package com.example.roomcrudapp

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.roomcrudapp.adpters.StudentRecyclerViewAdapter
import com.example.roomcrudapp.databinding.ActivityMainBinding
import com.example.roomcrudapp.db.RestoredDatabaseHelper
import com.example.roomcrudapp.db.Student
import com.example.roomcrudapp.db.StudentDao
import com.example.roomcrudapp.db.StudentDatabase
import com.example.roomcrudapp.viewModel.StudentViewModel
import com.example.roomcrudapp.viewModel.StudentViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import java.util.*
import com.google.api.services.drive.model.File
import kotlinx.coroutines.*
import net.sqlcipher.database.SQLiteDatabase
import java.io.*

class MainActivity : AppCompatActivity() {


    private lateinit var appDataDir: String
    private val TAG = this::class.java.simpleName
    private lateinit var dbPath: String
    private lateinit var dbPathShm: String
    private lateinit var dbPathWal: String
    private lateinit var passPath: String
    private lateinit var binding: ActivityMainBinding

    private lateinit var viewModel: StudentViewModel
    private lateinit var adapter: StudentRecyclerViewAdapter

    private lateinit var selectedStudent: Student
    private var isListItemClicked: Boolean = false

    private lateinit var googleDriveService: Drive
    private val REQUEST_CODE_SIGN_IN = 1001
    private val appDataScope = Scope(DriveScopes.DRIVE_APPDATA)

    private val password = "1234"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        SQLiteDatabase.loadLibs(this)

        appDataDir = applicationContext.applicationInfo.dataDir
        dbPath = "$appDataDir/databases/student_details_database.db"
        dbPathShm = "$appDataDir/databases/student_details_database.db-shm"
        dbPathWal = "$appDataDir/databases/student_details_database.db-wal"
        passPath = "$appDataDir/databases/password.txt"

        val databasePath = getDatabasePath("student_details_database.db")

// Print or log the path
        Log.e("DatabasePath", "Database path: $databasePath $password ")
        saveValueToFile(password)
        val dao = StudentDatabase.getInstance(application, password).studentDao()
        val factory = StudentViewModelFactory(dao)
        viewModel = ViewModelProvider(this, factory)[StudentViewModel::class.java]

        binding.apply {
            btnSave.setOnClickListener {
                if (isListItemClicked) {
                    updateStudentData()
                    clearInput()
                } else {
                    saveStudentData()
                    clearInput()
                }
            }

            btnClear.setOnClickListener {
                if (isListItemClicked) {
                    deleteStudentData()
                    clearInput()
                } else {
                    clearInput()
                }
            }

            initRecyclerView()

        }

        if (GoogleSignIn.getLastSignedInAccount(applicationContext) != null) {
            Log.e(TAG, "onCreate: Google email already signIn")
            initializeGoogleDriveService()
            displayStudentList()
            // No need to sign in again, the user is already signed in
        } else {
            signInToGoogle()
        }

    }

    override fun onDestroy() {
        StudentDatabase.getInstance(application, password).close()
        super.onDestroy()
    }


    /**
     * Main Menu bar
     */

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.upload -> {
                uploadDatabaseToDrive()
            }

            R.id.restore -> {
                if (::googleDriveService.isInitialized) {
                    // Call download function only if the database has been uploaded
                    download()
                    Log.d(TAG, "onOptionsItemSelected: Restore Data Successfully ")
                } else {
                    // Handle the case where googleDriveService is not initialized
                    // You might want to prompt the user to sign in or initialize it
                    signInToGoogle()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun saveStudentData() {

        viewModel.insertStudentData(
            Student(
                0,
                binding.etName.text.toString(),
                binding.etEmail.text.toString(),
            )
        )
    }

    private fun updateStudentData() {
        viewModel.updateStudentData(
            Student(
                selectedStudent.id,
                binding.etName.text.toString(),
                binding.etEmail.text.toString(),
            )
        )
        binding.btnSave.text = "Save"
        binding.btnClear.text = "Clear"
        isListItemClicked = false
    }

    private fun deleteStudentData() {
        viewModel.deleteStudentData(
            Student(
                selectedStudent.id,
                binding.etName.text.toString(),
                binding.etEmail.text.toString(),
            )
        )
        binding.btnSave.text = "Save"
        binding.btnClear.text = "Clear"
        isListItemClicked = false
    }

    private fun clearInput() {
        binding.etName.text.toString()
        binding.etEmail.text.toString()
    }

    private fun initRecyclerView() {
        binding.rvStudents.layoutManager = LinearLayoutManager(this)
        adapter = StudentRecyclerViewAdapter { selectedItem: Student ->
            listItemClicked(selectedItem)
        }
        binding.rvStudents.adapter = adapter

        displayStudentList()


    }


    private fun listItemClicked(student: Student) {
        selectedStudent = student
        binding.btnSave.text = "Update"
        binding.btnClear.text = "Delete"
        isListItemClicked = true

        binding.etName.setText(selectedStudent.name)
        binding.etEmail.setText(selectedStudent.email)
    }


    /**
     * @initializeGoogleDriveService for checking google account already login or not
     */
    private fun initializeGoogleDriveService() {
        Log.e(TAG, "initializeGoogleDriveService: true")
        val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(applicationContext)

        if (googleSignInAccount != null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                this, Collections.singleton(Scopes.DRIVE_FILE)
            )
            credential.selectedAccount = googleSignInAccount.account

            // Initialize googleDriveService
            googleDriveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(), GsonFactory(), credential
            ).setApplicationName(getString(R.string.app_name)).build()
        } else {
            Log.e(TAG, "Google account not available")
        }
    }

    /**
     * @uploadDatabaseToDrive for upload data on drive
     */
    private fun uploadDatabaseToDrive() {
        // Check if googleDriveService is initialized before using it
        if (::googleDriveService.isInitialized) {
            // Use Kotlin Coroutines to perform the operation on a background thread
            GlobalScope.launch(Dispatchers.IO) {
                // Delete existing files
                try {
                    googleDriveService.files().list().setSpaces("appDataFolder")
                        .setFields("nextPageToken, files(id, name)").setPageSize(10)
                        .execute().files.filter { it.name.startsWith("student_details_database_drive.db") }
                        .forEach { file ->
                            googleDriveService.files().delete(file.id).execute()
                            Log.d(TAG, "Deleted existing file: ${file.name}")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting existing files", e)
                }

                // Upload new files
                val storageFile = File().apply {
                    parents = Collections.singletonList("appDataFolder")
                    name = "student_details_database_drive.db"
                }

                val storageFileShm = File().apply {
                    parents = Collections.singletonList("appDataFolder")
                    name = "student_details_database_drive.db-shm"
                }
                val storageFileWal = File().apply {
                    parents = Collections.singletonList("appDataFolder")
                    name = "student_details_database_drive.db-wal"
                }

                val storageFilePass = File().apply {
                    parents = Collections.singletonList("appDataFolder")
                    name = "password.txt"
                }

                val filePath = java.io.File(dbPath)
                val filePathShm = java.io.File(dbPathShm)
                val filePathWal = java.io.File(dbPathWal)
                val filePathPass = java.io.File(passPath)

                val mediaContent = FileContent("", filePath)
                val mediaContentShm = FileContent("", filePathShm)
                val mediaContentWal = FileContent("", filePathWal)
                val mediaContentPass = FileContent("", filePathPass)

                try {
                    val file =
                        googleDriveService.files().create(storageFile, mediaContent).execute()
                    Log.d(TAG, "Uploaded file: ${file.name}")

                    val fileShm =
                        googleDriveService.files().create(storageFileShm, mediaContentShm).execute()
                    Log.d(TAG, "Uploaded file: ${fileShm.name}")

                    val fileWal =
                        googleDriveService.files().create(storageFileWal, mediaContentWal).execute()
                    Log.d(TAG, "Uploaded file: ${fileWal.name}")

                    try {
                        val filePass =
                            googleDriveService.files().create(storageFilePass, mediaContentPass)
                                .execute()
                        Log.d(TAG, "Uploaded file: ${filePass.name}")
                    } catch (e: UserRecoverableAuthIOException) {
                        startActivityForResult(e.intent, 1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Log.e(TAG, "Error uploading password file to Google Drive", e)
                    }

                    launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity, "Database uploaded successfully", Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG, "Error uploading to Google Drive", e)
                }
            }
        } else {
            signInToGoogle()
        }
    }


    /**
     * @signInToGoogle google sign in method
     */
    private fun signInToGoogle() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(Scopes.DRIVE_FILE), appDataScope).requestEmail()
            .requestScopes(appDataScope).build()

        val googleSignInClient = GoogleSignIn.getClient(this, signInOptions)
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, REQUEST_CODE_SIGN_IN)
    }

    // Handle the result of the Google Sign-In flow
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                initializeGoogleDriveService()
            } else {
                // Sign-in failed or user canceled
                // You might want to handle this case appropriately
                Log.e(TAG, "Google Sign-In failed or canceled")
            }
        }
    }


    private fun download() {


        Log.e(TAG, "download: $appDataDir")
        val restoreDir = "$appDataDir/databases/restore"
        val restoreDbPath = "$restoreDir/student_details_database_drive.db"
        val restoreDbPathShm = "$restoreDir/student_details_database_drive.db-shm"
        val restoreDbPathWal = "$restoreDir/student_details_database_drive.db-wal"
        val restoreDbPathPass = "$restoreDir/password.txt"
        Log.e(TAG, "Restore path: $restoreDbPath")

        File(restoreDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }

        Log.e(TAG, "download: true")
        GlobalScope.launch(Dispatchers.IO) {
            try {

                // Show progress bar and "Wait for sync" message
                // Show progress bar and "Wait for sync" message
                withContext(Dispatchers.Main) {
                    binding.progressbar.visibility = View.VISIBLE
                }

                delay(2000)


                val dir = java.io.File(dbPath)
                if (dir.isDirectory) {
                    val children = dir.listFiles()
                    children?.forEach { file ->
                        file.delete()

                    }
                } else {
                    Log.e(TAG, "Not a Directory : ${dir.absoluteFile} ")
                }


                val files = googleDriveService.files().list().setSpaces("appDataFolder")
                    .setFields("nextPageToken, files(id, name, createdTime)").setPageSize(10)
                    .execute()

                if (files.files.size == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity, "No Database file exists in Drive", Toast.LENGTH_LONG
                        ).show()
                    }

                }
// Assuming file.createdTime is of type com.google.api.client.util.DateTime
                val maxFileCreationTime = files.files?.maxByOrNull { it.createdTime?.value ?: 0L }?.createdTime?.value ?: 0L
                Log.e(TAG, "maxFileCreationTime $maxFileCreationTime ", )
                // Storing the latest creation time in SharedPreferences
                val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                val storedCreationTime = sharedPreferences.getLong("maxFileCreationTime", 0L)

                if (maxFileCreationTime > storedCreationTime) {


                    // Save the new max creation time in SharedPreferences
                    sharedPreferences.edit().putLong("maxFileCreationTime", maxFileCreationTime).apply()

                    files.files?.forEach { file ->
                        println("Found file: ${file.name} (${file.id}) ${file.createdTime}")

                        when (file.name) {
                            "student_details_database_drive.db" -> {
                                val outputStream = FileOutputStream(restoreDbPath)
                                googleDriveService.files().get(file.id)
                                    .executeMediaAndDownloadTo(outputStream)
                            }
                            "student_details_database_drive.db-shm" -> {
                                val outputStream = FileOutputStream(restoreDbPathShm)
                                googleDriveService.files().get(file.id)
                                    .executeMediaAndDownloadTo(outputStream)
                            }
                            "student_details_database_drive.db-wal" -> {
                                val outputStream = FileOutputStream(restoreDbPathWal)
                                googleDriveService.files().get(file.id)
                                    .executeMediaAndDownloadTo(outputStream)
                            }
                            "password.txt" -> {
                                val outputStreamPass = FileOutputStream(restoreDbPathPass)
                                googleDriveService.files().get(file.id)
                                    .executeMediaAndDownloadTo(outputStreamPass)
                            }
                        }
                    }


                    delay(5000)  // Adjust the delay duration as needed
                    val passwordFile = File(restoreDbPathPass)
                    val passwordContent = passwordFile.readText()
                    Log.e(TAG, "restored password for open db is >>> $passwordContent ")
                    val restoredDatabaseHelper =
                        RestoredDatabaseHelper(applicationContext, restoreDbPath, passwordContent)
                    val selectQuery = "SELECT * FROM tbl_student_details"
                    val cursor = restoredDatabaseHelper.executeSelectQuery(selectQuery)

                    cursor?.use {
                        val idIndex = it.getColumnIndex("student_id")
                        val nameIndex = it.getColumnIndex("student_name")
                        val emailIndex = it.getColumnIndex("student_email")

                        while (it.moveToNext()) {
                            val id = if (idIndex != -1) it.getInt(idIndex) else -1
                            val name = if (nameIndex != -1) it.getString(nameIndex) else ""
                            val email = if (emailIndex != -1) it.getString(emailIndex) else ""
                            Log.d(TAG, "Restored Data: ID=$id, Name=$name, Email=$email")

                            // Create a Student object from the restored data
                            val restoredStudent = Student(0, name, email)

                            // Insert the restored data into the main database
                            viewModel.insertStudentData(restoredStudent)
                        }
                    }

                    restoredDatabaseHelper.close()

//                // Call openAndInsertData to read and insert data from the downloaded files

                    withContext(Dispatchers.Main) {
                        binding.progressbar.visibility = View.INVISIBLE
                        Toast.makeText(
                            this@MainActivity, "Database Restore Successfully!!", Toast.LENGTH_LONG
                        ).show()
                        // Display the updated list after the data is downloaded
                        displayStudentList()
                    }
                }else{
                    // Files downloaded with the same or older creation time, show a message or perform relevant action
                    withContext(Dispatchers.Main) {
                        binding.progressbar.visibility = View.INVISIBLE
                        Toast.makeText(this@MainActivity, "Database already synced", Toast.LENGTH_SHORT).show()
                    }

                }

            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, "Error downloading data from Google Drive", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error downloading data from Google Drive",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun displayStudentList() {
        viewModel.student.observe(this) { studentList ->
            adapter.setList(studentList)
            Log.e(TAG, "displayStudentList:Student List >>> ${studentList.size}")
            adapter.notifyDataSetChanged()
        }
    }

    private fun passwordGenerator(): String {
        val pass = UUID.randomUUID()
        Log.e(TAG, "passwordGenerator: ${pass.toString()}")
        return pass.toString()
    }

    private fun saveValueToFile(value: String) {
        val filePath = "$appDataDir/databases/password.txt"

        try {
            val file = File(filePath)
            val fileWriter = FileWriter(file)
            fileWriter.write(value)
            fileWriter.flush()
            fileWriter.close()

            // Log success or perform other actions if needed
            Log.d(TAG, "Value saved to file: $value")
        } catch (e: Exception) {
            // Handle exceptions
            Log.e(TAG, "Error saving value to file", e)
        }
    }


}
