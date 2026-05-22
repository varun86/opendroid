package com.opendroid.ai.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.StatFs
import android.provider.MediaStore
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.opendroid.ai.accessibility.GenericAppAutomator
import com.opendroid.ai.actions.base.Action
import com.opendroid.ai.actions.base.ActionResult
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class AdvancedControlActions @Inject constructor() {

    companion object {
        private fun hasStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

        private fun checkStoragePermission(context: Context): ActionResult? {
            if (!hasStoragePermission(context)) {
                return ActionResult(false, null, "Storage / Files access permission is not granted. Please enable it in the app onboarding or system settings.")
            }
            return null
        }
    }

    fun getActions(): List<Action> = listOf(
        GetSystemInfoAction(),
        SetRingerModeAction(),
        ListFilesAction(),
        ReadFileAction(),
        WriteFileAction(),
        DeleteFileAction(),
        TakePhotoBackgroundAction(),
        ListInstalledAppsAction(),
        CloseAppAction(),
        ClickTextAction(),
        ClickIdAction(),
        TypeTextAction(),
        TypeIdAction(),
        ScrollAction(),
        GetScreenTextAction(),
        ClickCoordinatesAction()
    )

    private class GetSystemInfoAction : Action {
        override val name: String = "GET_SYSTEM_INFO"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            return try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val isCharging = batteryManager.isCharging

                val stat = StatFs(Environment.getDataDirectory().path)
                val totalStorage = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
                val freeStorage = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)

                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                val totalMem = memInfo.totalMem / (1024 * 1024)
                val availMem = memInfo.availMem / (1024 * 1024)

                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = connManager.activeNetworkInfo
                val isConnected = activeNetwork?.isConnectedOrConnecting == true
                val networkType = activeNetwork?.typeName ?: "UNKNOWN"

                val info = """
                    Battery: $batteryPct% (Charging: $isCharging)
                    Storage: Free $freeStorage GB / Total $totalStorage GB
                    Memory: Free $availMem MB / Total $totalMem MB
                    Network: Connected=$isConnected (Type=$networkType)
                    OS Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                    Device: ${Build.MANUFACTURER} ${Build.MODEL}
                """.trimIndent()

                ActionResult(true, info, null)
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to get system info: ${e.localizedMessage}")
            }
        }
    }

    private class SetRingerModeAction : Action {
        override val name: String = "SET_RINGER_MODE"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val modeStr = params["mode"] ?: "normal"
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val targetMode = when (modeStr.lowercase()) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            return try {
                audioManager.ringerMode = targetMode
                ActionResult(true, "Ringer mode set to $modeStr", null)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(false, "Failed to set ringer mode to $modeStr directly. Prompted for DND permission.", e.localizedMessage, true)
            }
        }
    }

    private class ListFilesAction : Action {
        override val name: String = "LIST_FILES"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            checkStoragePermission(context)?.let { return it }
            val pathStr = params["path"] ?: Environment.getExternalStorageDirectory().absolutePath
            return try {
                val dir = File(pathStr)
                if (!dir.exists()) {
                    return ActionResult(false, null, "Directory does not exist: $pathStr")
                }
                if (!dir.isDirectory) {
                    return ActionResult(false, null, "Path is not a directory: $pathStr")
                }
                val files = dir.listFiles() ?: emptyArray()
                val fileList = files.joinToString("\n") { file ->
                    val type = if (file.isDirectory) "DIR" else "FILE"
                    "${file.name} [$type] (${file.length()} bytes)"
                }
                ActionResult(true, if (fileList.isEmpty()) "Directory is empty." else fileList, null)
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to list files: ${e.localizedMessage}")
            }
        }
    }

    private class ReadFileAction : Action {
        override val name: String = "READ_FILE"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            checkStoragePermission(context)?.let { return it }
            val filePath = params["filePath"] ?: return ActionResult(false, null, "filePath parameter is missing")
            return try {
                val file = File(filePath)
                if (!file.exists()) {
                    return ActionResult(false, null, "File does not exist: $filePath")
                }
                if (file.isDirectory) {
                    return ActionResult(false, null, "Path is a directory, not a file: $filePath")
                }
                if (file.length() > 100 * 1024) {
                    return ActionResult(false, null, "File exceeds size limit of 100KB: $filePath")
                }
                val text = file.readText()
                ActionResult(true, text, null)
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to read file: ${e.localizedMessage}")
            }
        }
    }

    private class WriteFileAction : Action {
        override val name: String = "WRITE_FILE"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            checkStoragePermission(context)?.let { return it }
            val filePath = params["filePath"] ?: return ActionResult(false, null, "filePath parameter is missing")
            val content = params["content"] ?: ""
            return try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                file.writeText(content)
                ActionResult(true, "Successfully wrote content to $filePath", null)
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to write file: ${e.localizedMessage}")
            }
        }
    }

    private class DeleteFileAction : Action {
        override val name: String = "DELETE_FILE"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            checkStoragePermission(context)?.let { return it }
            val filePath = params["filePath"] ?: return ActionResult(false, null, "filePath parameter is missing")
            return try {
                val file = File(filePath)
                if (!file.exists()) {
                    return ActionResult(false, null, "File does not exist: $filePath")
                }
                val deleted = file.delete()
                if (deleted) {
                    ActionResult(true, "Successfully deleted $filePath", null)
                } else {
                    ActionResult(false, null, "Failed to delete file (unknown reason)")
                }
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to delete file: ${e.localizedMessage}")
            }
        }
    }

    private class TakePhotoBackgroundAction : Action {
        override val name: String = "TAKE_PHOTO_BACKGROUND"

        @SuppressLint("MissingPermission")
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return launchCameraIntentFallback(context, "Camera permission missing. Launched camera app instead.")
            }
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return try {
                val cameraIdList = cameraManager.cameraIdList
                if (cameraIdList.isEmpty()) {
                    return ActionResult(false, null, "No cameras available on this device")
                }
                val cameraId = cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: cameraIdList[0]

                val photoFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "background_photo_${System.currentTimeMillis()}.jpg")
                val success = captureStillImage(context, cameraManager, cameraId, photoFile)
                if (success) {
                    ActionResult(true, "Photo captured and saved to: ${photoFile.absolutePath}", null)
                } else {
                    launchCameraIntentFallback(context, "Background capture failed. Launched camera app instead.")
                }
            } catch (e: Exception) {
                launchCameraIntentFallback(context, "Background capture error: ${e.localizedMessage}. Launched camera app instead.")
            }
        }

        private fun launchCameraIntentFallback(context: Context, msg: String): ActionResult {
            return try {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult(true, "$msg Camera app opened.", null, true)
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to launch camera app fallback: ${e.localizedMessage}")
            }
        }

        private suspend fun captureStillImage(
            context: Context,
            cameraManager: CameraManager,
            cameraId: String,
            outputFile: File
        ): Boolean = suspendCoroutine { continuation ->
            val handlerThread = HandlerThread("CameraBackgroundThread")
            handlerThread.start()
            val backgroundHandler = Handler(handlerThread.looper)

            var cameraDevice: CameraDevice? = null
            var captureSession: CameraCaptureSession? = null
            var imageReader: ImageReader? = null
            var isResumed = false

            fun cleanUp() {
                try {
                    captureSession?.close()
                    cameraDevice?.close()
                    imageReader?.close()
                    handlerThread.quitSafely()
                } catch (e: Exception) {
                    // Ignore cleanup exceptions
                }
            }

            fun resumeOnce(result: Boolean) {
                if (!isResumed) {
                    isResumed = true
                    cleanUp()
                    continuation.resume(result)
                }
            }

            try {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val sizes = map?.getOutputSizes(ImageFormat.JPEG)
                val size = sizes?.firstOrNull { it.width <= 1920 && it.height <= 1080 } ?: sizes?.firstOrNull() ?: android.util.Size(640, 480)

                imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val buffer: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()
                        try {
                            FileOutputStream(outputFile).use { it.write(bytes) }
                            resumeOnce(true)
                        } catch (e: Exception) {
                            resumeOnce(false)
                        }
                    } else {
                        resumeOnce(false)
                    }
                }, backgroundHandler)

                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        val targets = listOf(imageReader.surface)
                        camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    builder.addTarget(imageReader.surface)
                                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    
                                    session.capture(builder.build(), null, backgroundHandler)
                                } catch (e: Exception) {
                                    resumeOnce(false)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                resumeOnce(false)
                            }
                        }, backgroundHandler)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        resumeOnce(false)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        resumeOnce(false)
                    }
                }, backgroundHandler)

                // Safety timeout
                backgroundHandler.postDelayed({
                    resumeOnce(false)
                }, 8000)

            } catch (e: Exception) {
                resumeOnce(false)
            }
        }
    }

    private class ListInstalledAppsAction : Action {
        override val name: String = "LIST_INSTALLED_APPS"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            return try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val appList = apps.map { app ->
                    val label = pm.getApplicationLabel(app).toString()
                    val packageName = app.packageName
                    "$label ($packageName)"
                }.sorted().joinToString("\n")
                ActionResult(true, appList, null)
            } catch (e: Exception) {
                ActionResult(false, null, "Failed to list installed apps: ${e.localizedMessage}")
            }
        }
    }

    private class CloseAppAction : Action {
        override val name: String = "CLOSE_APP"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val success = GenericAppAutomator.pressHome()
            return ActionResult(success, if (success) "App closed (navigated to home screen)" else "Failed to close app", null)
        }
    }

    private class ClickTextAction : Action {
        override val name: String = "CLICK_TEXT"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val text = params["text"] ?: return ActionResult(false, null, "text parameter is missing")
            val success = GenericAppAutomator.clickText(text)
            return ActionResult(success, if (success) "Clicked text '$text'" else "Text '$text' not found or not clickable", null)
        }
    }

    private class ClickIdAction : Action {
        override val name: String = "CLICK_ID"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val viewId = params["viewId"] ?: return ActionResult(false, null, "viewId parameter is missing")
            val success = GenericAppAutomator.clickId(viewId)
            return ActionResult(success, if (success) "Clicked element with ID '$viewId'" else "Element with ID '$viewId' not found or not clickable", null)
        }
    }

    private class TypeTextAction : Action {
        override val name: String = "TYPE_TEXT"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val searchText = params["searchText"] ?: return ActionResult(false, null, "searchText parameter is missing")
            val content = params["content"] ?: ""
            val success = GenericAppAutomator.typeText(searchText, content)
            return ActionResult(success, if (success) "Typed '$content' into field containing '$searchText'" else "Field with '$searchText' not found or not editable", null)
        }
    }

    private class TypeIdAction : Action {
        override val name: String = "TYPE_ID"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val viewId = params["viewId"] ?: return ActionResult(false, null, "viewId parameter is missing")
            val content = params["content"] ?: ""
            val success = GenericAppAutomator.typeId(viewId, content)
            return ActionResult(success, if (success) "Typed '$content' into field with ID '$viewId'" else "Field with ID '$viewId' not found or not editable", null)
        }
    }

    private class ScrollAction : Action {
        override val name: String = "SCROLL"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val direction = params["direction"] ?: "forward"
            val forward = direction.lowercase() == "forward"
            val success = GenericAppAutomator.scroll(forward)
            return ActionResult(success, if (success) "Scrolled screen $direction" else "Screen is not scrollable", null)
        }
    }

    private class GetScreenTextAction : Action {
        override val name: String = "GET_SCREEN_TEXT"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val text = GenericAppAutomator.scrapeScreen()
            return ActionResult(true, text.ifEmpty { "No text visible on screen" }, null)
        }
    }

    private class ClickCoordinatesAction : Action {
        override val name: String = "CLICK_COORDINATES"
        override suspend fun execute(params: Map<String, String>, context: Context): ActionResult {
            val x = params["x"]?.toFloatOrNull() ?: return ActionResult(false, null, "x coordinate is missing or invalid")
            val y = params["y"]?.toFloatOrNull() ?: return ActionResult(false, null, "y coordinate is missing or invalid")
            val success = GenericAppAutomator.clickCoordinates(x, y)
            return ActionResult(success, if (success) "Clicked at coordinates ($x, $y)" else "Failed to click at coordinates", null)
        }
    }
}
