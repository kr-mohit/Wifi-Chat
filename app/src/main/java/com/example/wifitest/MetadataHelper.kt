package com.example.wifitest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object MetadataHelper {

    fun sendProfileInfo(name: String, bitmap: Bitmap) {
        val writer = SocketHolder.writer ?: return
        val out = SocketHolder.socket?.getOutputStream() ?: return

        // Compress bitmap
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        val imgBytes = baos.toByteArray()

        // Send name length + name + image length + image
        val nameBytes = name.toByteArray(Charsets.UTF_8)

        // Write name length
        out.write(nameBytes.size shr 24)
        out.write(nameBytes.size shr 16)
        out.write(nameBytes.size shr 8)
        out.write(nameBytes.size)
        // Write name
        out.write(nameBytes)

        // Write image length
        out.write(imgBytes.size shr 24)
        out.write(imgBytes.size shr 16)
        out.write(imgBytes.size shr 8)
        out.write(imgBytes.size)
        // Write image bytes
        out.write(imgBytes)
        out.flush()
    }

    fun receiveProfileInfo(): Pair<String, Bitmap>? {
        val input = SocketHolder.socket?.getInputStream() ?: return null

        // Read name length
        val nameLen = (input.read() shl 24) or (input.read() shl 16) or
                (input.read() shl 8) or input.read()
        val nameBytes = ByteArray(nameLen)
        input.read(nameBytes)
        val name = String(nameBytes, Charsets.UTF_8)

        // Read image length
        val imgLen = (input.read() shl 24) or (input.read() shl 16) or
                (input.read() shl 8) or input.read()
        val imgBytes = ByteArray(imgLen)
        input.read(imgBytes)
        val bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgLen)

        return name to bitmap
    }

}