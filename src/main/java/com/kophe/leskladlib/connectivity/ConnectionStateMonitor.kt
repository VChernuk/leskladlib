package com.kophe.leskladlib.connectivity

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.loggingTag

interface ConnectionStateMonitor {

    fun available(): Boolean?

}

class DefaultConnectionStateMonitor(context: Context, val loggingUtil: LoggingUtil) :
    NetworkCallback(), ConnectionStateMonitor {

    var connectionAvailable = false

    private val connectivityManager =
        context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkRequest by lazy {
        NetworkRequest.Builder().addTransportType(TRANSPORT_CELLULAR)
            .addTransportType(TRANSPORT_WIFI).build()
    }

    init {
        enable()
    }

    override fun available() = connectionAvailable

    private fun enable() {
        loggingUtil.log("${loggingTag()} enable")
        connectivityManager.registerNetworkCallback(networkRequest, this)
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        loggingUtil.log("${loggingTag()} onAvailable")
        connectionAvailable = true
    }

    override fun onUnavailable() {
        super.onUnavailable()
        loggingUtil.log("${loggingTag()} onUnavailable")
        connectionAvailable = false
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        loggingUtil.log("${loggingTag()} onLost")
        connectionAvailable = false
    }
}
