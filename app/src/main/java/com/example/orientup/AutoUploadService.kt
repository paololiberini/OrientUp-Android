package com.example.orientup

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.NullPointerException
import java.security.MessageDigest
import java.util.*
import kotlin.concurrent.timerTask


class AutoUploadService : Service() {

    private var timer = Timer()
    private var md5Old : String = ""
    private val channelId = "Notification from Service"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }


    override fun onCreate() {
        super.onCreate()

        println("Service started")

        Toast.makeText(this, SERVICE_STARTED, Toast.LENGTH_LONG).show()

        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OrientUp Service")
            .setContentText("Service is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)

        MainActivity.serviceRunning = true
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        println("MD5OLD: $md5Old")

        val selectedPath: Uri? = intent?.data
        val competitionId = intent?.extras?.getString(MainActivity.COMPETITION_ID)
        println("Service path: $selectedPath id: $competitionId")
        var n = 0

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoUploadService::lock").apply {
                    acquire(300*60*1000L /*300 minutes*/)
                    println("Service wakelock acquired")
                }
        }

        timer.scheduleAtFixedRate(timerTask {

            try {
                val xmlFilePath = DocumentFile.fromTreeUri(applicationContext, selectedPath!!)?.findFile("results.xml")?.uri
                val xmlFile = readXML(xmlFilePath)
                println(competitionId.toString())
                println(md5(xmlFile))
                val md5New = md5(xmlFile)

                if(md5New != md5Old) {
                    println("File changed")
                    uploadResultsService(xmlFile, competitionId!!, md5New)
                }
            } catch (e: NullPointerException) {
                println("File not found")

                sendNotification(1)
            }

            println(n++.toString())
        }, 0, 60000)


        return super.onStartCommand(intent, flags, startId)
    }


    private fun sendNotification(code: Int) {
        when(code) {
            0 -> {
                val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("OrientUp Service")
                    .setContentText(MainActivity.TOAST_ERROR_ON_FAILURE)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(100, notification)
                }
            }
            1 -> {
                val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("OrientUp Service")
                    .setContentText(FILE_NOT_FOUND)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(100, notification)
                }
            }
            409 -> {
                val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("OrientUp Service")
                    .setContentText(MainActivity.TOAST_CONFLICT)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(100, notification)
                }
            }
            200, 201 -> {
                val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("OrientUp Service")
                    .setContentText(MainActivity.TOAST_SUCCESS_UPLOAD)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(100, notification)
                }
            }
            else -> {
                val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("OrientUp Service")
                    .setContentText("Server error")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()

                with(NotificationManagerCompat.from(applicationContext)) {
                    notify(100, notification)
                }
            }
        }
    }


    private fun md5(input : String) : String {
        val messageDigest = MessageDigest.getInstance("MD5")

        return messageDigest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }


    private fun uploadResultsService(postBody: String, competitionId: String, md5New: String) {

        val client = OkHttpClient()

        val request = Request.Builder()
            .url(MainActivity.URL)
            .addHeader("competition-code", competitionId)
            .addHeader("connection", "close")
            .post(postBody.toRequestBody("text/xml".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("OnFailure")
                e.printStackTrace()

                sendNotification(0)
            }

            override fun onResponse(call: Call, response: Response) {
                println("OnResponse")
                response.use {
                    if (!response.isSuccessful) {
                        println(response.code)

                        sendNotification(response.code)

                        println(response.body!!.string())

                        throw IOException("Unexpected code $response")
                    }

                    for ((name, value) in response.headers) {
                        println("$name: $value")
                    }


                    if(response.code == 200 || response.code == 201) {
                        md5Old = md5New
                        sendNotification(response.code)
                    }

                    client.dispatcher.executorService.shutdown()

                    println(response.body!!.string())
                    println(response.code)
                }
            }
        })
    }


    private fun readXML(uri: Uri?): String {
        val xmlFileStream = uri?.let { contentResolver.openInputStream(it) }
        val inputStreamReader = InputStreamReader(xmlFileStream)

        return BufferedReader(inputStreamReader).readText()
    }


    override fun onDestroy() {
        super.onDestroy()
        println("Service destroyed")

        timer.cancel()
        timer.purge()

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                println("Service wakelock released")
            }
        }

        MainActivity.serviceRunning = false

        Toast.makeText(this, SERVICE_STOPPED, Toast.LENGTH_LONG).show()
    }


    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(channelId, "Active", NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }


    companion object {
        const val FILE_NOT_FOUND = "File to upload not found"
        const val SERVICE_STARTED = "Service started"
        const val SERVICE_STOPPED = "Service stopped"
    }
}