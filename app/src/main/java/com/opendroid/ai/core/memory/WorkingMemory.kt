package com.opendroid.ai.core.memory

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.data.models.Plan
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkingMemory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _conversationHistory = mutableListOf<ChatMessage>()
    val conversationHistory: List<ChatMessage> get() = _conversationHistory

    var activePlan: Plan? = null
    var location: String = "Unknown"

    val batteryLevel: Int
        get() {
            return try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            } catch (e: Exception) {
                100
            }
        }

    val wifiState: Boolean
        get() {
            return try {
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = connManager?.activeNetworkInfo
                activeNetwork?.type == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected
            } catch (e: Exception) {
                true
            }
        }

    val connectivity: String
        get() {
            return try {
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = connManager?.activeNetworkInfo
                if (activeNetwork?.isConnected == true) {
                    activeNetwork.typeName ?: "Connected"
                } else {
                    "None"
                }
            } catch (e: Exception) {
                "None"
            }
        }

    fun addMessage(msg: ChatMessage) {
        _conversationHistory.add(msg)
        if (_conversationHistory.size > 20) {
            _conversationHistory.removeAt(0)
        }
    }

    fun clear() {
        _conversationHistory.clear()
        activePlan = null
    }
}
