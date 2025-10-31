import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/pigeon.dart',
    kotlinOut: 'android/src/main/kotlin/billion/group/wireguard_flutter/Pigeon.kt',
    kotlinOptions: KotlinOptions(
      errorClassName: 'WireGuardFlutterError',
      package: 'billion.group.wireguard_flutter',
    ),
  ),
)

class InstalledApp {
  String? name;
  String? packageName;
  Uint8List? icon;
}

class Stats {
  int? rx;
  int? tx;
}

@HostApi()
abstract class WireGuardHostApi {
  @async
  void initialize(String interfaceName);

  @async
  void startVpn(
    String serverAddress,
    String wgQuickConfig,
    String providerBundleIdentifier,
    List<String>? allowedApplications,
    List<String>? disallowedApplications,
  );

  @async
  void stopVpn();

  @async
  String stage();

  @async
  void refreshStage();

  @async
  Stats getStats(String tunnelName);

  @async
  List<InstalledApp?> getInstalledApplications();
}
