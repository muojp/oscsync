package jp.muo.oscsync

import android.util.Log
import awaitStringResult
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class OSCClient(val server: String) {

    companion object {
        const val PROGRESS_CHECK_INTERVAL: Long = 250
        const val PROGRESS_CHECK_RETRY = 20
    }

    suspend fun isCompatibleCamera(): Boolean {
        val result = Fuel.get("http://${this.server}/osc/info").awaitStringResult()
        if (result !is Result.Success) {
            return false
        }
        val info = Gson().fromJson(result.value, OSCDeviceInfo::class.java)
        if (info == null) {
            return false
        }
        Log.d(MainActivity.TAG, "Model: ${info.model}")
        return info.model.contains(MainActivity.CAMERA_IDENTIFIER)
    }

    suspend fun postCommand(data: Any) = Fuel.post("http://${this.server}/osc/commands/execute").body(Gson().toJson(data)).awaitStringResult()

    suspend fun fetchStatus(id: String) = Fuel.post("http://${this.server}/osc/commands/status").body(Gson().toJson(StatusCommand(id))).awaitStringResult()

    inline fun <reified T> parseCommandResponse(text: String): T = Gson().fromJson<T>(text, object : TypeToken<T>() {}.type)

    data class OSCDeviceInfo(
            val manufacturer: String,
            val model: String,
            val serialNumber: String,
            val firmwareVersion: String,
            val supportUrl: String,
            val gps: Boolean,
            val gyro: Boolean,
            val uptime: Int,
            val api: List<String>,
            // endpoints
            val apiLevel: List<Int>
            // vendorSpecific
    )

    data class CommandRequest<T>(
            val name: String,
            val parameters: T?
    )

    data class StartSessionParameters(
            val timeout: Int?
    )

    data class TakePictureParameters(
            val sessionId: String
    )

    data class CommandResponse<T>(
            val name: String,
            val state: String,
            val id: String?,
            val results: T?,
            val error: CommandError?,
            val progress: CommandProgress?
    )

    data class CommandProgress(val completion: Double)

    data class StartSessionResponse(
            val sessionId: String,
            val timeout: Int
    )

    data class TakePictureResponse(
            val fileUri: String
    )

    data class CommandError(
            val code: String,
            val message: String
    )

    data class StatusCommand(
            val id: String
    )

    var sessionId: String? = null

    suspend fun performTakePicture() {
        if (!this.isCompatibleCamera()) {
            return
        }
        this.startSession()
        if (this.sessionId == null) {
            // TODO: needs error handling
            return
        }
        val fileUri = this.takePicture()
        Log.d(MainActivity.TAG, "Picture taken: ${fileUri}")
    }

    private suspend fun startSession() {
        val startSessionRequest = CommandRequest(
                name = "camera.startSession",
                parameters = StartSessionParameters(timeout = 50))
        val result = postCommand(startSessionRequest)
        if (result !is Result.Success) {
            return
        }
        val r: CommandResponse<StartSessionResponse> = parseCommandResponse(result.value)
        if (r.results == null || r.error != null) {
            return
        }
        this.sessionId = r.results.sessionId
        Log.d(MainActivity.TAG, "Session ID: ${this.sessionId}")
    }

    private suspend fun takePicture(): String {
        if (this.sessionId == null) {
            return ""
        }
        val takePictureRequest = CommandRequest(
                name = "camera.takePicture",
                parameters = TakePictureParameters(sessionId = this.sessionId!!)
        )
        val result = postCommand(takePictureRequest)
        if (result !is Result.Success) {
            return ""
        }
        val progress: CommandResponse<Unit> = parseCommandResponse(result.value)
        var picResponse: CommandResponse<TakePictureResponse>? = null
        if (progress.state == "inProgress" && progress.id != null) {
            Log.d(MainActivity.TAG, "Taking a picture")
            for (i in 0..PROGRESS_CHECK_RETRY) {
                Log.d(MainActivity.TAG, ".")
                Thread.sleep(PROGRESS_CHECK_INTERVAL)
                val status = fetchStatus(progress.id)
                if (status !is Result.Success) {
                    continue
                }
                val statusInfo: CommandResponse<Unit> = parseCommandResponse(status.value)
                when (statusInfo.state) {
                    "done" -> picResponse = parseCommandResponse(status.value)
                    "inProgress" -> Log.d(MainActivity.TAG, "progress: " + statusInfo.progress?.completion.toString())
                    "error" -> {
                    } // TODO: handle error
                }
                if (picResponse != null) {
                    break
                }
            }
        }
        if (picResponse != null) {
            return picResponse.results!!.fileUri
        }
        return ""
    }
}