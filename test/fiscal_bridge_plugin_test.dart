import 'package:flutter_test/flutter_test.dart';
import 'package:fiscal_bridge_plugin/fiscal_bridge_plugin.dart';
import 'package:fiscal_bridge_plugin/fiscal_bridge_plugin_platform_interface.dart';
import 'package:fiscal_bridge_plugin/fiscal_bridge_plugin_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'package:fiscal_bridge_plugin/fiscal_bridge_plugin.dart';

class MockFiscalBridgePluginPlatform with MockPlatformInterfaceMixin implements FiscalBridgePluginPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FiscalBridgePluginPlatform initialPlatform = FiscalBridgePluginPlatform.instance;

  test('$MethodChannelFiscalBridgePlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFiscalBridgePlugin>());
  });

  testWidgets('getPlatformVersion test', (tester) async {
    // final FiscalBridgePlugin plugin = FiscalBridgePlugin();
    // final String? version = await plugin.getPlatformVersion();

    final info = await FiscalBridge.getStatus();
    expect(info['libVersion']?.toString().isNotEmpty, true);
  });
}
