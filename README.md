# WireGuard Flutter

Un plugin de Flutter para configurar y controlar túneles VPN de WireGuard en **Android**, con control de firewall por aplicación.

---

## Plataformas Soportadas

| Android | iOS   | macOS | Windows | Linux |
| :-----: | :---: | :---: | :-----: | :---: |
|   ✔️    |  ❌   |  ❌   |    ❌   |  ❌   |

*Esta versión de la librería ha sido refactorizada para ser exclusiva de Android.*

## Características

- Creación y gestión de túneles WireGuard.
- Comunicación entre Flutter y Android 100% tipada y segura usando **Pigeon**.
- **Modo Firewall:** Permite que solo las aplicaciones seleccionadas tengan acceso a la red.
- **Modo Split-Tunnel:** Excluye a las aplicaciones seleccionadas del túnel VPN.
- Escucha de cambios de estado de la conexión en tiempo real.
- Obtención de estadísticas de uso (Rx/Tx).

## Instalación

Añade la dependencia a tu archivo `pubspec.yaml`:

```yaml
dependencies:
  wireguard_flutter: ^0.1.0 # Reemplaza con la versión que estés usando
```

## Uso

### 1. Obtener la instancia

Accede a la instancia del plugin a través de un singleton.

```dart
final wireguard = WireGuardFlutter.instance;
```

### 2. Inicializar

Antes de conectar, inicializa la librería con un nombre para la interfaz del túnel.

```dart
await wireguard.initialize(interfaceName: 'wg0');
```

### 3. Obtener Aplicaciones Instaladas (Opcional)

Si vas a usar el firewall o el split-tunnel, necesitarás obtener la lista de paquetes de las aplicaciones instaladas en el dispositivo.

```dart
final List<InstalledApp> apps = await wireguard.getInstalledApplications();

for (final app in apps) {
  print('App: ${app.name}, Paquete: ${app.packageName}');
  // app.icon contiene los bytes del icono para mostrar en un Image.memory
}
```

### 4. Iniciar la Conexión

El método `startVpn` permite tres modos de funcionamiento.

#### a) Conexión Normal (Todo el tráfico por la VPN)

No pases ninguna lista de aplicaciones. Todo el tráfico del dispositivo pasará por el túnel.

```dart
await wireguard.startVpn(
  serverAddress: 'demo.wireguard.com:51820',
  wgQuickConfig: '''[Interface]
PrivateKey = ...
Address = ...
''',
  providerBundleIdentifier: 'com.tu.paquete',
);
```

#### b) Modo Firewall (Solo apps permitidas)

Pasa una lista de paquetes de aplicaciones al parámetro `allowedApplications`. Solo estas apps tendrán acceso a la red. El resto serán bloqueadas (en Android 10+).

```dart
await wireguard.startVpn(
  // ... otros parámetros
  allowedApplications: ['com.android.chrome', 'com.slack'],
);
```

#### c) Modo Split-Tunnel (Apps excluidas)

Pasa una lista de paquetes de aplicaciones al parámetro `disallowedApplications`. Todas las apps usarán la VPN, excepto las de la lista, que usarán la red normal.

```dart
await wireguard.startVpn(
  // ... otros parámetros
  disallowedApplications: ['com.netflix.mediaclient'],
);
```

### 5. Escuchar Cambios de Estado

Usa el `vpnStageSnapshot` para reaccionar a los cambios en el estado de la conexión.

```dart
wireguard.vpnStageSnapshot.listen((stage) {
  print('Nuevo estado de la VPN: $stage'); // ej. VpnStage.connected
});
```

### 6. Obtener Estadísticas

Consulta el tráfico de subida y bajada del túnel.

```dart
final Stats stats = await wireguard.getStats(tunnelName: 'wg0');
print('Bytes recibidos: ${stats.rx}');
print('Bytes transmitidos: ${stats.tx}');
```

### 7. Detener la Conexión

```dart
await wireguard.stopVpn();
```

---

*"WireGuard" is a registered trademark of Jason A. Donenfeld.*

(Refactor commit fix)