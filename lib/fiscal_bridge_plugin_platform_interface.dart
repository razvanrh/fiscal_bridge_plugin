import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'fiscal_bridge_plugin_method_channel.dart';

abstract class FiscalBridgePluginPlatform extends PlatformInterface {
  /// Constructs a FiscalBridgePluginPlatform.
  FiscalBridgePluginPlatform() : super(token: _token);

  static final Object _token = Object();

  static FiscalBridgePluginPlatform _instance = MethodChannelFiscalBridgePlugin();

  /// The default instance of [FiscalBridgePluginPlatform] to use.
  ///
  /// Defaults to [MethodChannelFiscalBridgePlugin].
  static FiscalBridgePluginPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FiscalBridgePluginPlatform] when
  /// they register themselves.
  static set instance(FiscalBridgePluginPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
