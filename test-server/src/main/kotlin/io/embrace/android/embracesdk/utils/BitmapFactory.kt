package io.embrace.android.embracesdk.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.util.Random

public object BitmapFactory {
    public fun newRandomBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.setColor(Math.abs(Random().nextInt()))
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}

public fun Bitmap.compress(): ByteArray {
    val stream = ByteArrayOutputStream()
    try {
        compress(Bitmap.CompressFormat.JPEG, 70, stream)
    } catch (e: OutOfMemoryError) {
        return ByteArray(0)
    }
    return stream.toByteArray()
}
