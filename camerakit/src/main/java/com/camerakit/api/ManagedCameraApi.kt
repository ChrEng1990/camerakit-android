package com.camerakit.api

import android.graphics.SurfaceTexture
import com.camerakit.type.CameraFacing
import com.camerakit.type.CameraFlash
import com.camerakit.type.CameraSize

class ManagedCameraApi(private val delegate: CameraApi) : CameraApi by delegate {

    @Synchronized
    override fun open(facing: CameraFacing) {
        cameraHandler.run { delegate.open(facing) }
    }

    @Synchronized
    override fun release() {
        cameraHandler.run { delegate.release() }
    }

    override fun setPreviewSize(size: CameraSize) {
        cameraHandler.run { delegate.setPreviewSize(size) }
    }

    @Synchronized
    override fun setPreviewOrientation(degrees: Int) {
        cameraHandler.run { delegate.setPreviewOrientation(degrees) }
    }

    @Synchronized
    override fun startPreview(surfaceTexture: SurfaceTexture) {
        cameraHandler.run { delegate.startPreview(surfaceTexture) }
    }

    @Synchronized
    override fun stopPreview() {
        cameraHandler.run { delegate.stopPreview() }
    }

    @Synchronized
    override fun setFlash(flash: CameraFlash) {
        cameraHandler.run { delegate.setFlash(flash) }
    }

    @Synchronized
    override fun setPhotoSize(size: CameraSize) {
        cameraHandler.run { delegate.setPhotoSize(size) }
    }

    @Synchronized
    override fun capturePhoto(callback: (jpeg: ByteArray) -> Unit) {
        cameraHandler.run { delegate.capturePhoto(callback) }
    }

    @Synchronized
    override  fun lockfocusClose(): Boolean{
        cameraHandler.run {return delegate.lockfocusClose() }
    }

    @Synchronized
    override  fun setZoom(zoomLevel: Float){
        cameraHandler.run {delegate.setZoom(zoomLevel) }
    }

    @Synchronized
    override fun setFocusArea(x: Float, y: Float) {
        cameraHandler.run {delegate.setFocusArea(x,y)}
    }

    @Synchronized
    override fun releaseFocus() {
        cameraHandler.run{delegate.releaseFocus()}
    }
}