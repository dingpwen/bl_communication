package com.mshare.server

import java.util.*

object Constants {
    const val FOR_BORAD_TEST = false
    const val BASE_BORAD_MAC = "84:0D:8E:C1:82:26"
    var BLE_CONNECT_MODE = 1  //1: with gatt 0:with socket
    val BLE_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
    val BLE_WRITE_UUID = UUID.fromString("00002a02-0000-1000-8000-00805f9b34fb")
    val BLE_READ_UUID  = UUID.fromString("00002a03-0000-1000-8000-00805f9b34fb")
    val BLE_DESC_UUID  = UUID.fromString("00002a04-0000-1000-8000-00805f9b34fb")
    const val WHITE = 0xFFFFFF00
    const val BLACK = 0xFF000000
    val CONTENTS = listOf<String>("你好吗。加油啊", "快点走啊要迟到了","利用Android Camera2 的照相机api实现", "实时的图像采集与预览", "实时的图像采集与预览")
}
