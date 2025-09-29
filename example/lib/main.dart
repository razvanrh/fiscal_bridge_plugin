import 'package:flutter/material.dart';
import 'package:fiscal_bridge_plugin/fiscal_bridge_plugin.dart';

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(debugShowCheckedModeBanner: false, theme: ThemeData(useMaterial3: true), home: const UsbPrinterDemo());
  }
}

class UsbPrinterDemo extends StatefulWidget {
  const UsbPrinterDemo({super.key});

  @override
  State<UsbPrinterDemo> createState() => _UsbPrinterDemoState();
}

class _UsbPrinterDemoState extends State<UsbPrinterDemo> {
  Map<String, dynamic>? _selectedDevice;
  Map<String, dynamic>? _connectedInfo;
  bool _busy = false;
  final List<String> _log = <String>[];

  void _pushLog(String message) {
    setState(() {
      _log.insert(0, '${DateTime.now().toIso8601String()}  $message');
    });
  }

  Future<T?> _guard<T>(String label, Future<T> Function() operation) async {
    if (_busy) {
      _pushLog('Already running: $label');
      return null;
    }
    setState(() => _busy = true);
    try {
      _pushLog('[start] $label');
      final T result = await operation();
      _pushLog('[ok] $label -> $result');
      return result;
    } catch (err, stack) {
      _pushLog('[err] $label -> $err');
      debugPrint('ERROR during $label: $err\n$stack');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Eroare: $err')));
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
    return null;
  }

  Future<void> _selectDevice() async {
    await _guard('usbListDevices', () async {
      final devices = await FiscalBridge.usbListDevices();
      if (!mounted) return devices;
      if (devices.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Nu s-au gasit device-uri USB.')));
        return devices;
      }
      final picked = await showDialog<Map<String, dynamic>>(
        context: context,
        builder: (BuildContext dialogContext) {
          return AlertDialog(
            title: const Text('Alege device USB'),
            content: SizedBox(
              width: 420,
              height: 320,
              child: ListView.separated(
                itemCount: devices.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (_, int index) {
                  final device = devices[index];
                  final name = (device['productName'] as String?)?.trim();
                  return ListTile(
                    title: Text(name?.isNotEmpty == true ? name! : 'Fara nume'),
                    subtitle: Text('VID: ${device['vid']}  -  PID: ${device['pid']}'),
                    onTap: () => Navigator.of(dialogContext).pop(device),
                  );
                },
              ),
            ),
            actions: [TextButton(onPressed: () => Navigator.of(dialogContext).pop(null), child: const Text('Anuleaza'))],
          );
        },
      );
      if (picked != null && mounted) {
        setState(() => _selectedDevice = picked);
        _pushLog('Device selectat: VID=${picked['vid']} PID=${picked['pid']}');
      }
      return devices;
    });
  }

  Future<Map<String, dynamic>> _requireDevice() async {
    final device = _selectedDevice;
    if (device == null) {
      throw StateError('Selecteaza un device USB mai intai');
    }
    return device;
  }

  Future<void> _callUsbRequestPermission() async {
    final device = await _requireDevice();
    await _guard('usbRequestPermission', () async {
      final granted = await FiscalBridge.usbRequestPermission(device['vid'] as int, device['pid'] as int);
      return {'granted': granted};
    });
  }

  Future<void> _callUsbConnect() async {
    final device = await _requireDevice();
    await _guard('usbConnect', () async {
      final info = await FiscalBridge.usbConnect(device['vid'] as int, device['pid'] as int);
      setState(() => _connectedInfo = info);
      return info;
    });
  }

  Future<void> _callUsbDisconnect() async {
    await _guard('usbDisconnect', () async {
      await FiscalBridge.usbDisconnect();
      setState(() => _connectedInfo = null);
      return 'done';
    });
  }

  Future<void> _callSetDateTime() async {
    await _guard('setDateTime', () => FiscalBridge.setDateTime(DateTime.now()));
  }

  Future<void> _callNonFiscalOpen() async {
    await _guard('nonFiscalOpen', FiscalBridge.nonFiscalOpen);
  }

  Future<void> _callPrintNonFiscal() async {
    await _guard('printNonFiscalText', () => FiscalBridge.printNonFiscalText('Salut din demo', bold: true, align: 'C'));
  }

  Future<void> _callNonFiscalClose() async {
    await _guard('nonFiscalClose', () => FiscalBridge.nonFiscalClose(cut: false));
  }

  Future<void> _callOpenReceipt() async {
    await _guard('openReceipt', () {
      return FiscalBridge.openReceipt(operatorId: '1', operatorPassword: '0001', tillNo: '1', invoiceNumber: '', customerTaxId: 'RO123456');
    });
  }

  Future<void> _callSellItem() async {
    await _guard('sellItem', () {
      return FiscalBridge.sellItem(
        //max 72 caractere
        name: 'Produs demo',
        taxCode: '1',
        price: 1.21,
        quantity: 1.0,
        discountType: '0',
        discountValue: 0.0,
        department: '0',
        unit: 'pcs',
      );
    });
  }

  Future<void> _callCashInCashOut() async {
    await _guard('cashInCashOut', () async {
      return FiscalBridge.cashInCashOut(amount: 1.0, foreignCurrency: false);
    });
  }

  Future<void> _callPayment(String type, double amount) async {
    await _guard('payment', () {
      return FiscalBridge.payment(type: type, amount: amount);
    });
  }

  Future<void> _callCloseReceipt() async {
    await _guard('closeReceipt', FiscalBridge.closeReceipt);
  }

  Future<void> _callCancelReceipt() async {
    await _guard('cancelReceipt', FiscalBridge.cancelReceipt);
  }

  Future<void> _callReportX() async {
    await _guard('reportX', () async => {'result': await FiscalBridge.reportX()});
  }

  Future<void> _callReportZ() async {
    await _guard('reportZ', () async => {'result': await FiscalBridge.reportZ()});
  }

  Future<void> _callGetStatus() async {
    await _guard('getStatus', FiscalBridge.getStatus);
  }

  @override
  Widget build(BuildContext context) {
    final deviceLabel = _selectedDevice == null ? 'Niciun Device selectat' : 'Device: VID=${_selectedDevice!['vid']} PID=${_selectedDevice!['pid']}';
    final connectionLabel = _connectedInfo == null ? 'Neconectat' : 'Conectat: ${_connectedInfo!['model']} (${_connectedInfo!['connector']})';

    final buttonStyle = FilledButton.styleFrom(minimumSize: const Size(0, 44));

    return Scaffold(
      appBar: AppBar(title: const Text('Fiscal Bridge - Demo functii')),
      body: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(deviceLabel),
                  Text(connectionLabel),
                  if (_busy) const Padding(padding: EdgeInsets.only(top: 8), child: LinearProgressIndicator()),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                children: [
                  const Text('USB'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      FilledButton(onPressed: _busy ? null : _selectDevice, style: buttonStyle, child: const Text('List & select device')),
                      FilledButton(onPressed: _busy ? null : _callUsbRequestPermission, style: buttonStyle, child: const Text('Request permission')),
                      FilledButton(onPressed: _busy ? null : _callUsbConnect, style: buttonStyle, child: const Text('Connect')),
                      FilledButton(onPressed: _busy ? null : _callUsbDisconnect, style: buttonStyle, child: const Text('Disconnect')),
                    ],
                  ),
                  const SizedBox(height: 24),
                  const Text('Setari & Non-fiscal'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      FilledButton(onPressed: _busy ? null : _callSetDateTime, style: buttonStyle, child: const Text('Set date/time')),
                      FilledButton(onPressed: _busy ? null : _callNonFiscalOpen, style: buttonStyle, child: const Text('Non-fiscal open')),
                      FilledButton(onPressed: _busy ? null : _callPrintNonFiscal, style: buttonStyle, child: const Text('Print non-fiscal text')),
                      FilledButton(onPressed: _busy ? null : _callNonFiscalClose, style: buttonStyle, child: const Text('Non-fiscal close')),
                    ],
                  ),
                  const SizedBox(height: 24),
                  const Text('Fiscal'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      FilledButton(onPressed: _busy ? null : _callOpenReceipt, style: buttonStyle, child: const Text('Open receipt')),
                      FilledButton(onPressed: _busy ? null : _callSellItem, style: buttonStyle, child: const Text('Sell item')),
                      FilledButton(onPressed: _busy ? null : () => _callPayment('0', 1.00), style: buttonStyle, child: const Text('Cash 1.00')),
                      FilledButton(onPressed: _busy ? null : () => _callPayment('1', 2.00), style: buttonStyle, child: const Text('Card 2.00')),
                      FilledButton(onPressed: _busy ? null : _callCashInCashOut, style: buttonStyle, child: const Text('Cash in/out')),
                      FilledButton(onPressed: _busy ? null : _callCloseReceipt, style: buttonStyle, child: const Text('Close receipt')),
                      FilledButton(onPressed: _busy ? null : _callCancelReceipt, style: buttonStyle, child: const Text('Cancel receipt')),
                    ],
                  ),
                  const SizedBox(height: 24),
                  const Text('Rapoarte & status'),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 12,
                    runSpacing: 12,
                    children: [
                      FilledButton(onPressed: _busy ? null : _callReportX, style: buttonStyle, child: const Text('Report X')),
                      FilledButton(onPressed: _busy ? null : _callReportZ, style: buttonStyle, child: const Text('Report Z')),
                      FilledButton(onPressed: _busy ? null : _callGetStatus, style: buttonStyle, child: const Text('Get status')),
                    ],
                  ),
                  const SizedBox(height: 24),
                  const Text('Log (ultimele evenimente)'),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(border: Border.all(color: Theme.of(context).colorScheme.outline), borderRadius: BorderRadius.circular(8)),
                    constraints: const BoxConstraints(minHeight: 160),
                    child:
                        _log.isEmpty
                            ? const Text('Nimic de raportat inca.')
                            : SizedBox(height: 200, child: ListView.builder(itemCount: _log.length, itemBuilder: (_, int index) => Text(_log[index]))),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
