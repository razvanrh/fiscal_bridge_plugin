package ro.masipos.fiscal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
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
import java.io.IOException

class FiscalBridgePlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var appContext: Context

    private val actionUsbPermission = "ro.masipos.fiscal.USB_PERMISSION"
    private var pendingUsbPermissionResult: MethodChannel.Result? = null
    private var receiverRegistered = false

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
                "nonFiscalClose" -> handleNonFiscalClose(call, result)
                "openReceipt" -> handleOpenReceipt(call, result)
                "sellItem" -> handleSellItem(call, result)
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

        val connector = UsbDeviceConnector(appContext, usbManager, device)
        connector.connect()

        PrinterManager.instance.init(connector)

        val info = mapOf(
            "model" to PrinterManager.instance.modelVendorName,
            "connector" to PrinterManager.getsConnectorType(),
            "libVersion" to BuildInfo.VERSION
        )
        result.success(info)
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
        val doubleHeight = if (call.argument<Boolean>("doubleH") == true) '1' else '0'
        val doubleWidth = if (call.argument<Boolean>("doubleW") == true) '1' else '0'
        val underline = if (call.argument<Boolean>("underline") == true) '1' else '0'
        val align = when (call.argument<String>("align") ?: "L") {
            "C" -> '1'
            "R" -> '2'
            else -> '0'
        }
        receipt.PrintNonFiscalText(text, bold, doubleHeight, doubleWidth, underline, align, '0')
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
}
