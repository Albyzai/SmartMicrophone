package com.example.smartmicrophone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.android.volley.*
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.net.URL
import android.R.string.no
import android.os.StrictMode
import android.util.ArrayMap
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection


private const val LOG_TAG = "AudioRecordTest"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

class MainActivity : AppCompatActivity() {


    /**
     * requires a little of noise by the user to trigger, background noise may
     * trigger it
     */
    val AMPLITUDE_DIFF_LOW = 10000
    val AMPLITUDE_DIFF_MED = 18000

    /**
     * requires a lot of noise by the user to trigger. background noise isn't
     * likely to be this loud
     */
    val AMPLITUDE_DIFF_HIGH = 25000

    private var fileName: String = ""

    private var recordButton: RecordButton? = null
    private var recorder: MediaRecorder? = null

    private var player: MediaPlayer? = null
    private var isRecording = false
    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }

    private fun onRecord(start: Boolean) = if (start) {
        startRecording()
    } else {
        stopRecording()
    }


    private fun startRecording() {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(LOG_TAG, "prepare() failed")
            }

            start()
            isRecording = true
        }
        monitorAmplitudeAndSendAlert()

    }

    private fun monitorAmplitudeAndSendAlert() {
        val handler = Handler(Looper.getMainLooper())
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        handler.postDelayed({
            while(isRecording){
                var amp = recorder?.maxAmplitude

                when {
                    amp!! >= AMPLITUDE_DIFF_LOW -> { createRequest("A low noise has been detected") }
                    amp!! >= AMPLITUDE_DIFF_MED -> { createRequest("A medium noise has been detected")}
                    amp!! >= AMPLITUDE_DIFF_HIGH -> { createRequest("A loud noise has been detected")}
                }
            }
        }, 10000)
    }

    private fun createRequest(title: String) {
        val queue = Volley.newRequestQueue(this)
        val url = "https://fcm.googleapis.com/fcm/send"

        val body = JSONObject()
        val notification = JSONObject()
        val data = JSONObject()

        data.put("extra_information", "This is some extra information")
        notification.put("title", title)
        notification.put("body","This is the notification message")

        body.put("to", "/topics/alert")
        body.put("data", data)
        body.put("notification", notification)

        val request: JsonObjectRequest = object : JsonObjectRequest(Method.POST, url, body,
            Response.Listener { res ->
                println(res.toString())
            },
            Response.ErrorListener { err ->
                println(err.toString())
            }) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                var params = mutableMapOf<String, String>()

                if(!super.getHeaders().isNullOrEmpty()){ params = super.getHeaders() }
                params["Content-Type"] = "application/json"
                params["Authorization"] =
                    "key=AAAAuruC1Kc:APA91bE8kFHjjULWk8hGweQYon9sbzDvSfvh8b0x-X-SaZ8gNKC6d6MQU3OFkImy-aCA7xxb26JvKfZjiY6oj-fLpBl6dfB3YED-GzmTCW6_1k3BAvZwDnOwDGtumkxg4f0pV-7Flzb3"


                //..add other headers
                return params
            }
        }
        queue.add(request)
    }

    private fun stopRecording() {
        recorder?.apply {
            stop()
            release()
            isRecording = false
        }
        recorder = null
    }

    internal inner class RecordButton(ctx: Context) : androidx.appcompat.widget.AppCompatButton(ctx) {

        var mStartRecording = true

        var clicker: OnClickListener = OnClickListener {
            onRecord(mStartRecording)
            text = when (mStartRecording) {
                true -> "Stop recording"
                false -> "Start recording"
            }
            mStartRecording = !mStartRecording
        }

        init {
            text = "Start recording"
            setOnClickListener(clicker)
        }
    }



    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Record to the external cache directory for visibility
        fileName = "${externalCacheDir?.absolutePath}/audiorecordtest.3gp"

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        recordButton = RecordButton(this)
        val ll = LinearLayout(this).apply {
            addView(recordButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0f))
        }
        setContentView(ll)
    }


}