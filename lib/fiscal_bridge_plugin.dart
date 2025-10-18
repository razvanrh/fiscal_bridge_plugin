import 'package:flutter/services.dart';

class FiscalBridge {
  static const MethodChannel _ch = MethodChannel('ro.masipos.fiscal/bridge');

  // USB
  static Future<List<Map<String, dynamic>>> usbListDevices() async {
    final list = await _ch.invokeMethod('usbListDevices');
    return (list as List)
        .map((e) => (e as Map).cast<String, dynamic>())
        .toList();
  }

  static Future<bool> usbRequestPermission(int vid, int pid) async {
    final ok = await _ch.invokeMethod('usbRequestPermission', {
      'vid': vid,
      'pid': pid,
    });
    return ok == true;
  }

  static Future<Map<String, dynamic>> usbConnect(int vid, int pid) async {
    final info = await _ch.invokeMethod('usbConnect', {'vid': vid, 'pid': pid});
    return (info as Map).cast<String, dynamic>();
  }

  static Future<void> usbDisconnect() => _ch.invokeMethod('usbDisconnect');

  // Settings
  static Future<void> setDateTime(DateTime dt) {
    final formatted =
        "${dt.day.toString().padLeft(2, '0')}-"
        "${dt.month.toString().padLeft(2, '0')}-"
        "${(dt.year % 100).toString().padLeft(2, '0')} "
        "${dt.hour.toString().padLeft(2, '0')}:"
        "${dt.minute.toString().padLeft(2, '0')}:"
        "${dt.second.toString().padLeft(2, '0')}";
    return _ch.invokeMethod('setDateTime', {'formatted': formatted});
  }

  // Non-fiscal
  static Future<void> nonFiscalOpen() => _ch.invokeMethod('nonFiscalOpen');

  static Future<void> printNonFiscalText(
    String text, {
    bool bold = false,
    bool italic = false,
    bool doubleH = false,
    bool underline = false,
    String align = 'L',
    bool condensed = false,
    @Deprecated('Use condensed instead') bool? doubleW,
  }) =>
      _ch.invokeMethod('printNonFiscalText', {
        'text': text,
        'bold': bold,
        'italic': italic,
        'doubleH': doubleH,
        'underline': underline,
        'align': align,
        'condensed': doubleW ?? condensed,
      });

  static Future<void> drawerKickOut() =>
      _ch.invokeMethod('drawerKickOut');

  static Future<void> nonFiscalClose({bool cut = true}) =>
      _ch.invokeMethod('nonFiscalClose', {'cut': cut});

  // Fiscal
  static Future<void> fiscalSubtotal({
    required String print,
    required String display,
    required String discountType,
    required String discountValue,
  }) =>
      _ch.invokeMethod('fiscalSubtotal', {
        'print': print,
        'display': display,
        'discountType': discountType,
        'discountValue': discountValue,
      });

  static Future<void> openReceipt({
    required String operatorId,
    required String operatorPassword,
    required String tillNo,
    String? invoiceNumber,
    String? customerTaxId,
  }) =>
      _ch.invokeMethod('openReceipt', {
        'operatorId': operatorId,
        'operatorPassword': operatorPassword,
        'tillNo': tillNo,
        'invoiceNumber': invoiceNumber ?? '',
        'customerTaxId': customerTaxId ?? '',
      });

  static Future<void> sellItem({
    required String name,
    required String taxCode,
    required double price,
    required double quantity,
    String discountType = '0',
    double discountValue = 0.0,
    String department = '0',
    String unit = 'pcs',
  }) {
    // aplicăm restricția: max 72 caractere
    final trimmedName = name.length > 72 ? name.substring(0, 72) : name;

    return _ch.invokeMethod('sellItem', {
      'name': trimmedName,
      'taxCode': taxCode,
      'price': price,
      'quantity': quantity,
      'discountType': discountType,
      'discountValue': discountValue,
      'department': department,
      'unit': unit,
    });
  }

  static Future<Map<String, dynamic>> cashInCashOut({
    required double amount,
    bool foreignCurrency = false,
  }) async {
    final result = await _ch.invokeMethod('cashInCashOut', {
      'amount': amount,
      'foreignCurrency': foreignCurrency,
    });
    return (result as Map).cast<String, dynamic>();
  }

  static Future<void> payment({required String type, required double amount}) =>
      _ch.invokeMethod('payment', {'type': type, 'amount': amount});

  static Future<void> closeReceipt() => _ch.invokeMethod('closeReceipt');

  static Future<void> cancelReceipt() => _ch.invokeMethod('cancelReceipt');

  // Reports & status
  static Future<int> reportX() async =>
      (await _ch.invokeMethod('reportX')) as int;

  static Future<int> reportZ() async =>
      (await _ch.invokeMethod('reportZ')) as int;

  static Future<Map<String, dynamic>> getStatus() async =>
      (await _ch.invokeMethod('getStatus') as Map).cast<String, dynamic>();
}

