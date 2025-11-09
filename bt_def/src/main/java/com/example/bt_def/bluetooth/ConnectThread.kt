package com.example.bt_def.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import java.io.IOError
import java.io.IOException
import java.util.UUID

class ConnectThread(device: BluetoothDevice, val listener: BluetoothController.Listener) :
    Thread() {
    private val uuid = "00001101-0000-1000-8000-00805F9B34FB"
    private var mSocket: BluetoothSocket? = null

    init {
        try {
            mSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(uuid))
        } catch (e: IOException) {

        } catch (e: SecurityException) {

        }


    }

    override fun run() {
        try {
            Log.d("connect", "Try to connect with device")
            mSocket?.connect()
            listener.onReceive(BluetoothController.BLUETOOTH_CONNECTED)
            Log.d("connect", "Connect with device")


        } catch (e: IOException) {

            listener.onReceive(BluetoothController.BLUETOOTH_NO_CONNECTED)
            Log.e("BT", "Нет разрешений для Bluetooth", e)


        } catch (e: SecurityException) {

        }
    }

    private fun readMessage() {
        val buffer = ByteArray(256)
        while (true) {
            try {
                val length = mSocket?.inputStream?.read()
                val message = String(buffer, 0, length ?: 0)
                listener.onReceive(message)
            } catch (e: IOException) {
                break

            }

        }

    }


    fun sendMessage(message: String) {
        try {
            mSocket?.outputStream?.write(message.toByteArray())
        } catch (e: IOException) {
            listener.onReceive(BluetoothController.BLUETOOTH_NO_CONNECTED)
        }
    }

    fun closeConnection() {
        try {
            mSocket?.close()
        } catch (e: IOException) {

        }
    }
}