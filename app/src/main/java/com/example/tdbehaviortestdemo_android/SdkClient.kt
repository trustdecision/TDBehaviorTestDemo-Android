package com.TrustDecision.TDBehaviorTestDemo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.trustdecision.mobrisk.TDRisk
import com.trustdecision.tdbehavior.TDBehavior
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

internal object SdkClient {
    private const val TAG = "TDBehaviorShowcase"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "td-demo-risk-query").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var initializationError: String? = null
        private set

    @Volatile
    private var initialized = false

    @Synchronized
    fun initializeAndStart(context: Context) {
        if (initialized) return
        try {
            val behaviorOptions = linkedMapOf<String, Any>(
                "partner" to DemoConfig.PARTNER,
                "partnerKey" to DemoConfig.PARTNER_KEY,
                "appKey" to DemoConfig.APP_KEY,
                "country" to DemoConfig.COUNTRY.lowercase(),
                "behaviorHost" to DemoConfig.BEHAVIOR_HOST,
                "behaviorConfigUrl" to DemoConfig.BEHAVIOR_CONFIG_URL,
                "behaviorUploadUrl" to DemoConfig.BEHAVIOR_UPLOAD_URL,
                "behaviorHttpTimeOut" to DemoConfig.BEHAVIOR_HTTP_TIMEOUT_SECONDS,
                "behaviorCollectTimeOut" to DemoConfig.BEHAVIOR_COLLECT_TIMEOUT_SECONDS,
                "behaviorKeepNaviInfo" to DemoConfig.BEHAVIOR_KEEP_NAVI_INFO
            )
            TDBehavior.initWithOptions(context.applicationContext, behaviorOptions)

            val riskBuilder = TDRisk.Builder()
                .partner(DemoConfig.PARTNER)
                .appKey(DemoConfig.APP_KEY)
                .dataCenter(DemoConfig.COUNTRY)
                .behaviorHttpTimeOut(DemoConfig.BEHAVIOR_HTTP_TIMEOUT_SECONDS)
            TDRisk.initWithOptions(context.applicationContext, riskBuilder)
            TDBehavior.start()

            initialized = true
            initializationError = null
            Log.d(TAG, "TDBehavior initialized and started")
        } catch (error: Exception) {
            initializationError = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "SDK initialization failed", error)
        }
    }

    fun sdkStatus(): String {
        val error = initializationError
        return if (error == null) {
            "Behavior SDK ${TDBehavior.getSdkVersion()} · MobRisk ${TDRisk.getSDKVersion()}"
        } else {
            "SDK initialization failed: $error"
        }
    }

    fun collectAndQuery(callback: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                initializationError?.let { throw IllegalStateException("SDK initialization failed: $it") }
                if (!initialized) throw IllegalStateException("The SDK is not initialized.")

                val collected = TDRisk.collect()
                    ?: throw IllegalStateException("The SDK returned an empty collection result.")
                if (collected.code() != 0) {
                    throw IllegalStateException(
                        "Collection failed (${collected.code()}): ${collected.msg()}"
                    )
                }
                val behavior = collected.payload()
                if (behavior.isNullOrBlank()) {
                    throw IllegalStateException("The collection result does not contain behavior data.")
                }
                val blackBox = TDRisk.getBlackBox()
                if (blackBox.isNullOrBlank()) {
                    throw IllegalStateException("The SDK returned an empty black box.")
                }
                queryRisk(blackBox, behavior)
            }
            mainHandler.post { callback(result) }
        }
    }

    private fun queryRisk(blackBox: String, behavior: String): String {
        val url = URL(
            "${DemoConfig.RISK_URL}?partner_code=${encode(DemoConfig.PARTNER)}" +
                "&partner_key=${encode(DemoConfig.PARTNER_KEY)}"
        )
        val body = JSONObject()
            .put("black_box", blackBox)
            .put("behavior", behavior)
            .toString()

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = DemoConfig.HTTP_TIMEOUT_MS
            readTimeout = DemoConfig.HTTP_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val response = readText(
                if (status in 200..299) connection.inputStream else connection.errorStream
            )
            if (status !in 200..299) {
                throw IllegalStateException("Risk service returned HTTP $status.\n$response")
            }
            val json = JSONObject(response)
            val code = json.optString("code")
            if (code != "200" && code != "000") {
                throw IllegalStateException(
                    "Risk query failed: ${json.optString("message", "Unknown error")}\n${formatJson(response)}"
                )
            }
            formatJson(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun readText(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }

    internal fun formatJson(value: String): String = try {
        when (val parsed = JSONTokener(value).nextValue()) {
            is JSONObject -> parsed.toString(2)
            is JSONArray -> parsed.toString(2)
            else -> value
        }
    } catch (_: Exception) {
        value
    }
}
