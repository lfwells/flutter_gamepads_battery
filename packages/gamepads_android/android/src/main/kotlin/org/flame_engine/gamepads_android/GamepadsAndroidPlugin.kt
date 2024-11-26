package org.flame_engine.gamepads_android

import androidx.annotation.NonNull
import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import kotlin.concurrent.thread
import android.os.Handler
import android.os.Looper

class GamepadsAndroidPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  companion object {
    private const val TAG = "GamepadsAndroidPlugin"
  }
  private lateinit var channel : MethodChannel
  private lateinit var devices : DeviceListener
  private lateinit var events : EventListener

  private fun listGamepads(): List<Map<String, String>>  {
    return devices.getDevices().map { device ->
      mapOf(
        "id" to device.key.toString(),
        "name" to device.value.name
      )
    }
  }

  // FlutterPlugin
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "xyz.luan/gamepads")
    channel.setMethodCallHandler(this)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    if (call.method == "listGamepads") {
      result.success(listGamepads())
    } else {
      result.notImplemented()
    }
  }

  // Activity Aware
  override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
    onAttachedToActivityShared(activityPluginBinding.activity)
  }

  fun onAttachedToActivityShared(activity: Activity) {
    val compatibleActivity = activity as GamepadsCompatibleActivity
    devices = DeviceListener { compatibleActivity.isGamepadsInputDevice(it) }
    events = EventListener()
    compatibleActivity.registerInputDeviceListener(devices, handler = null)
    compatibleActivity.registerKeyEventHandler { event ->
      if (devices.containsKey(event.deviceId)) {
        events.onKeyEvent(event, channel)
      } else {
        false
      }
     }
    compatibleActivity.registerMotionEventHandler { event ->
      if (devices.containsKey(event.deviceId)) {
        events.onMotionEvent(event, channel)
      } else {
        false
      }
    }
    compatibleActivity.registerBatteryEventHandler { device ->
      if (devices.containsKey(device.id)) {
        events.onBatteryEvent(device, channel)
      } else {
        false
      }
    }

    //create a repeating task to check battery events every 5 seconds
    batteryThread = thread {
      while (true) {
        devices.getDevices().forEach { device ->
          mainHandler.post {
            events.onBatteryEvent(device.value, channel)
          }
        }
        Thread.sleep(5000)
      }
    }
  }

  // Create a handler associated with the main thread
  private val mainHandler = Handler(Looper.getMainLooper())

  //store the thread in a variable so we can stop it when the activity is detached
  private var batteryThread: Thread? = null

  override fun onDetachedFromActivity() {
    // No-op
    batteryThread?.interrupt()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // No-op
    batteryThread?.interrupt()
  }

  override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
    onAttachedToActivityShared(activityPluginBinding.activity)
  }
}
