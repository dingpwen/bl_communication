package com.mshare.server

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {
    private lateinit var qrImg:ImageView
    private lateinit var mBleThread:BleServerThread
    private lateinit var wifiThread:WlanClientThread
    private var mBleManager: BluetoothManager? = null
    private var mWifiManager:WifiManager? = null
    private var connectivityManager:ConnectivityManager? = null
    private var mBleAdapter: BluetoothAdapter? = null
    private lateinit var mBluetoothLeAdvertiser:BluetoothLeAdvertiser
    private lateinit var mAdvertiseCallback: AdvertiseCallback
    private val mDevices = ArrayList<BluetoothDevice>()

    private val mDataHandler = Handler {
        val data:String = it.obj as String
        wifiThread = WlanClientThread(data,mWifiManager as WifiManager, connectivityManager as ConnectivityManager)
        wifiThread.start()
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        qrImg = findViewById(R.id.qrcode)
        //runOnUiThread(Runnable {
            createQrImg()
        //})
        //MyUtils.setImage(qrImg, Constants.BASE_MAC)
        initBleManager()
        mBleThread = BleServerThread(mBleAdapter as BluetoothAdapter, mDataHandler)
        if(Constants.BLE_CONNECT_MODE == 2) {
            mBleThread.start()
        }
        if(mWifiManager == null) {
            mWifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        //wifiThread = WlanClientThread("",mWifiManager as WifiManager)
        //wifiThread.start()
    }
    private fun initBleManager() {
        if (mBleManager == null) {
            mBleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBleAdapter == null && mBleManager != null) {
                mBleAdapter = (mBleManager as BluetoothManager).adapter
                Log.d("client", "mBleAdapter:$mBleAdapter")
            }
        }
    }

    private fun createQrImg() {
        try {
            var mac = getBluetoothMacAddress()
            Log.d("wenpd", "mac:$mac")
            if(mac.isNullOrEmpty() || Constants.FOR_BORAD_TEST) {
                mac = Constants.BASE_BORAD_MAC
            }
            val hints = mutableMapOf<EncodeHintType, String>()
            hints[EncodeHintType.CHARACTER_SET] =  "UTF-8"
            val result = MultiFormatWriter().encode(mac, BarcodeFormat.QR_CODE,350,350,hints)//通过字符串创建二维矩阵
            val width = result.width
            val height = result.height
            Log.d("client", "width:$width height:$height")
            val pixels = Array(width * height){0}

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if(result.get(x, y)) MyUtils.BLACK else MyUtils.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)//创建位图
            bitmap.setPixels(pixels.toIntArray(), 0, width, 0, 0, width, height)//设置位图像素集为数组
            qrImg.setImageBitmap(bitmap)//显示二维码
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("HardwareIds")
    private fun getBluetoothMacAddress(): String? {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        var bluetoothMacAddress: String? = ""
        if(bluetoothAdapter == null) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val mServiceField: Field = bluetoothAdapter.javaClass.getDeclaredField("mService")
                mServiceField.isAccessible = true
                val btManagerService: Any = mServiceField.get(bluetoothAdapter)?:return null
                bluetoothMacAddress =
                        btManagerService.javaClass.getMethod("getAddress").invoke(btManagerService) as String
            } catch (e: NoSuchFieldException) {
            } catch (e: NoSuchMethodException) {
            } catch (e: IllegalAccessException) {
            } catch (e: InvocationTargetException) {
            }
        } else {
            bluetoothMacAddress = bluetoothAdapter.address
        }
        return bluetoothMacAddress
    }

    override fun onResume() {
        super.onResume()
        if (mBleAdapter == null || !(mBleAdapter as BluetoothAdapter).isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
            finish()
            return
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish()
            return
        }
        if(Constants.BLE_CONNECT_MODE == 1) {
            if (!(mBleAdapter as BluetoothAdapter).isMultipleAdvertisementSupported) {
                finish()
                return
            }
            mBluetoothLeAdvertiser = (mBleAdapter as BluetoothAdapter).bluetoothLeAdvertiser
            mGattServer = mBleManager?.openGattServer(this, gattServerCallback) as BluetoothGattServer
            setupServer()
            startAdvertising()
        }
    }

    override fun onPause() {
        super.onPause()
        if(Constants.BLE_CONNECT_MODE == 1) {
            stopAdvertising()
            stopServer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBleThread.close()
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTimeout(0)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(Constants.BLE_SERVICE_UUID))
            .build()
        /*val scanResponseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(Constants.BLE_SERVICE_UUID))
            .setIncludeTxPowerLevel(true)
            .build()*/
        mAdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d("wenpd", "BLE advertisement added successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e("wenpd", "Failed to add BLE advertisement, reason: $errorCode")
            }
        }
        mBluetoothLeAdvertiser.startAdvertising(settings, advertiseData, mAdvertiseCallback)
    }

    private lateinit var mGattServer:BluetoothGattServer
    private fun setupServer() {
        val gattService = BluetoothGattService(Constants.BLE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristicRead = BluetoothGattCharacteristic(Constants.BLE_READ_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ)
        val descriptor = BluetoothGattDescriptor(Constants.BLE_DESC_UUID, BluetoothGattCharacteristic.PERMISSION_WRITE)
        characteristicRead.addDescriptor(descriptor)
        gattService.addCharacteristic(characteristicRead)
        val characteristicWrite = BluetoothGattCharacteristic(Constants.BLE_WRITE_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE)
        gattService.addCharacteristic(characteristicWrite)
        Log.d("wenpd", "startGattServer:stagattServicetus=$gattService")
        mGattServer.addService(gattService)
    }

    private val gattServerCallback = object:BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            Log.d("wenpd", "onConnection:status=$status,newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mDevices.add(device!!)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mDevices.remove(device)
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            Log.d("wenpd", "onServiceAdded:status=$status service=$service")
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d("wenpd", "onCharacteristicRead:characteristic=${characteristic?.uuid}")
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            Log.d("wenpd", "onCharacteristicWrite:characteristic=${characteristic?.uuid},value=$value")
            if (characteristic!!.uuid == Constants.BLE_WRITE_UUID) {
                setReceivedData(String(value!!, StandardCharsets.UTF_8));
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                val length = value!!.size
                val reversed = ByteArray(length)
                for (i in 0 until length) {
                    reversed[i] = value[length - (i + 1)]
                }
                characteristic.value = reversed
                for (dev in mDevices) {
                    mGattServer.notifyCharacteristicChanged(dev, characteristic, false)
                }
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            Log.d("wenpd", "onDescriptorWrite:value=$value")
        }
    }

    private fun stopServer() {
        mGattServer.close()
    }

    private fun stopAdvertising() {
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback)
    }

    private var mReceiveData: String? = null
    private fun setReceivedData(data: String) {
        val time = SimpleDateFormat.getDateTimeInstance()
            .format(Date(System.currentTimeMillis()))
        mReceiveData = if (mReceiveData == null) {
            "$time $data"
        } else {
            "$time $data\n$mReceiveData"
        }
        val receivedTxt = findViewById<TextView>(R.id.received)
        runOnUiThread { receivedTxt.text = "Received data:\n$mReceiveData" }
    }
}
