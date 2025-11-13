import 'dart:async';

import 'package:flutter/services.dart';
import 'package:wireguard_flutter/pigeon.dart' as pigeon;

import 'models.dart';
export 'models.dart';

enum VpnStage {
  connected('connected'),
  connecting('connecting'),
  disconnecting('disconnecting'),
  disconnected('disconnected'),
  waitingConnection('wait_connection'),
  authenticating('authenticating'),
  reconnect('reconnect'),
  noConnection('no_connection'),
  preparing('prepare'),
  denied('denied'),
  exiting('exiting');

  final String code;
  const VpnStage(this.code);
}

class WireGuardFlutter {
  WireGuardFlutter._();
  static final WireGuardFlutter instance = WireGuardFlutter._();

  final pigeon.WireGuardHostApi _api = pigeon.WireGuardHostApi();

  static const _eventChannelVpnStage = 'billion.group.wireguard_flutter/wgstage';
  static const _eventChannel = EventChannel(_eventChannelVpnStage);

  Stream<VpnStage> get vpnStageSnapshot {
    return _eventChannel.receiveBroadcastStream().map(
          (event) => VpnStage.values.firstWhere(
            (stage) => stage.code == event,
            orElse: () => VpnStage.noConnection,
          ),
        );
  }

  Future<void> initialize({required String interfaceName}) {
    return _api.initialize(interfaceName);
  }

  Future<void> startVpn({
    required String serverAddress,
    required String wgQuickConfig,
    required String providerBundleIdentifier,
  }) {
    return _api.startVpn(
      serverAddress,
      wgQuickConfig,
      providerBundleIdentifier,
    );
  }

  Future<void> stopVpn() => _api.stopVpn();

  Future<void> refreshStage() => _api.refreshStage();

  Future<VpnStage> stage() async {
    final stageString = await _api.stage();
    return VpnStage.values.firstWhere(
      (s) => s.code == stageString,
      orElse: () => VpnStage.noConnection,
    );
  }

  Future<Stats> getStats({required String tunnelName}) async {
    final pigeon.Stats stats = await _api.getStats(tunnelName);
    return Stats(rx: stats.rx?.toInt() ?? 0, tx: stats.tx?.toInt() ?? 0);
  }

  Future<List<InstalledApp>> getInstalledApplications() async {
    final List<pigeon.InstalledApp?> apps = await _api.getInstalledApplications();
    return apps
        .where((app) => app != null)
        .map((app) => InstalledApp(
              name: app!.name ?? '',
              packageName: app.packageName ?? '',
              icon: app.icon ?? Uint8List(0),
            ))
        .toList();
  }
}