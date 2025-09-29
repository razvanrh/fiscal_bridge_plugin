import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'fiscal_bridge_plugin_platform_interface.dart';

/// An implementation of [FiscalBridgePluginPlatform] that uses method channels.
class MethodChannelFiscalBridgePlugin extends FiscalBridgePluginPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('fiscal_bridge_plugin');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
