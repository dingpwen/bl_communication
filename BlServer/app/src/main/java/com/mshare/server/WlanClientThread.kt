package com.mshare.server

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import org.json.JSONObject
import android.content.Context.WIFI_SERVICE
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import android.os.PatternMatcher
import android.util.Log
import android.text.format.Formatter
import androidx.core.content.ContextCompat.getSystemService
import java.net.Socket
import java.net.SocketException
import java.util.*


class WlanClientThread(val data:String, val manager: WifiManager, connectivity: ConnectivityManager):Thread() {
    lateinit var ssid:String;
    lateinit var psw:String
    var port = 50000
    lateinit var socket:Socket
    private val wifiManager = manager
    private val connectivityManager = connectivity
    var isRunning = true

    init{
        if(data.isEmpty()) {
            ssid = "test"
            psw = "123"
        } else {
            var jObj = JSONObject(data)
            ssid = jObj.getString("SSID")
            psw = jObj.getString("KEY")
            port = jObj.getInt("PORT")
        }
    }

    override fun run() {
        //wifiManager.updateNetwork(createConfig(ssid,psw))
        if (!ssid.equals("test")) {
            wifiManager.addNetwork(createConfig(ssid, psw))
            while (true) {//检测是否连接上热点
                if (wifiManager.connectionInfo != null) {
                    var c_ssid = getSSID(wifiManager)
                    Log.i("wenpd", "connectionInfo：$c_ssid")
                    if (ssid.equals(c_ssid)) break;
                }
                Thread.sleep(500)
            }
        }
        while (true) {//检测是否已获得IP地址
            var ip = manager.connectionInfo.ipAddress
            Log.d("wenpd", "ip is $ip")
            if (ip != 0) {
                var routeip = manager.dhcpInfo.gateway
                Log.d("wenpd", "routeip is $routeip")
                break
            }
            Thread.sleep(400)
        }
        val ipRoute = getWifiRouteIPAddress(wifiManager);
        Log.d("wenpd", "connect:$ipRoute")
        while (true){//检测网络是否已经通了
            try {
                socket = Socket(ipRoute, 50000)
                break
            } catch (se: SocketException) {
                Log.d("wenpd", "connect:" + se.toString())
            }
            Thread.sleep(500)
        }
        var outputStream = socket.getOutputStream();
        var i = 0;
        val len = Constants.CONTENTS.size
        while(isRunning) {
            val content = Constants.CONTENTS.get(i);
            Log.i("wenpd", "writing：$i content$content")
            outputStream.write(content.toByteArray())
            Thread.sleep(40)
            if(++i == len) {
                i = 0;
            }
        }

    }

    public fun close() {
        isRunning = false
        socket.close()
    }

    companion object{
        fun createConfig(ssid:String, password:String): WifiConfiguration {
            val config = WifiConfiguration()
            config.allowedAuthAlgorithms.clear()
            config.allowedGroupCiphers.clear()
            config.allowedKeyManagement.clear()
            config.allowedPairwiseCiphers.clear()
            config.allowedProtocols.clear()
            config.SSID = "\"" + ssid + "\""
            config.preSharedKey = "\""+password+"\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;

            return config;
        }
       fun getWifiRouteIPAddress(manager: WifiManager): String {
           val dhcpInfo = manager.dhcpInfo
           val routeIp = Formatter.formatIpAddress(dhcpInfo.gateway)
            Log.i("wenpd", "wifi route ip：$routeIp")

            return routeIp
        }

        fun getSSID(manager: WifiManager):String {
            var ssid = manager.connectionInfo.ssid
            if (ssid != null) {
                if (ssid.length > 2 && ssid[0] == '"' && ssid[ssid.length - 1] == '"') {
                    return ssid.substring(1, ssid.length - 1);
                }
            }
            return "";
        }
    }

    private fun connectHotspot(name:String, password: String) {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            wifiManager.addNetwork(createConfig(name, password))
        } else {
            val specifier = WifiNetworkSpecifier.Builder()
                    .setSsidPattern(PatternMatcher(name, PatternMatcher.PATTERN_PREFIX))
                    .setWpa2Passphrase(password)
                    .build()
            val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network?) {
                    // do success processing here..
                }

                override fun onUnavailable() {
                    // do failure processing here..
                }
            }

            connectivityManager.requestNetwork(request, networkCallback)
            //connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
}