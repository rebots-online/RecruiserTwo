package com.example.ui.viewer

import android.webkit.JavascriptInterface

class ModelWebInterface(
    private val onCameraUpdate: (Float, Float, Float, Float, Float, Float) -> Unit,
    private val onStatsUpdate: (Int, Int) -> Unit,
    private val onLoadSuccess: (String) -> Unit,
    private val onLoadError: (String) -> Unit
) {
    @JavascriptInterface
    fun onCameraUpdate(posX: Float, posY: Float, posZ: Float, targetX: Float, targetY: Float, targetZ: Float) {
        onCameraUpdate.invoke(posX, posY, posZ, targetX, targetY, targetZ)
    }

    @JavascriptInterface
    fun onStatsUpdate(vertices: Int, polygons: Int) {
        onStatsUpdate.invoke(vertices, polygons)
    }

    @JavascriptInterface
    fun onLoadSuccess(name: String) {
        onLoadSuccess.invoke(name)
    }

    @JavascriptInterface
    fun onLoadError(error: String) {
        onLoadError.invoke(error)
    }
}
