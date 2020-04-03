package com.camerakit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import com.camerakit.preview.CameraSurfaceTexture
import com.camerakit.preview.CameraSurfaceTextureListener
import com.camerakit.preview.CameraSurfaceView
import com.camerakit.util.CameraSizeCalculator
import com.camerakit.api.CameraApi
import com.camerakit.api.CameraAttributes
import com.camerakit.api.CameraEvents
import com.camerakit.api.ManagedCameraApi
import com.camerakit.api.camera1.Camera1
import com.camerakit.api.camera2.Camera2
import com.camerakit.type.CameraFacing
import com.camerakit.type.CameraFlash
import com.camerakit.type.CameraSize
import com.jpegkit.Jpeg
import kotlinx.coroutines.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraPreview : FrameLayout, CameraEvents {

    companion object {
        private const val FORCE_DEPRECATED_API = false
    }

    var lifecycleState: LifecycleState = LifecycleState.STOPPED
    var surfaceState: SurfaceState = SurfaceState.SURFACE_WAITING
    var cameraState: CameraState = CameraState.CAMERA_CLOSED
        set(state) {
            field = state
            when (state) {
                CameraState.CAMERA_OPENED -> {
                    listener?.onCameraOpened()
                }
                CameraState.PREVIEW_STARTED -> {
                    listener?.onPreviewStarted()
                }
                CameraState.PREVIEW_STOPPED -> {
                    listener?.onPreviewStopped()
                }
                CameraState.CAMERA_CLOSING -> {
                    listener?.onCameraClosed()
                }
                else -> {
                    // ignore
                }
            }
        }

    var listener: Listener? = null

    var displayOrientation: Int = 0
    var previewOrientation: Int = 0
    var captureOrientation: Int = 0
    var previewSize: CameraSize = CameraSize(0, 0)
    var surfaceSize: CameraSize = CameraSize(0, 0)
        get() {
            return surfaceTexture?.size ?: field
        }

    var photoSize: CameraSize = CameraSize(0, 0)
    var flash: CameraFlash = CameraFlash.OFF
    var imageMegaPixels: Float = 2f

    private var cameraFacing: CameraFacing = CameraFacing.BACK
    private var surfaceTexture: CameraSurfaceTexture? = null
    private var attributes: CameraAttributes? = null

    private val cameraSurfaceView: CameraSurfaceView = CameraSurfaceView(context)

    private val cameraDispatcher: CoroutineDispatcher = newSingleThreadContext("CAMERA")
    private val cameraDispatcherMain: CoroutineDispatcher = Dispatchers.Main
    private var cameraOpenContinuation: Continuation<Unit>? = null
    private var previewStartContinuation: Continuation<Unit>? = null

    @SuppressWarnings("NewApi")
    private val cameraApi: CameraApi = ManagedCameraApi(
            when (Build.VERSION.SDK_INT < 21 || FORCE_DEPRECATED_API) {
                true -> Camera1(this)
                false -> Camera2(this, context)
            })

    constructor(context: Context) :
            super(context)

    constructor(context: Context, attributeSet: AttributeSet) :
            super(context, attributeSet)

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayOrientation = windowManager.defaultDisplay.rotation * 90

        cameraSurfaceView.cameraSurfaceTextureListener = object : CameraSurfaceTextureListener {
            override fun onSurfaceReady(cameraSurfaceTexture: CameraSurfaceTexture) {
                Log.e("Flora","Surface ready: " + lifecycleState.toString())
                surfaceState = SurfaceState.SURFACE_AVAILABLE
                if (lifecycleState == LifecycleState.STARTED || lifecycleState == LifecycleState.RESUMED) {
                    Log.e("Flora","surface ready call")
                    surfaceTexture = cameraSurfaceTexture
                    resume()
                }
            }

        }

        addView(cameraSurfaceView)
    }

    override fun onDraw(canvas: Canvas?) {
        var can = canvas
        if(can == null){
            Log.e("Flora","No Canvas")
           return
        }
        Log.e("Flora", "on Draw Preview")

        super.onDraw(canvas)
        val rectShape = ShapeDrawable(RectShape())
        rectShape.setBounds(100,100,200,200)
        rectShape.paint.color = Color.parseColor("#000000")
        rectShape.draw(can)
    }
    fun start(facing: CameraFacing) {
        GlobalScope.launch(cameraDispatcher) {

                lifecycleState = LifecycleState.STARTED
                cameraFacing = facing
                openCamera()

        }
    }

    fun resume() {
        GlobalScope.launch(cameraDispatcher) {

                lifecycleState = LifecycleState.RESUMED
                try {
                    startPreview()
                } catch (e: Exception) {
                    // camera or surface not ready, wait.
                }

        }
    }

    fun pause() {
        GlobalScope.launch(cameraDispatcher) {

                lifecycleState = LifecycleState.PAUSED
                stopPreview()

        }
    }

    fun stop() {

        GlobalScope.launch(cameraDispatcher) {


                lifecycleState = LifecycleState.STOPPED
                closeCamera()

        }
       // Log.e("Flora Preview", lifecycleState.toString())
        //cameraApi.release()

    }


    fun getmaxZoom():Float{
        return cameraApi.getmaxZoom()
    }
    fun taptofocus(x: Float, y: Float, width: Int, height: Int){
        cameraApi.setFocusArea(x ,y )

    }

    fun releaseFocus(){
        cameraApi.releaseFocus()
    }

    fun capturePhoto(callback: PhotoCallback) {
        Log.e("Flora","posting take Picture")
        //this.addView(Canvass)

        GlobalScope.launch(cameraDispatcher) {
            runBlocking {
                cameraApi.setFlash(flash)
                cameraApi.capturePhoto {
                    cameraApi.cameraHandler.post {
                        val jpeg = Jpeg(it)
                        jpeg.rotate(captureOrientation)
                        val transformedBytes = jpeg.jpegBytes
                        jpeg.release()
                        callback.onCapture(transformedBytes)
                        Log.e("Flora","Pic posted")
                    }
                }
            }
        }
    }

    fun hasFlash(): Boolean {
        if (attributes?.flashes != null) {
            return true
        }
        return false
    }

    fun getSupportedFlashTypes(): Array<CameraFlash>? {
        return attributes?.flashes
    }

    interface PhotoCallback {
        fun onCapture(jpeg: ByteArray)
    }

    // CameraEvents:
    fun lockFocus(): Boolean{
       return cameraApi.lockfocusClose()
    }

    fun setZoom(zoomLevel: Float){
        cameraApi.setZoom(zoomLevel)
    }

    override fun onCameraOpened(cameraAttributes: CameraAttributes) {
        Log.e("Flora","camera opened")
        cameraState = CameraState.CAMERA_OPENED
        attributes = cameraAttributes
        cameraOpenContinuation?.resume(Unit)
        cameraOpenContinuation = null
        if(surfaceState == SurfaceState.SURFACE_AVAILABLE){
            resume()
        }else{
            Log.e("Flora", "Surface not available.")
        }
    }

    override fun onCameraClosed() {
        cameraState = CameraState.CAMERA_CLOSED
    }

    override fun onCameraError() {
    }

    override fun onPreviewStarted() {
        cameraState = CameraState.PREVIEW_STARTED
        previewStartContinuation?.resume(Unit)
        previewStartContinuation = null
    }

    override fun onPreviewStopped() {
        cameraState = CameraState.PREVIEW_STOPPED
    }

    override fun onPreviewError() {
    }

    // State enums:

    enum class LifecycleState {
        STARTED,
        RESUMED,
        PAUSED,
        STOPPED;
    }

    enum class SurfaceState {
        SURFACE_AVAILABLE,
        SURFACE_WAITING;
    }

    enum class CameraState {
        CAMERA_OPENING,
        CAMERA_OPENED,
        PREVIEW_STARTING,
        PREVIEW_STARTED,
        PREVIEW_STOPPING,
        PREVIEW_STOPPED,
        CAMERA_CLOSING,
        CAMERA_CLOSED;
    }

    // Camera control:

    private suspend fun openCamera(): Unit = suspendCoroutine {
        cameraOpenContinuation = it
        cameraState = CameraState.CAMERA_OPENING
        cameraApi.open(cameraFacing)
        Log.e("Flora", "Open Camera intern")
    }

    private suspend fun startPreview(): Unit = suspendCoroutine {
        previewStartContinuation = it
        val surfaceTexture = surfaceTexture
        Log.e("Flora","starting Preview")
        val attributes = attributes
        if (surfaceTexture != null && attributes != null) {
            cameraState = CameraState.PREVIEW_STARTING

            previewOrientation = when (cameraFacing) {
                CameraFacing.BACK -> (attributes.sensorOrientation - displayOrientation + 360) % 360
                CameraFacing.FRONT -> {
                    val result = (attributes.sensorOrientation + displayOrientation) % 360
                    (360 - result) % 360
                }
            }

            captureOrientation = when (cameraFacing) {
                CameraFacing.BACK -> (attributes.sensorOrientation - displayOrientation + 360) % 360
                CameraFacing.FRONT -> (attributes.sensorOrientation + displayOrientation + 360) % 360
            }

            if (Build.VERSION.SDK_INT >= 21 && !FORCE_DEPRECATED_API) {
                surfaceTexture.setRotation(displayOrientation)
            }

            previewSize = CameraSizeCalculator(attributes.previewSizes)
                    .findClosestSizeContainingTarget(when (previewOrientation % 180 == 0) {
                        true -> CameraSize(width, height)
                        false -> CameraSize(height, width)
                    })

            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            surfaceTexture.size = when (previewOrientation % 180) {
                0 -> previewSize
                else -> CameraSize(previewSize.height, previewSize.width)
            }

            photoSize = CameraSizeCalculator(attributes.photoSizes)
                    .findClosestSizeMatchingArea((imageMegaPixels * 1000000).toInt())

            cameraApi.setPreviewOrientation(previewOrientation)
            cameraApi.setPreviewSize(previewSize)
            cameraApi.setPhotoSize(photoSize)
            cameraApi.startPreview(surfaceTexture)
        } else {
            if(surfaceTexture == null) {
                Log.e("Flora", "Surface Error")
            }else{
                Log.e("Flora","attribute error")
            }
            it.resumeWithException(IllegalStateException())
            previewStartContinuation = null

        }
    }

    private suspend fun stopPreview(): Unit = suspendCoroutine {
        cameraState = CameraState.PREVIEW_STOPPING
        cameraApi.stopPreview()
        it.resume(Unit)
    }

    private suspend fun closeCamera(): Unit = suspendCoroutine {
        cameraState = CameraState.CAMERA_CLOSING
        cameraApi.release()
        Log.e("Flora susp", "Closing")
        Log.e("Flora susp", lifecycleState.toString())
        it.resume(Unit)
    }

    // Listener:

    interface Listener {
        fun onCameraOpened()
        fun onCameraClosed()
        fun onPreviewStarted()
        fun onPreviewStopped()
    }

}