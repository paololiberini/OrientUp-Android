package com.example.orientup


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.orientup.databinding.ActivityMainBinding
import okhttp3.MediaType.Companion.toMediaType
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var switchChecked : Boolean = false

    private var xmlData: String = ""
    private var competitionIdString: String = ""

    private var xmlPath : Uri? = null
    private var selectedPath : Uri? = null
    private var xmlFile : File? = null

    //private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("Create")

//Permessi di lettura
//        val permissions = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
//        ActivityCompat.requestPermissions(this, permissions,0)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if(isChecked) {
                switchChecked = true

                selectedPath = null
                xmlPath = null

                binding.uploadButton.isEnabled = false
                binding.selectXmlButton.text = getString(R.string.select_folder_text)
                binding.fileLoadedText.text = getString(R.string.folder_not_loaded_text)
                xmlPath = null
                println("Switch checked")
            } else {
                switchChecked = false

                binding.startServiceButton.isEnabled = false
                binding.stopServiceButton.isEnabled = false
                binding.selectXmlButton.text = getString(R.string.select_xml_text)
                binding.fileLoadedText.text = getString(R.string.file_not_loaded_text)
                println("Switch not checked")
            }
        }


        binding.selectXmlButton.setOnClickListener {
            if(switchChecked) {
                pickScanFolder()
            } else {
                pickXmlFile()
            }
        }


        binding.editTextNumber.addTextChangedListener( object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                if(switchChecked) {
                    if(binding.editTextNumber.text.toString() == "" || selectedPath == null) {
                        binding.startServiceButton.isEnabled = false
                    } else {
                        binding.startServiceButton.isEnabled = true
                    }
                    competitionIdString = binding.editTextNumber.text.toString()
                } else {
                    if(binding.editTextNumber.text.toString() == "" || xmlPath == null) {
                        binding.uploadButton.isEnabled = false
                    } else {
                        binding.uploadButton.isEnabled = true
                    }
                    competitionIdString = binding.editTextNumber.text.toString()
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        binding.uploadButton.setOnClickListener { uploadTap() }

        binding.startServiceButton.setOnClickListener {
            val intent = Intent(this, AutoUploadService::class.java)
            intent.putExtra(COMPETITION_ID, competitionIdString)
            intent.data = selectedPath

            binding.stopServiceButton.isEnabled = true
            binding.serviceSwitch.isEnabled = false
            binding.editTextNumber.isEnabled = false
            binding.selectXmlButton.isEnabled = false
            binding.startServiceButton.isEnabled = false

//            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
//                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MainActivity::lock").apply {
//                    acquire(300*60*1000L)
//                    println("Wakelock acquired")
//                }
//            }

            ContextCompat.startForegroundService(this, intent)
        }

        binding.stopServiceButton.setOnClickListener {
            val intent = Intent(this, AutoUploadService::class.java)

            binding.stopServiceButton.isEnabled = false
            binding.serviceSwitch.isEnabled = true
            binding.editTextNumber.isEnabled = true
            binding.selectXmlButton.isEnabled = true
            binding.startServiceButton.isEnabled = true

            stopService(intent)

//            try {
//                wakeLock?.let {
//                    if (it.isHeld) {
//                        it.release()
//                        println("Wakelock released")
//                    }
//                }
//            } catch (e: Exception) {
//                println("Error stopping wakelock")
//            }
        }

        when(intent?.action) {
            Intent.ACTION_SEND -> {
                if ("text/xml" == intent.type) {
                    println("Intent riconosciuto")
                    handleSendXml(intent)
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        println("Start")
        println("Switch checked: $switchChecked")
        println("Service running: $serviceRunning")
        println("xmlPath: $xmlPath")
        println("Competition id string: $competitionIdString")
        println("Selected path: $selectedPath")

//        if(serviceRunning) {
//            binding.startServiceButton.isEnabled = false
//        }

    }


    private val pickXmlFileActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == Activity.RESULT_OK) {
            result.data.let { intent ->
                xmlPath = intent?.data
                xmlFile = File(intent?.data.toString())

                println(xmlPath?.toString())
                xmlData = readXML(intent?.data)
                println(xmlData)
                if(xmlData == "") {
                    binding.fileLoadedText.text = MSG_FILE_EMPTY
                } else if(xmlData != "" && binding.editTextNumber.text.toString() != "") {
                    binding.fileLoadedText.text = MSG_FILE_LOADED
                    binding.uploadButton.isEnabled = true
                } else {
                    binding.fileLoadedText.text = MSG_FILE_LOADED
                }
            }
        }
    }


    private val pickScanFolderActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == Activity.RESULT_OK) {
            result.data.let { intent ->
                selectedPath = intent?.data

                if(selectedPath == null) {
                    binding.fileLoadedText.text = MSG_FOLDER_NOT_LOADED
                } else if(selectedPath != null && binding.editTextNumber.text.toString() != "") {
                    binding.fileLoadedText.text = MSG_FOLDER_LOADED
                    binding.startServiceButton.isEnabled = true
                } else {
                    binding.fileLoadedText.text = MSG_FOLDER_LOADED
                }
                println(selectedPath.toString())
            }
        }
    }


    private fun readXML(uri: Uri?): String {
        val xmlFileStream = uri?.let { contentResolver.openInputStream(it) }
        val inputStreamReader = InputStreamReader(xmlFileStream)

        return BufferedReader(inputStreamReader).readText()
    }


    private fun pickXmlFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/xml"
        }

        pickXmlFileActivity.launch(intent)
    }


    private fun pickScanFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        }

        pickScanFolderActivity.launch(intent)
    }


    private fun handleSendXml(intent: Intent) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
            xmlPath = it
            xmlData = readXML(it)
            println(xmlData)

            if(xmlData == "") {
                binding.fileLoadedText.text = MSG_FILE_EMPTY
            } else if(xmlData != "" && binding.editTextNumber.text.toString() != "") {
                binding.fileLoadedText.text = MSG_FILE_LOADED
                binding.uploadButton.isEnabled = true
            } else {
                binding.fileLoadedText.text = MSG_FILE_LOADED
            }
        }
    }


    private fun uploadTap() {
        Log.d("Upload tapped", "Tapped")
        uploadResults(xmlData)
    }


    private fun displayResult(code : Int) {
        when(code) {
            0 -> Toast.makeText(this, TOAST_ERROR_ON_FAILURE, Toast.LENGTH_LONG).show()
            200 -> {
                Toast.makeText(this, TOAST_SUCCESS_UPDATE, Toast.LENGTH_LONG).show()
                xmlData = ""
                xmlPath = null
                binding.editTextNumber.setText("")
                binding.fileLoadedText.text = ""
            }
            201 -> {
                Toast.makeText(this, TOAST_SUCCESS_UPLOAD, Toast.LENGTH_LONG).show()
                xmlData = ""
                xmlPath = null
                binding.editTextNumber.setText("")
                binding.fileLoadedText.text = ""
            }
            404 -> {
                Toast.makeText(this, TOAST_404_NOT_FOUND, Toast.LENGTH_LONG).show()
            }
            409 -> {
                Toast.makeText(this, TOAST_CONFLICT, Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun uploadResults(postBody: String) {

        val client = OkHttpClient()

        val request = Request.Builder()
            .url(URL)
            .addHeader("competition-code", competitionIdString)
            .addHeader("connection", "close")
            .post(postBody.toRequestBody("text/xml".toMediaType()))
            .build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("OnFailure")
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    displayResult(0)
                }

            }

            override fun onResponse(call: Call, response: Response) {
                println("OnResponse")
                response.use {
                    if (!response.isSuccessful) {
                        println(response.code)

                        Handler(Looper.getMainLooper()).post {
                            displayResult(response.code)
                        }

                        println(response.body!!.string())

                        throw IOException("Unexpected code $response")
                    }

                    for ((name, value) in response.headers) {
                        println("$name: $value")
                    }

                    Handler(Looper.getMainLooper()).post {
                        displayResult(response.code)
                    }

                    client.dispatcher.executorService.shutdown()

                    println(response.body!!.string())
                    println(response.code)
                }
            }
        })
    }

//    private fun releaseWakelock() {
//        try {
//            wakeLock?.let {
//                if (it.isHeld) {
//                    it.release()
//                    println("Wakelock released")
//                }
//            }
//        } catch (e: Exception) {
//            println("Error releasing wakelock")
//        }
//    }


    override fun onDestroy() {
        super.onDestroy()
        println("Destroy")

        val intent = Intent(this, AutoUploadService::class.java)
        stopService(intent)

        //releaseWakelock()

        switchChecked = false
    }


    override fun onPause() {
        super.onPause()
        println("Pause")
    }


    override fun onStop() {
        super.onStop()
        println("Stop")
    }


    override fun onResume() {
        super.onResume()
        println("Resume")
    }


    companion object {
        const val TOAST_ERROR_ON_FAILURE = "Error: check internet connection"
        const val TOAST_SUCCESS_UPLOAD = "File uploaded"
        const val TOAST_SUCCESS_UPDATE = "File updated"
        const val TOAST_CONFLICT = "Error: competition code not found"
        const val TOAST_404_NOT_FOUND = "404: Not found"

        const val MSG_FILE_LOADED = "File loaded"
        const val MSG_FILE_EMPTY = "Warning: empty file"
        const val MSG_FOLDER_LOADED = "Folder loaded"
        const val MSG_FOLDER_NOT_LOADED = "Folder not loaded"

        const val SELECTED_PATH = "selectedPath"
        const val COMPETITION_ID = "competitionId"

        const val URL = "http://10.0.2.2:3000/appAPI/sendXml"

        var serviceRunning : Boolean = false
    }
}