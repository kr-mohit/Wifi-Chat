package com.example.wifitest

import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket

object SocketHolder {
    var socket: Socket? = null
    var serverSocket: ServerSocket? = null
    var reader: BufferedReader? = null
    var writer: BufferedWriter? = null
}