package ro.masipos.fiscal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
// Datecs SDK imports (required by device operations)
import com.datecs.fiscalprinter.SDK.BuildInfo
import com.datecs.fiscalprinter.SDK.FiscalException
import com.datecs.fiscalprinter.SDK.model.DatecsFiscalDevice
import com.datecs.fiscalprinter.SDK.model.UserLayer.cmdConfig
import com.datecs.fiscalprinter.SDK.model.UserLayer.cmdReceipt
import com.datecs.fiscalprinter.SDK.model.UserLayer.cmdReport
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class FiscalBridgePlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context

    private val actionUsbPermission = "ro.masipos.fiscal.USB_PERMISSION"
    private var pendingUsbPermissionResult: MethodChannel.Result? = null
    private var receiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pluginJob = SupervisorJob()
    private val pluginScope = CoroutineScope(pluginJob + Dispatchers.IO)

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != actionUsbPermission) {
                return
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val callback = pendingUsbPermissionResult
            pendingUsbPermissionResult = null

            val usbManager = (context ?: appContext).getSystemService(Context.USB_SERVICE) as UsbManager
            val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
            val effectiveGrant = granted || (device != null && usbManager.hasPermission(device))
            callback?.success(effectiveGrant)
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        appContext = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "ro.masipos.fiscal/bridge")
        channel.setMethodCallHandler(this)
        registerUsbReceiver()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        unregisterUsbReceiver()
        channel.setMethodCallHandler(null)
        pluginJob.cancel()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "usbListDevices" -> handleUsbListDevices(result)
                "usbRequestPermission" -> handleUsbRequestPermission(call, result)
                "usbConnect" -> handleUsbConnect(call, result)
                "usbDisconnect" -> handleUsbDisconnect(result)
                "setDateTime" -> handleSetDateTime(call, result)
                "nonFiscalOpen" -> handleNonFiscalOpen(result)
                "printNonFiscalText" -> handlePrintNonFiscalText(call, result)
                "drawerKickOut" -> handleDrawerKickOut(result)
                "nonFiscalClose" -> handleNonFiscalClose(call, result)
                "openReceipt" -> handleOpenReceipt(call, result)
                "sellItem" -> handleSellItem(call, result)
                "fiscalSubtotal" -> handleFiscalSubtotal(call, result)
                "cashInCashOut" -> handleCashInCashOut(call, result)
                "payment" -> handlePayment(call, result)

                "closeReceipt" -> handleCloseReceipt(result)
                "cancelReceipt" -> handleCancelReceipt(result)
                "reportX" -> handleReport('X', result)
                "reportZ" -> handleReport('Z', result)
                "getStatus" -> handleGetStatus(result)
                else -> result.notImplemented()
            }
        } catch (e: FiscalException) {
            result.error("FISCAL", e.message, null)
        } catch (e: IOException) {
            result.error("IO", e.message, null)
        } catch (e: Exception) {
            result.error("ERR", e.message, null)
        }
    }

    private fun handleUsbListDevices(result: MethodChannel.Result) {
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values.map {
            mapOf(
                "vid" to it.vendorId,
                "pid" to it.productId,
                "productName" to (it.productName ?: ""),
                "deviceName" to it.deviceName
            )
        }
        result.success(devices)
    }

    private fun handleUsbRequestPermission(call: MethodCall, result: MethodChannel.Result) {
        val vid = call.argument<Int>("vid") ?: run {
            result.error("ARG", "vid required", null)
            return
        }
        val pid = call.argument<Int>("pid") ?: run {
            result.error("ARG", "pid required", null)
            return
        }

        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { it.vendorId == vid && it.productId == pid }
            ?: run {
                result.error("USB", "Device not found", null)
                return
            }

        if (usbManager.hasPermission(device)) {
            result.success(true)
            return
        }

        if (pendingUsbPermissionResult != null) {
            result.error("USB", "Another permission request is in progress", null)
            return
        }

        val permissionIntent = Intent(actionUsbPermission).apply {
            setPackage(appContext.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            permissionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        pendingUsbPermissionResult = result
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun handleUsbConnect(call: MethodCall, result: MethodChannel.Result) {
        val vid = call.argument<Int>("vid") ?: run {
            result.error("ARG", "vid required", null)
            return
        }
        val pid = call.argument<Int>("pid") ?: run {
            result.error("ARG", "pid required", null)
            return
        }

        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { it.vendorId == vid && it.productId == pid }
            ?: run {
                result.error("USB", "Device not found", null)
                return
            }

        if (!usbManager.hasPermission(device)) {
            result.error("USB", "Permission denied", null)
            return
        }

        val completion = AtomicBoolean(false)
        fun sendSuccess(data: Map<String, Any?>) {
            if (completion.compareAndSet(false, true)) {
                mainHandler.post { result.success(data) }
            }
        }
        fun sendError(code: String, message: String?) {
            if (completion.compareAndSet(false, true)) {
                mainHandler.post { result.error(code, message, null) }
            }
        }

        pluginScope.launch {
            try {
                val info = withTimeout(CONNECT_TIMEOUT_MS) {
                    connectToUsbDevice(usbManager, device)
                }
                sendSuccess(info)
            } catch (e: TimeoutCancellationException) {
                sendError(ERROR_USB_TIMEOUT, MESSAGE_USB_TIMEOUT)
            } catch (e: FiscalException) {
                sendError("FISCAL", e.message)
            } catch (e: IOException) {
                if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    sendError(ERROR_USB_TIMEOUT, MESSAGE_USB_TIMEOUT)
                } else {
                    sendError("IO", e.message)
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Exception) {
                sendError("ERR", e.message)
            }
        }
    }

    private fun connectToUsbDevice(usbManager: UsbManager, device: UsbDevice): Map<String, Any?> {
        val connector = UsbDeviceConnector(appContext, usbManager, device)
        return try {
            connector.connect()
            PrinterManager.instance.init(connector)
            mapOf(
                "model" to PrinterManager.instance.modelVendorName,
                "connector" to PrinterManager.getsConnectorType(),
                "libVersion" to BuildInfo.VERSION
            )
        } catch (e: Exception) {
            try {
                connector.close()
            } catch (_: Exception) {
            }
            PrinterManager.instance.close()
            throw e
        }
    }
    private fun handleUsbDisconnect(result: MethodChannel.Result) {
        PrinterManager.instance.close()
        result.success(true)
    }

    private fun handleSetDateTime(call: MethodCall, result: MethodChannel.Result) {
        val timestamp = call.argument<String>("formatted") ?: run {
            result.error("ARG", "formatted required", null)
            return
        }
        val config = requireConfigCommands()
        config.SetDateTime(timestamp)
        result.success(true)
    }

    private fun handleNonFiscalOpen(result: MethodChannel.Result) {
        requireReceiptCommands().NonFiscalOpen()
        result.success(true)
    }


    private fun handlePrintNonFiscalText(call: MethodCall, result: MethodChannel.Result) {
        val receipt = requireReceiptCommands()
        val text = call.argument<String>("text") ?: ""
        val bold = if (call.argument<Boolean>("bold") == true) '1' else '0'
        val italic = if (call.argument<Boolean>("italic") == true) '1' else '0'
        val doubleHeight = if (call.argument<Boolean>("doubleH") == true) '1' else '0'
        val underline = if (call.argument<Boolean>("underline") == true) '1' else '0'
        val align = when ((call.argument<String>("align") ?: "L").uppercase()) {
            "C" -> '1'
            "R" -> '2'
            "J" -> '3'
            else -> '0'
        }
        val condensed = if (call.argument<Boolean>("condensed") == true || call.argument<Boolean>("doubleW") == true) '1' else '0'
        receipt.PrintNonFiscalText(text, bold, italic, doubleHeight, underline, align, condensed)
        result.success(true)
    }

    private fun handleDrawerKickOut(result: MethodChannel.Result) {
        requireReceiptCommands().DrawerKickOut()
        result.success(true)
    }

    private fun handleNonFiscalClose(call: MethodCall, result: MethodChannel.Result) {
        val cut = call.argument<Boolean>("cut") == true
        val closed = requireReceiptCommands().NonFiscalClose(cut)
        result.success(closed)
    }

    private fun handleOpenReceipt(call: MethodCall, result: MethodChannel.Result) {
        val receipt = requireReceiptCommands()
        val operatorId = call.argument<String>("operatorId") ?: "1"
        val operatorPassword = call.argument<String>("operatorPassword") ?: "0000"
        val till = call.argument<String>("tillNo") ?: "1"
        val invoiceNumber = call.argument<String>("invoiceNumber") ?: ""
        val customerTaxId = call.argument<String>("customerTaxId") ?: ""
        val opened = receipt.FiscalOpen(operatorId, operatorPassword, till, invoiceNumber, customerTaxId)
        result.success(opened)
    }

    private fun handleFiscalSubtotal(call: MethodCall, result: MethodChannel.Result) {
        val receipt = requireReceiptCommands()
        val print = call.argument<String>("print")
            ?: call.argument<Int>("print")?.toString()
            ?: run {
                result.error("ARG", "print required", null)
                return
            }
        val display = call.argument<String>("display")
            ?: call.argument<Int>("display")?.toString()
            ?: run {
                result.error("ARG", "display required", null)
                return
            }
        val discountType = call.argument<String>("discountType")
            ?: call.argument<Int>("discountType")?.toString()
            ?: run {
                result.error("ARG", "discountType required", null)
                return
            }
        val discountValue = call.argument<String>("discountValue")
            ?: call.argument<Double>("discountValue")?.toString()
            ?: run {
                result.error("ARG", "discountValue required", null)
                return
            }
        receipt.FiscalSubtotal(print, display, discountType, discountValue)
        result.success(true)
    }

    private fun handleSellItem(call: MethodCall, result: MethodChannel.Result) {
        val receipt = requireReceiptCommands()
        val name = call.argument<String>("name") ?: "Item"
        val taxCode = call.argument<String>("taxCode") ?: "0"
        val price = (call.argument<Double>("price") ?: 0.0).toString()
        val quantity = (call.argument<Double>("quantity") ?: 1.0).toString()
        val discountType = call.argument<String>("discountType") ?: "0"
        val discountValue = (call.argument<Double>("discountValue") ?: 0.0).toString()
        val department = call.argument<String>("department") ?: "0"
        val unit = call.argument<String>("unit") ?: "pcs"
        receipt.FiscalSale(name, taxCode, price, quantity, discountType, discountValue, department, unit)
        result.success(true)
    }

    private fun handleCashInCashOut(call: MethodCall, result: MethodChannel.Result) {
        val receipt = requireReceiptCommands()
        val amount = call.argument<Double>("amount") ?: 0.0
        val foreignCurrency = call.argument<Boolean>("foreignCurrency") ?: false
        val outData = Array(2) { "" }
        receipt.CashInCashOut(amount, foreignCurrency, outData)
        result.success(mapOf("outData" to outData.toList()))
    }
    private fun handlePayment(call: MethodCall, result: MethodChannel.Result) {
        val receipt = requireReceiptCommands()
        val type = call.argument<String>("type") ?: "0"
        val amount = (call.argument<Double>("amount") ?: 0.0).toString()
        receipt.FiscalTotal(type, amount)
        result.success(true)
    }

    private fun handleCloseReceipt(result: MethodChannel.Result) {
        val closed = requireReceiptCommands().FiscalClose()
        result.success(closed)
    }

    private fun handleCancelReceipt(result: MethodChannel.Result) {
        val canceled = requireReceiptCommands().FiscalCancel()
        result.success(canceled)
    }

    private fun handleReport(kind: Char, result: MethodChannel.Result) {
        val report = requireReportCommands()
        val out = IntArray(1)
        report.ReportECR(kind, out)
        result.success(out.firstOrNull() ?: 0)
    }

    private fun handleGetStatus(result: MethodChannel.Result) {
        requireDevice()
        val info = mapOf(
            "model" to PrinterManager.instance.modelVendorName,
            "connector" to PrinterManager.getsConnectorType(),
            "libVersion" to BuildInfo.VERSION
        )
        result.success(info)
    }

    private fun requireDevice(): DatecsFiscalDevice {
        val device = PrinterManager.instance.fiscalDevice ?: throw IllegalStateException("Not connected")
        if (!device.isConnectedDevice) {
            throw IllegalStateException("Not connected")
        }
        return device
    }

    private fun requireReceiptCommands(): cmdReceipt {
        requireDevice()
        return cmdReceipt()
    }

    private fun requireConfigCommands(): cmdConfig {
        requireDevice()
        return cmdConfig()
    }

    private fun requireReportCommands(): cmdReport {
        requireDevice()
        return cmdReport()
    }

    private fun registerUsbReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(actionUsbPermission)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(usbPermissionReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterUsbReceiver() {
        if (!receiverRegistered) return
        try {
            appContext.unregisterReceiver(usbPermissionReceiver)
        } catch (_: IllegalArgumentException) {
            // receiver was already unregistered
        } finally {
            receiverRegistered = false
        }
    }
    companion object {
        private const val CONNECT_TIMEOUT_MS = 7000L
        private const val ERROR_USB_TIMEOUT = "USB_TIMEOUT"
        private const val MESSAGE_USB_TIMEOUT = "Casa de marcat nu este conectata. Verificati ca este in modul 06 Conexiune PC "

    }
}


