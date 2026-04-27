package com.example.kamaynikasyon.core.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.material.snackbar.Snackbar
import android.view.View
import com.example.kamaynikasyon.R

/**
 * Network connectivity monitor that tracks network state and can show offline/online banners.
 * 
 * Usage:
 * ```
 * val networkMonitor = NetworkMonitor(activity)
 * networkMonitor.observe(lifecycleOwner) { isOnline ->
 *     // Handle network state changes
 * }
 * ```
 */
class NetworkMonitor(private val context: Context) {
    
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkReceiver: BroadcastReceiver? = null
    private var currentState: Boolean = true
    private var listeners = mutableListOf<(Boolean) -> Unit>()
    private var offlineSnackbar: Snackbar? = null
    private var anchorView: View? = null
    
    init {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        currentState = ErrorHandler.isOnline(context)
    }
    
    /**
     * Observe network state changes with a lifecycle-aware observer.
     */
    fun observe(lifecycleOwner: LifecycleOwner, listener: (Boolean) -> Unit) {
        listeners.add(listener)
        
        // Immediately notify current state
        listener(currentState)
        
        lifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_START)
            fun onStart() {
                startMonitoring()
            }
            
            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            fun onStop() {
                stopMonitoring()
            }
            
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                listeners.remove(listener)
                if (listeners.isEmpty()) {
                    cleanup()
                }
            }
        })
    }
    
    /**
     * Show an offline banner that automatically appears/disappears based on network state.
     * The banner will be anchored to the provided view.
     */
    fun showOfflineBanner(anchorView: View) {
        this.anchorView = anchorView
        updateOfflineBanner()
    }
    
    /**
     * Hide the offline banner.
     */
    fun hideOfflineBanner() {
        offlineSnackbar?.dismiss()
        offlineSnackbar = null
    }
    
    private fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Use NetworkCallback for API 24+
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkState(true)
                }
                
                override fun onLost(network: Network) {
                    updateNetworkState(false)
                }
                
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                     networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    updateNetworkState(hasInternet)
                }
            }
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        } else {
            // Use BroadcastReceiver for older APIs
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            networkReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val isOnline = ErrorHandler.isOnline(context ?: this@NetworkMonitor.context)
                    updateNetworkState(isOnline)
                }
            }
            context.registerReceiver(networkReceiver, filter)
        }
    }
    
    private fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
            networkCallback = null
        }
        
        networkReceiver?.let {
            context.unregisterReceiver(it)
            networkReceiver = null
        }
    }
    
    private fun updateNetworkState(isOnline: Boolean) {
        if (currentState != isOnline) {
            currentState = isOnline
            listeners.forEach { it(isOnline) }
            updateOfflineBanner()
        }
    }
    
    private fun updateOfflineBanner() {
        anchorView?.let { view ->
            if (!currentState) {
                // Show offline banner
                if (offlineSnackbar == null || !offlineSnackbar!!.isShown) {
                    offlineSnackbar = Snackbar.make(
                        view,
                        context.getString(R.string.error_offline),
                        Snackbar.LENGTH_INDEFINITE
                    )
                    offlineSnackbar!!.show()
                }
            } else {
                // Hide offline banner, show online message briefly
                offlineSnackbar?.dismiss()
                offlineSnackbar = null
                
                // Optionally show "Back online" message
                Snackbar.make(
                    view,
                    context.getString(R.string.network_online),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun cleanup() {
        stopMonitoring()
        hideOfflineBanner()
        listeners.clear()
    }
    
    /**
     * Get current network state.
     */
    fun isOnline(): Boolean = currentState
}

/**
 * Extension function to easily create and observe network state in Activities.
 */
fun android.app.Activity.observeNetworkState(listener: (Boolean) -> Unit) {
    val networkMonitor = NetworkMonitor(this)
    // Activity implements LifecycleOwner in AndroidX
    networkMonitor.observe(this as LifecycleOwner, listener)
}

/**
 * Extension function to easily create and observe network state in Fragments.
 */
fun androidx.fragment.app.Fragment.observeNetworkState(listener: (Boolean) -> Unit) {
    val networkMonitor = NetworkMonitor(requireContext())
    viewLifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.LifecycleObserver {
        @androidx.lifecycle.OnLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        fun onStart() {
            networkMonitor.observe(viewLifecycleOwner, listener)
        }
    })
}

