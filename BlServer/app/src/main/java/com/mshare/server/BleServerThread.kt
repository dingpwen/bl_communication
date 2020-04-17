package com.mshare.server

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Message
import java.io.IOException

class BleServerThread(bleAdapter: BluetoothAdapter, handler: Handler): Thread() {
    private val mBleAdapter = bleAdapter
    private val mHandler = handler
    private var mSocket: BluetoothServerSocket
    private var running: Boolean = true

    init{
        mSocket = mBleAdapter.listenUsingInsecureRfcommWithServiceRecord("GomeServer", Constants.BLE_SERVICE_UUID)
    }

    override fun run() {
        var socket: BluetoothSocket?
        try{
            while(running) {
                socket = mSocket.accept()
                if(socket != null) {
                    val inputStream = socket.inputStream
                    val os = socket.outputStream
                    var read:Int
                    val byteArray = ByteArray(1024) { 0}
                    while(socket.isConnected) {
                        read = inputStream.read(byteArray)
                        if(read == -1) break
                        val byte = ByteArray(read){ byteArray[it] }
                        val message = Message.obtain()
                        message.obj = String(byte)
                        mHandler.sendMessage(message)
                        //Thread.sleep(2000)
                        break
                    }
                    os.close()
                    socket.close()
                }
            }
        } catch(e:IOException) {
            e.printStackTrace()
        }
    }

    fun close() {
        interrupt()
        running = false
        mSocket.close()
    }
}