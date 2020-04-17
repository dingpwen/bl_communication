package com.mshare.blclient;

import java.util.UUID;

public class Constants {
    public static final UUID SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a02-0000-1000-8000-00805f9b34fb");
    public static final int BLE_CONNECT_MODE = 1;//1: with gatt 0:with socket
    public static final UUID MY_SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
}
