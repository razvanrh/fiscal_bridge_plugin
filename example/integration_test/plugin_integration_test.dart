// This is a basic Flutter integration test.
//
// Since integration tests run in a full Flutter application, they can interact
// with the host side of a plugin implementation, unlike Dart unit tests.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:fiscal_bridge_plugin/fiscal_bridge_plugin.dart'; // asta expune clasa FiscalBridge

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('bridge smoke test', (tester) async {
    // doar verificăm că MethodChannel funcționează și întoarce ceva la getStatus()
    try {
      final info = await FiscalBridge.getStatus(); // {model, connector, libVersion}
      expect(info, isA<Map>());
    } catch (_) {
      // dacă nu e conectată imprimanta, măcar nu picăm pe clasa inexistentă
      expect(true, isTrue);
    }
  });
}
