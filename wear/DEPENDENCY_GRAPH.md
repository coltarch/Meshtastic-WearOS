# Wear dependency graph

## Startup selection

`WearApplication` starts only `WearKoinApp`, the common graph, and reads the persisted operating mode through the
existing `WatchPrefs` DataStore before registering a mode-specific module. Missing, corrupt, and unknown values resolve
to Companion in `WatchPrefsImpl`; a stored Standalone value also falls back to Companion when the watch has no BLE
hardware. Exactly one of `WearCompanionModule` and `WearStandaloneRadioModule` is then loaded for the process lifetime.

Mode changes are persisted only after confirmation. The active `WearStartupState` is deliberately not changed, no Koin
module is unloaded, and Settings displays “Restart the app to apply” until the next normal process start.

## Common graph

The common startup graph contains:

- `WearCommonModule`: the immutable startup selection, Watch Settings view model, and three lightweight in-memory
  repositories used by the Data Layer listener.
- `CoreDiModule`: coroutine scopes and dispatchers.
- The existing Core preference modules and Android DataStore providers. The full preference module is registered here
  rather than loading a second Watch DataStore with the Standalone graph; definitions remain lazy, and only
  `WatchPrefs` is resolved during startup.

No direct-radio controller, BLE scanner, Room database, Ktor client, WorkManager instance, USB object, or Data Layer
client is defined by the common graph.

## Companion graph

`WearCompanionModule` contains only the Play Services `WearDataLayerClient` and Companion screen view models. It renders
the synchronized nodes/messages/channels experience and sends reply, open-on-phone, and synchronization commands. It
does not contain the direct-radio application graph.

`DataLayerListenerService` uses common in-memory repositories so Android can safely create it in either mode, but it
ignores incoming phone synchronization while the active startup mode is Standalone.

## Standalone-radio graph

`WearStandaloneRadioModule` contains the existing BLE, data/domain/repository/service, Room, and direct-radio
definitions. It supplies a Wear-only `WearBleRadioTransportFactory`, which supports BLE and does not resolve Android USB
serial or TCP/Ktor platform bindings. The connection view model receives the BLE scanner, Bluetooth repository, radio
controller, preferences, and transport factory as `Lazy` dependencies:

- Merely loading the graph or opening the connection screen starts no scan.
- The scanner and Bluetooth implementation resolve only after the user requests discovery and grants permission.
- The radio controller and its Room-backed repositories resolve only after the user selects a radio.
- Ktor has no Wear Standalone binding in this BLE-only implementation.
- WorkManager remains without its AndroidX Startup initializer and its Koin provider is not resolved by discovery or
  connection setup.
- USB manager, serial probing, and the Android multi-transport factory are not part of the Wear Standalone graph.

Standalone navigation opens a two-page connection/settings experience. Companion-only commands and screens are not
reachable, and `WearDataLayerClient` is not defined.
