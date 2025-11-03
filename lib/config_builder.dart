class WgPeer {
  WgPeer({
    required this.publicKey,
    required this.allowedIps,
    this.endpoint,
    this.presharedKey,
    this.persistentKeepalive,
  });

  final String publicKey;
  final List<String> allowedIps;
  final String? endpoint;
  final String? presharedKey;
  final int? persistentKeepalive;
}

class WgInterface {
  WgInterface({
    required this.privateKey,
    required this.addresses,
    this.dnsServers = const [],
    this.allowedApplications = const [],
    this.disallowedApplications = const [],
  });

  final String privateKey;
  final List<String> addresses;
  final List<String> dnsServers;
  final List<String> allowedApplications;
  final List<String> disallowedApplications;
}

class WgConfig {
  WgConfig({required this.interface, required this.peers});

  final WgInterface interface;
  final List<WgPeer> peers;

  String toWgQuickString() {
    final buffer = StringBuffer();

    buffer.writeln('[Interface]');
    buffer.writeln('PrivateKey = ${interface.privateKey}');

    for (final address in interface.addresses) {
      buffer.writeln('Address = $address');
    }

    if (interface.dnsServers.isNotEmpty) {
      buffer.writeln('DNS = ${interface.dnsServers.join(', ')}');
    }

    if (interface.allowedApplications.isNotEmpty) {
      buffer.writeln(
          'IncludedApplications = ${interface.allowedApplications.join(', ')}');
    } else if (interface.disallowedApplications.isNotEmpty) {
      buffer.writeln(
          'ExcludedApplications = ${interface.disallowedApplications.join(', ')}');
    }

    for (final peer in peers) {
      buffer.writeln();
      buffer.writeln('[Peer]');
      buffer.writeln('PublicKey = ${peer.publicKey}');

      if (peer.presharedKey != null) {
        buffer.writeln('PresharedKey = ${peer.presharedKey}');
      }

      if (peer.endpoint != null) {
        buffer.writeln('Endpoint = ${peer.endpoint}');
      }

      if (peer.persistentKeepalive != null) {
        buffer.writeln('PersistentKeepalive = ${peer.persistentKeepalive}');
      }

      buffer.writeln('AllowedIPs = ${peer.allowedIps.join(', ')}');
    }

    return buffer.toString();
  }
}

class WgConfigBuilder {
  String? _privateKey;
  List<String> _addresses = [];
  List<String> _dnsServers = [];
  List<String> _allowedApplications = [];
  List<String> _disallowedApplications = [];
  final List<WgPeer> _peers = [];

  WgConfigBuilder setInterface({
    required String privateKey,
    List<String> addresses = const [],
    List<String> dnsServers = const [],
  }) {
    _privateKey = privateKey;
    _addresses = addresses;
    _dnsServers = dnsServers;
    return this;
  }

  WgConfigBuilder addPeer({
    required String publicKey,
    required List<String> allowedIps,
    String? endpoint,
    String? presharedKey,
    int? persistentKeepalive,
  }) {
    _peers.add(WgPeer(
      publicKey: publicKey,
      allowedIps: allowedIps,
      endpoint: endpoint,
      presharedKey: presharedKey,
      persistentKeepalive: persistentKeepalive,
    ));
    return this;
  }

  WgConfigBuilder setAllowedApplications(List<String> packages) {
    _allowedApplications = packages;
    _disallowedApplications = []; // Ensure mutual exclusion
    return this;
  }

  WgConfigBuilder setDisallowedApplications(List<String> packages) {
    _disallowedApplications = packages;
    _allowedApplications = []; // Ensure mutual exclusion
    return this;
  }

  WgConfig build({required String providerBundleIdentifier}) {
    if (_privateKey == null) {
      throw Exception('Interface private key is required.');
    }

    final finalAllowedApplications = List<String>.from(_allowedApplications);
    if (finalAllowedApplications.isNotEmpty) {
      if (!finalAllowedApplications.contains(providerBundleIdentifier)) {
        finalAllowedApplications.add(providerBundleIdentifier);
      }
    }

    return WgConfig(
      interface: WgInterface(
        privateKey: _privateKey!,
        addresses: _addresses,
        dnsServers: _dnsServers,
        allowedApplications: finalAllowedApplications,
        disallowedApplications: _disallowedApplications,
      ),
      peers: _peers,
    );
  }
}
