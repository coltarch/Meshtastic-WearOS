# Wear OS companion app — audit, 2026-10-14

**What this is.** A read-only audit of the Wear OS work on this fork: the `wear/` module, the
phone-side Wearable sync code, the watch settings surface, and the watch-related tests. Every
finding below is documented so it can be fixed deliberately — **no code in this PR changes
behaviour**, and no finding here is a release blocker on its own.

| | |
|---|---|
| Audited commit | `fbd3242bf0` — branch `feat/wear-os-complete` (the line that carries all the Wear work) |
| Audit date | 2026-10-14 |
| Repo | `coltarch/Meshtastic-WearOS` (this fork only — nothing was filed against `meshtastic/Meshtastic-Android`) |
| Method | Four parallel read-only code reads (watch UI/state; standalone radio + DI + Data Layer; phone-side sync; settings + tests + privacy) plus the GitHub Actions run below |
| CI evidence | run [`38512870483`](https://github.com/coltarch/Meshtastic-WearOS/actions/runs/38512870483) on this commit: `android-check` ✅ (it compiles), `lint-check` ❌, all three `test-shards` ❌ |
| Not verified | Nothing here was compiled or run on a device. The host that produced this audit is a Raspberry Pi with no JDK/Android SDK, so compile- and runtime-dependent claims carry their own confidence label. |

**Confidence labels.** `certain` = read the code and traced it. `likely` = read it, but a caller or
a runtime behaviour is unverified. `unsure` = needs a build or a device to settle.

**Severity** is about user impact, not effort: `high` = wrong/unsafe/blocking behaviour,
`medium` = visible defect or divergence from the app's own conventions, `low` = polish, hygiene,
future-proofing.

---

## 0. Status, 2026-10-16

Where each entry stands after the work that followed this audit. The findings below are left as written — this
section records their state, so nobody re-fixes something that is done and nobody chases something that is
deliberately parked.

**Closed**

- `E1`–`E3` — the missing test import, the entry point missing from the unsupported-device guard, and the startup
  tests reaching Koin.
- `E4`, `E5`, `E6`, `E7` — spotless, the detekt findings, the two flagged upstream-shaped functions (resolved with
  reasoned `@Suppress` rather than a baseline entry), and the wear `google-services.json`.
- `C1` — the five `CoroutineScope(` sites are allowlisted.
- `C2` — every phone-side Data Layer await is bounded: 8 sites through a shared `awaitOrThrow`, no bare `.await()`
  left in `WearableSyncService`.
- `C5` / `D1` (`P1`) — `WearableRadioStatus` no longer carries a position, and nothing on the watch read it.
- `B11` — `ForegroundStartPolicy.foregroundServiceType()` takes `isWatch` and never claims the location type on a
  watch.

**Parked with standalone mode** — standalone is unreachable as of PR #8: `selectWearStartupMode()` returns
`COMPANION` unconditionally and the mode control is gone from Settings. Parked, not fixed: `B1`, `B2`, `B3`, `B4`,
`B5`, `B10`, `D7`.

When the mode comes back, **fix `B1` and `B2` first**, and add a `verify()` over the standalone module set to the
wear `KoinVerificationTest`. Nothing verifies that graph today — its `HttpClient` and `WorkManager` assertions are
about them being *absent* in companion mode — which is why both of those defects passed CI. `A12` is moot while the
mode switch is gone.

**Partly addressed** — `C6` / `D2`: the payload no longer carries a position, but the watch still stores the
radio-status category and reads none of it. **Needs a re-read against current `main`** — `C4` (one failure path now
answers the requesting node; the empty-target path may not) and `C11` (the status sends now use `WatchSyncPaths`,
so the three-sources-of-truth claim may be narrower than written).

**Open** — the remaining entries. The next batch is the companion-sync group: `A1`, `C3`, `C4`, `C7`, `C8`.

---

## 1. Summary

| # | Area | Findings | Highest severity present |
|---|---|---|---|
| A | Watch app UI and state | 13 | high |
| B | Standalone radio, DI, Data Layer, packaging | 12 | high |
| C | Phone-side wearable sync | 15 | high |
| D | Watch settings, tests, privacy | 12 | high |
| E | CI findings from run `38512870483` | 7 | high (tests do not compile) |

Three findings appear in more than one area read and are merged below, with both evidence paths:
the location-in-radio-status question (C5 + D1), the zero-enabled-category sync (C3 + D6), and
turning watch connectivity off without clearing the watch (C7 + D3).

**The ten worth doing first**, in rough order of consequence:

1. `E1` — the watch tests do not compile; two of three test shards fail on that alone.
2. `E2` — the unsupported-device guard no longer covers every app entry point.
3. `C1` — the konsist architecture gate is red: three new `CoroutineScope(...)` sites are not allowlisted.
4. `C2` — no timeout on any phone-side Play Services `await()`: a hung `Task` pins the UI in Checking/Syncing permanently.
5. `B1` — the watch's standalone radio may never get its foreground service (WorkManager cannot initialize in the wear app as wired).
6. `B2` — `WearNetworkModule` is never loaded, so the standalone graph has no `HttpClient`/`ApiService`.
7. `P1` — precise location is shipped to the watch under a telemetry toggle that does not mention location.
8. `C3` — a watch-initiated sync with zero categories clears the watch and reports success.
9. `A1` — two Settings buttons on the watch are tappable no-ops.
10. `E7` — `wear/` has no `google-services.json`, so the wear google-flavor build task fails.

---

## 2. Area A — Watch app UI and state

### A1 — Settings "Notifications" and "Radio Settings" are tappable no-ops
- **Severity** high · **Confidence** certain
- **Evidence** `wear/src/main/kotlin/org/meshtastic/wear/presentation/SettingsScreen.kt:327`
  `onClick = { /* TODO: Notification settings */ },` and `:365` `onClick = { /* TODO: Radio settings */ },`
- **Why** Both render as enabled full-width buttons with icons and subtitles ("Alerts from phone",
  "Configure radio"). A watch user taps them and nothing happens — worse than an absent row.
- **Smallest change** Wire each to its destination, or remove the rows until they do something.

### A2 — Most watch UI strings are hardcoded literals, not string resources
- **Severity** medium · **Confidence** certain
- **Evidence** `NodesScreen.kt:149` `text = "MESH NODES",`; `ChatScreen.kt:240` `Text(text = "Reply (Mic / Keys)"`;
  `MessagesScreen.kt:97` `title = "Private Messages",`; `SettingsScreen.kt:319` `text = "Phone Menu",`
- **Why** Every watch screen inlines English. The repo mandates resources (`.skills/compose-ui`,
  `strings-index.txt`) and the phone already has resources for the same concepts (`channels`,
  `messages`, `settings`), so the watch is untranslatable and diverges from the phone UI.
- **Smallest change** Add the missing `wear_*` strings and swap each literal for `stringResource(...)`.

### A3 — One failed command mutates the shared sync-state holder, misreporting phone connectivity app-wide
- **Severity** medium · **Confidence** likely
- **Evidence** `wear/.../data/WearDataLayerClient.kt:150-160` — `if (nodes.isEmpty()) { syncStateHolder.phoneUnavailable() ... }`
  and `if (!delivered) syncStateHolder.syncFailed()`
- **Why** `sendCommand` backs unrelated user actions (`NodeDetailsViewModel.openOnPhone`,
  `ChatViewModel.openOnPhone`/`sendMessage`). Any transient failure flips the process-global state to
  PhoneUnavailable/SyncFailed, so the nodes list, chat header and reply UI of an otherwise-connected
  watch claim "phone not linked". Recovery needs a manual retry, and `syncCompleted()` early-returns
  while `attemptFailed` is set (`WearDataLayerClient.kt:65`), so a later success does not clear it.
- **Smallest change** Let `sendCommand` report its own result without writing the global sync state,
  or clear `attemptFailed` on the next `beginAttempt`.

### A4 — Watch re-implements `MessageStatusIcon`, drops the content description and two statuses
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/.../presentation/ChatScreen.kt:322` `Icon(imageVector = icon, contentDescription = null, ...)`
- **Why** The phone's house component (`feature/messaging/.../MessageStatusIcon.kt`) sets
  `contentDescription = stringResource(Res.string.message_delivery_status)` and handles
  SFPP_ROUTING/SFPP_CONFIRMED. The watch copy is `contentDescription = null` and its `when` falls
  through to Warning for those statuses: no delivery state for TalkBack, and two statuses render as a
  generic warning.
- **Smallest change** Reuse the house component, or add the missing branches plus a content description.

### A5 — Chat timestamps hand-roll `SimpleDateFormat`, ignoring the house formatter and locale
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/.../presentation/ChatScreen.kt:250` `val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())`
- **Why** `DateFormatter.formatTime` (core/common, already used in `NodeDetailsScreen`) renders the
  user's locale short time, respecting 12/24-hour. This forces 24-hour everywhere, and allocates a
  `SimpleDateFormat` per message per recomposition (`ChatScreen.kt:302`).
- **Smallest change** Call `DateFormatter.formatTime(msg.timestamp)` and delete the local helper.

### A6 — No ambient-mode handling; `PulsingDot` animates forever
- **Severity** medium · **Confidence** likely
- **Evidence** `wear/.../presentation/components/CommonComponents.kt:118` `val transition = rememberInfiniteTransition(label = "pulse")`
- **Why** Nothing in the watch area references ambient mode (`grep ambient` = 0 hits), and the dot is
  used in the Nodes and Chat headers. On a watch this keeps animating into AOD, preventing the
  low-power state and risking OLED burn-in.
- **Smallest change** Stop the transition / hide the dot when ambient, or make it a static dot.

### A7 — Quick-reply chips are raw clickable `Box`es below the minimum touch target
- **Severity** medium · **Confidence** likely
- **Evidence** `wear/.../presentation/ChatScreen.kt:209-213` `Modifier.weight(1f).clip(...).background(COLOR_SURFACE2).clickable { onReply(qr) }.padding(vertical = 6.dp)`
- **Why** No `minimumInteractiveComponentSize`/48 dp floor, no `Role`, 10 sp text. On a watch that is
  a hard-to-hit, hard-to-read target and offers accessibility services no button role.
- **Smallest change** Use the wear `Button`/`Chip`, or add a role plus a 48 dp minimum and larger text.

### A8 — Node card `contentDescription` suppresses the merged node-status semantics
- **Severity** medium · **Confidence** likely
- **Evidence** `wear/.../presentation/NodesScreen.kt:206-208` `.semantics { contentDescription = nodeDescription }` ("Open details for %1$s")
- **Why** The clickable Card is a merging semantics node, so an explicit `contentDescription` replaces
  the derived children text: TalkBack reads only "Open details for <name>" and drops online/offline,
  battery and SNR — the status the card exists to convey.
- **Smallest change** Drop the override, or build a description that includes status and battery.

### A9 — Node Details ignores the theme tokens, so "Alternate colors" does nothing there
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/.../presentation/NodeDetailsScreen.kt:106` `.background(MaterialTheme.colorScheme.background)` (also `:199, :233, :265`)
- **Why** Every other watch screen paints with the theme-aware `COLOR_BG_DEEP`/`COLOR_SURFACE*` tokens
  driven by `LocalHighContrastMode`. Node Details uses raw `MaterialTheme.colorScheme.*`, so the
  Display → Alternate colors (high-contrast/grey) setting silently has no effect on that screen.
- **Smallest change** Use the same `COLOR_*` tokens / high-contrast source as the rest of the watch UI.

### A10 — `ChatScreen` recomputes filter/sort/reverse on every recomposition
- **Severity** low · **Confidence** certain
- **Evidence** `wear/.../presentation/ChatScreen.kt:91` `val chatMessages = messages.filter { it.contactKey == contactKey }.sortedByDescending { it.timestamp }` (and `.reversed()` at `:120`)
- **Why** `MessagesScreen.kt:78` wraps the same work in `remember(messages)`. Chat redraws the filter,
  sort and allocation on every incoming message and every sync-state tick.
- **Smallest change** Wrap it in `remember(messages, contactKey) { ... }`.

### A11 — Watch navigation hand-rolls the deprecated wear `SwipeDismissableNavHost`
- **Severity** low · **Confidence** likely
- **Evidence** `wear/.../presentation/MainActivity.kt:143` `SwipeDismissableNavHost(navController = navController, startDestination = MAIN_PAGER_ROUTE)`
- **Why** `AGENTS.md` requires `MeshtasticNavDisplay` (Navigation 3, core/ui) and the project is
  nav-3 based; the watch uses `androidx.wear.compose.navigation` with string routes — a parallel
  navigation stack that will diverge on back handling, ViewModel scoping and transitions.
- **Smallest change** Route the watch host through the house Nav3 display, or document the exemption.

### A12 — Restart depends on window focus; if focus never arrives the mode change silently doesn't apply
- **Severity** low · **Confidence** unsure
- **Evidence** `wear/.../presentation/RestartActivity.kt:39` `if (!hasFocus || restartStarted) return`
- **Why** The kill-and-relaunch is driven entirely by `onWindowFocusChanged`. If the activity is
  created but never gains focus (display off, another window on top), nothing happens: black screen,
  old process still on the old mode, no timeout or fallback.
- **Smallest change** Trigger the restart from a post/`LaunchedEffect` with a timeout instead of relying on focus alone.

### A13 — Reply affordances vanish unless sync is `Ready`, unlike the phone
- **Severity** low · **Confidence** likely
- **Evidence** `wear/.../presentation/ChatScreen.kt:94` `val canSend = validConversation && syncState == WearCompanionSyncState.Ready`
- **Why** While a sync is in flight, retrying, or briefly failed (including A3's case), the quick
  replies and both reply buttons disappear (`ChatScreen.kt:131-148`), so the watch cannot compose a
  reply while the phone can send regardless of sync state. It also couples an input control to a
  transport-status flag.
- **Smallest change** Keep the reply UI visible; disable or queue instead of removing it.

---

## 3. Area B — Standalone radio, DI, Data Layer, packaging

### B1 — The watch's standalone radio may never get its foreground service (WorkManager cannot initialize)
- **Severity** high · **Confidence** likely (code chain read; the throw is inferred, not executed)
- **Evidence** `wear/.../di/WearStandaloneRadioModule.kt:124` `fun provideWorkManager(context: Application): WorkManager = WorkManager.getInstance(context)`;
  `wear/.../WearApplication.kt:96` `startKoin<WearKoinApp> { androidContext(this@WearApplication) }` (the phone additionally calls `workManagerFactory()`, `MeshUtilApplication.kt:119-122`);
  `wear/src/main/AndroidManifest.xml:119-122` removes `androidx.work.WorkManagerInitializer`; `WearApplication` implements no `Configuration.Provider` (the phone does, `MeshUtilApplication.kt:84`).
- **Why** Resolving `MeshServiceOrchestrator` pulls `MeshConnectionManager` → `MeshConnectionManagerImpl`
  (`workerManager: MeshWorkerManager`) → `AndroidMeshWorkerManager` → `WorkManager`, i.e. the wear-only
  definition above. With the manifest initializer removed and neither `Configuration.Provider` nor
  `workManagerFactory()`, `WorkManager.getInstance()` cannot succeed; `MeshService.onCreate`'s catch
  then stops the service (`MeshService.kt:120-124`). `MeshService` is what owns the transport on
  Android, so the standalone BLE link has no `connectedDevice` foreground service holding it.
- **Smallest change** Add `libs.koin.androidx.workmanager` to `wear/build.gradle.kts` and call
  `workManagerFactory()` in `WearApplication.startCommonGraph()`, or implement `Configuration.Provider`
  exactly as `MeshUtilApplication` does. Do not rely on the removed default initializer.

### B2 — `WearNetworkModule` is dead code, so the standalone graph has no `HttpClient`/`ApiService`
- **Severity** high · **Confidence** likely
- **Evidence** `wear/.../di/WearNetworkModule.kt:45` `class WearNetworkModule {` — the only occurrence in the repo; it is in neither `WearKoinApp.kt:27-36` (`@KoinApplication`) nor `WearStandaloneRadioModule.kt:61-79` (`includes`).
- **Why** The standalone graph includes `CoreNetworkModule` (`ApiServiceImpl` → `HttpClient`) plus
  `CoreDataModule`/`CoreUiModule`/`CoreTakServerModule`; the phone supplies the transitive deps in
  `NetworkModule.kt`/`AppKoinModule.kt`. Nothing loads the wear equivalent, so those bindings have no
  definition in either wear mode, and the file is also the only reason the wear build carries
  ktor-okhttp/-logging/-content-negotiation and usb-serial-for-android (`wear/build.gradle.kts:124-132`)
  that no other wear source uses.
- **Smallest change** Either include `WearNetworkModule` in the standalone module (and prune what it
  does not need), or delete it and drop the now-unused dependencies — then add a Koin `verify()` for
  the standalone graph (only the companion graph has one, `KoinVerificationTest.kt:69-72`).

### B3 — Standalone scan runs un-filtered for 30 s and offers every BLE device as a radio
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/.../standalone/StandaloneRadioViewModel.kt:113` `bleScanner.value.scan(SCAN_DURATION).collect { device ->` with `SCAN_DURATION = 30.seconds` (`:215`); the API takes a filter (`core/ble/.../BleScanner.kt:30`), and the phone uses it (`core/network/.../BleDeviceLocator.kt:121` `scanner.scan(timeout = timeout, serviceUuid = SERVICE_UUID, address = address)`).
- **Why** A 30-second unfiltered scan is the most expensive scanning mode on a watch radio, and the
  resulting list is unfiltered: the user can tap a headset or a phone and the app opens a GATT link to
  it (`device.name` is the only label, `StandaloneRadioScreen.kt:225`).
- **Smallest change** Pass `MeshtasticBleConstants.SERVICE_UUID` (keeping the 30 s cap).

### B4 — Nothing re-arms the standalone radio after reboot or process death
- **Severity** medium · **Confidence** likely
- **Evidence** No `startService`/`MeshService` reference anywhere in `wear/src/main`; `wear/src/main/AndroidManifest.xml` declares no `<receiver>` and no `RECEIVE_BOOT_COMPLETED`, while the phone declares both (`androidApp/src/main/AndroidManifest.xml:59, 336-350`). The only arming path is the address-change hook (`StandaloneRadioViewModel.kt:151` → `StartMeshServiceOnAddressChange`).
- **Why** The watch connects only while the Radio screen is open: the link is not restored on boot,
  app update, or process replacement, even though the address is persisted.
- **Smallest change** Declare `RECEIVE_BOOT_COMPLETED` + the shared `BootCompleteReceiver` in the wear
  manifest, and/or start `MeshService` from the wear `MainActivity` as the phone does.

### B5 — `WearBleRadioTransportFactory` reimplements the base address rule and drops the no-BLE guard
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/.../standalone/WearBleRadioTransportFactory.kt:43-44` `override fun isAddressValid(address: String?) = address?.startsWith(InterfaceId.BLUETOOTH.id) == true || address?.startsWith("!") == true` versus `core/network/.../BaseRadioTransportFactory.kt:53-55` (`InterfaceId.BLUETOOTH.id, '!' -> bluetoothRepository.isSupported`).
- **Why** Same rule, second implementation, reachable from `MeshService.onStartCommand`'s re-arm check
  and `getBondedDeviceAddress()`. It silently admits a persisted BLE address on hardware without LE —
  the case the base comment calls out. Future fixes in the base will not reach the watch.
- **Smallest change** Delegate the BLE/`!` case to the shared logic instead of re-deriving the prefix test.

### B6 — Two copies of the Data Layer wire-protocol paths
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/.../data/WearDataLayerClient.kt:177-181` `object WearDataLayerPaths { const val OPEN_CONVERSATION_ON_PHONE = "/open_on_phone" ... const val SEND_MESSAGE = "/send_message" }` versus `core/service/.../WearableIncomingActionRouter.kt:102-103` (`PATH_SEND_MESSAGE`, `PATH_OPEN_ON_PHONE`) and the phone manifest filter (`androidApp/src/main/AndroidManifest.xml:325-326`).
- **Why** The path string *is* the protocol: renaming it on one side breaks watch→phone send/open with
  no compile error and no test, because the watch never references the router constants. The watch
  already depends on `core/service`, so the copy is unnecessary. (Phone side has a third copy — see `C11`.)
- **Smallest change** Delete `WearDataLayerPaths` and reference `WearableIncomingActionRouter.PATH_*`.

### B7 — Data Layer payloads are unversioned, uncapped, and delivered twice per sync
- **Severity** medium · **Confidence** likely
- **Evidence** `wear/.../DataLayerListenerService.kt:74-118` handles the same JSON in `onDataChanged`
  for "/nodes"…"/radio_status" **and again** in `onMessageReceived` (`:120-145`), while the sender does
  both (`core/service/.../WearableSyncService.kt:452-481`); the sender writes a timestamp
  (`:504` `dataMap.putLong(KEY_TIMESTAMP, nowMillis)`) that the watch never reads; no size limit or version key exists on either side.
- **Why** Every sync is parsed and applied twice on a watch (redundant CPU/RAM) with no dedupe key, and
  there is no guard on the Message API budget: a large node DB makes `sendMessage` fail, `sendPayload`
  returns false, and the phone reports a whole-sync failure with no diagnostic.
- **Smallest change** Pick one transport per payload (or dedupe on the timestamp the sender already
  writes), and add a payload version plus a size check/chunking so an oversized sync has its own state.

### B8 — The wear APK is packaged under the phone's `applicationId`, including the phone's debug override
- **Severity** low · **Confidence** certain
- **Evidence** `wear/build.gradle.kts:58` `applicationId = configProperties.getProperty("APPLICATION_ID")` (`config.properties`: `APPLICATION_ID=com.geeksville.mesh`) and `:84-88` identical to `androidApp/build.gradle.kts:180-181` (`"com.geeksville.mesh.$flavor.debug"`).
- **Why** The wear module (namespace `org.meshtastic.wear`) produces the phone's id exactly. Phone and
  watch debug APKs cannot coexist on one device (install replaces; authorities such as
  `${applicationId}.androidx-startup` collide), the two share one Data Layer identity, and the fork's
  own "change APPLICATION_ID for your identity" step would change both at once.
- **Smallest change** Derive a wear-specific id, e.g. `"$APPLICATION_ID.wear"` with its own debug suffix.

### B9 — `CoreCommonModule` is loaded twice (startup app list and mode module `includes`)
- **Severity** low · **Confidence** likely
- **Evidence** `wear/.../di/WearKoinApp.kt:29-33` lists `CoreCommonModule::class` while
  `WearStandaloneRadioModule.kt:61-63` also includes it; `WearApplication.loadModeGraph` (`:115-119`)
  applies the mode module with `loadKoinModules` after `startKoin`.
- **Why** `loadKoinModules` re-defines every `CoreCommonModule` singleton after the graph is live and
  after `WearApplication` already resolved `CoroutineDispatchers` (`:61`) — exactly how Koin ends up
  with a replaced definition next to an existing instance. It also hides a genuinely missing binding.
- **Smallest change** Keep each core module in exactly one place, and assert it with a Koin
  `verify()`/`checks()` for the standalone entry too.

### B10 — `BLUETOOTH_SCAN` without `neverForLocation`, unlike the phone
- **Severity** medium · **Confidence** certain
- **Evidence** `wear/src/main/AndroidManifest.xml:35` `<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />` (no flags; plus `ACCESS_FINE_LOCATION` at `:29`) versus `androidApp/src/main/AndroidManifest.xml:40-42` (`android:usesPermissionFlags="neverForLocation"`).
- **Why** On API 31+ a scan declaration without `neverForLocation` is treated as location-derived, so
  scanning additionally needs fine location granted and location services enabled
  (`LocationServicesDisabled`, `StandaloneRadioViewModel.kt:225`). The wear app declares
  `ACCESS_FINE_LOCATION` but never requests it at runtime on those API levels
  (`core/ui/src/androidMain/.../PlatformUtils.kt:430-434` asks only for `BLUETOOTH_SCAN`+`CONNECT`),
  so the standalone device list can stay empty with no prompt explaining why.
- **Smallest change** Add `android:usesPermissionFlags="neverForLocation"` (BLE-only scanning, as the
  phone does) and re-check whether `ACCESS_FINE_LOCATION` is still needed for the pre-S path.

### B11 — `MeshService` claims a location foreground type the wear manifest does not declare
- **Severity** low · **Confidence** likely
- **Evidence** `wear/src/main/AndroidManifest.xml:80` `android:foregroundServiceType="connectedDevice"` with no `FOREGROUND_SERVICE_LOCATION` (phone declares it, `androidApp/.../AndroidManifest.xml:71`), while `ForegroundStartPolicy.kt:74-88` returns `CONNECTED_DEVICE` **or** `LOCATION` when fine location is granted and the app is foregrounded — the wear app's normal state.
- **Why** Each foreground start requests a type the manifest does not allow. `ServiceForegroundPromotion.kt:62-74`
  swallows the refusal and retries with `connectedDevice`, so it is not fatal, but every start pays an
  exception round-trip and logs an error, and the location type the shared policy intends can never be held.
- **Smallest change** Declare `FOREGROUND_SERVICE_LOCATION`, or pass only `connectedDevice` on wear.

### B12 — Legacy `package=` attribute left in the wear manifest
- **Severity** low · **Confidence** unsure
- **Evidence** `wear/src/main/AndroidManifest.xml:21` `package="org.meshtastic.wear">` — the only manifest in the repo still carrying it; the namespace is set in `wear/build.gradle.kts:40`. AGP 9.4.1 (`gradle/libs.versions.toml:5`) removed support for the attribute.
- **Why** At best a warning, at worst a manifest-merger failure for the one module this fork ships. Which of the two could not be determined without a build.
- **Smallest change** Delete the attribute; rely on the module namespace.

---

## 4. Area C — Phone-side wearable sync

### C1 — Three new `CoroutineScope(...)` sites fail the konsist architecture gate
- **Severity** high · **Confidence** certain
- **Evidence** `core/service/src/androidMain/.../WearableSyncService.kt:106` `private val scope = CoroutineScope(serviceJob + dispatchers.io + exceptionHandler)`;
  `core/service/src/androidMain/.../PhoneWearableListenerService.kt:46` `private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io + exceptionHandler) }`;
  `core/prefs/src/commonMain/.../watch/WatchPrefsImpl.kt:43` `private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)` — none of these keys is in `core/konsist/.../CoroutineScopeConstructionTest.kt`'s allowlist (`:33-90`).
- **Why** `:core:konsist:allTests` is a build gate (`reusable-check.yml:216`) and the rule fails on any
  production `CoroutineScope` construction that is not allowlisted. Replaying the rule's own regex,
  comment-skip and allowlist logic over this tree reported exactly 5 unallowlisted sites (3 here, 2 in
  `wear/`), so the gate is red on this branch. None of the three scopes has an owner that cancels it.
- **Smallest change** Inject `ApplicationCoroutineScope` (core/common) or a `ServiceScope` and delete
  the local scopes — or, if out of scope for this change, add the exact three keys to the allowlist.
  Nothing else makes the gate pass.

### C2 — No timeout on any phone-side Play Services `await()`
- **Severity** high · **Confidence** likely
- **Evidence** `WearableSyncService.kt:468` `safeCatching { dataClient.putDataItem(dataRequest).await() }`;
  `:476` `messageClient.sendMessage(it, path, bytes).await()`; `:486` `messageClient.sendMessage(nodeId, path, null).await()`;
  `:528` `getCapability(...).await()`; `:128` `capabilityClient.addListener(...).await()` — the file contains no `withTimeout`/`withTimeoutOrNull` at all.
- **Why** Data Layer `Task`s are known to never complete (rather than fail) when the Play Services
  connection drops. With no timeout, `syncMutex.withLock { synchronizeConnectedWatch(...) }`
  (`:150/:156/:201`) never releases: the state stays `Checking`/`Syncing` for the process's life,
  `WatchViewModel` keeps `canSync = false` (`WatchViewModel.kt:124`) so the user cannot retry, and every
  later sync queues behind the held lock. The watch side of this same feature does bound its awaits
  (`WearDataLayerClient.kt:111,116,125,150,156`), as does the rest of core/service.
- **Smallest change** Wrap each `await()` in `withTimeoutOrNull(...)` (≈5 s for capability/status sends,
  10–30 s for payloads) and route a timeout into the existing failure path so the mutex is always
  released and the state machine always leaves Checking/Syncing.

### C3 — A watch-initiated sync runs with zero categories enabled, clears the watch, and reports success
- **Severity** medium · **Confidence** likely
- **Evidence** `WearableSyncService.kt:202` `synchronizeConnectedWatch(targetNodeId = targetNodeId, reportDisconnectedAsError = false)` — no `hasEnabledCategory` check anywhere in `forceSync`/`performSynchronization`, while the phone UI path guards it (`WatchViewModel.kt:71` `if (!watchPrefs.syncPreferences.value.hasEnabledCategory) return`); the wear path reaches `forceSync` via `PhoneWearableListenerService.kt:50` `requestSync = syncService::forceSync`.
- **Why** With all five toggles off, `syncPlan.enabledCategories` is empty, so no payload stage runs —
  but `clearDisabledCategories` (`:272` → `:429`) deletes all five categories from the Data Layer and
  tells the watch to clear them, then the phone sends SYNC_COMPLETED and marks success (`:324-329`),
  setting the "last successful sync" timestamp the settings screen shows. The one place the rule exists
  is the one the watch bypasses.
- **Smallest change** Enforce it in the service (`performSynchronization` returns early and answers the
  requesting node with a failure status) and drop the duplicate check in `WatchViewModel`.

### C4 — A watch-requested sync can finish without a terminal status, leaving the watch in Loading/Retrying
- **Severity** medium · **Confidence** likely
- **Evidence** `WearableSyncService.kt:202` → `:246` `findReachableWatchNodes(targetNodeId, retryWhileSearching = reportDisconnectedAsError)` → `:252-259` `if (targetNodes.isEmpty()) { ... watchSyncStateHolder.disconnected() }` with no `sendSyncStatus`; the watch waits for a terminal state (`WearDataLayerClient.kt:125`, 30 s).
- **Why** `/request_sync` is the one command where the watch is blocked on the phone's answer, but
  `forceSync` deliberately passes `reportDisconnectedAsError = false`, so the no-reachable-target path
  (and the connectivity-off path at `:238-241`) returns silently. The watch sits in `Loading`/`Retrying`
  until its own timeout and reports a timeout instead of the phone's real reason.
- **Smallest change** For a request that carried a `sourceNodeId`, always terminate on that node on
  every early-return path.

### C5 — The connected radio's GPS position is sent to the watch under a toggle whose copy never mentions location
- **Severity** medium (see also `P1`, same defect from the privacy side) · **Confidence** certain
- **Evidence** `WearableSyncService.kt:422-423` `latitude = position?.latitude_i?.times(POSITION_SCALE),` / `longitude = ...` inside `createWearableRadioStatus()`, sent whenever `preferences.syncRadioStatus` (`:313-322`); the toggle's copy is `core/resources/.../strings.xml:2100` "Connection, battery, and supported telemetry information."
- **Why** Turning on "Radio status and telemetry" silently ships the phone's/radio's last known
  position to a second device, and the label gives the user no way to know. It is also dead weight —
  see `C6`.
- **Smallest change** Drop the two fields from the payload (and from `createWearableRadioStatus`); if a
  watch map is planned, gate them on an explicit location consent and say "location" in the description.

### C6 — The watch stores the radio status and never reads it
- **Severity** medium · **Confidence** likely
- **Evidence** `wear/.../presentation/model/WearableSyncCategoryRepositories.kt:37-43` defines
  `WearableRadioStatusRepository` and `wear/.../DataLayerListenerService.kt:210-217` writes it, but a
  repo-wide grep finds no reader of `radioStatusRepository`/`.status` outside DI registration.
- **Why** Latitude/longitude cross the Data Layer, are deserialized and retained on the watch, and no
  screen consumes them: the privacy cost of `C5`/`P1` buys nothing today, and nothing pins why the
  field exists.
- **Smallest change** Wire radio status into a watch screen (then treat it as a real, disclosed
  feature), or drop the location fields / the category; if kept, add a wear test asserting its use.

### C7 — Turning watch connectivity off neither clears the watch nor tells it
- **Severity** medium · **Confidence** likely
- **Evidence** `WearableSyncService.kt:164` `if (connectivityEnabled) repositoryInvalidations.trySend(Unit)` (the collector ignores the false transition) and `:238-241` `if (!watchPrefs.watchConnectivityEnabled.value) { watchSyncStateHolder.disconnected(); return }`; the only clearing path is `clearDisabledCategories` (`:429`), reachable only from inside a successful sync; the category copy promises clearing (`strings.xml:2090` "Turning a category off clears that category on compatible watches and stops future updates.").
- **Why** The master switch is the user's emergency stop, but flipping it stops future pushes while the
  watch keeps the phone's nodes, recent message text, channels and quick replies and keeps presenting
  them as live. The repositories are only emptied by `/clear_sync_category`, which is never sent here.
- **Smallest change** React to the false transition: clear the categories on the reachable watch, push
  a terminal status, then settle the phone state to Disconnected.

### C8 — `PhoneWearableListenerService` cancels an in-flight watch reply in `onDestroy`
- **Severity** medium · **Confidence** likely
- **Evidence** `PhoneWearableListenerService.kt:74` `scope.launch { ... handler.handle(action) }`, `:46` the lazy `CoroutineScope(SupervisorJob() + ...)`, `:132-134` `override fun onDestroy() { scope.cancel(); super.onDestroy() }`.
- **Why** A `WearableListenerService` is bound by Play Services and can be destroyed as soon as the
  callback returns, so the work that actually sends the user's message runs in a scope owned by that
  lifetime. Cancellation mid-send drops the message with no error on either device (the watch already
  showed it QUEUED), and it is the one failure mode not logged here. Comparable code avoids it:
  `ConversationActionService.kt:71-79` keeps itself started until the work finishes, and
  `BootCompleteReceiver.kt:68` holds the process with `goAsync()`.
- **Smallest change** Hand the send to work that outlives the listener (app-scoped/foreground service
  queue), or keep the service alive until the send completes.

### C9 — Two watch-prefs Koin modules are dead code and would be a duplicate-definition error if wired in
- **Severity** low · **Confidence** certain
- **Evidence** `core/prefs/.../di/WatchPrefsModule.kt:25` and `WatchPrefsAndroidModule.kt:28-29` — neither symbol appears in any `@KoinApplication`/`@Module(includes = [...])`; the phone loads `CorePrefsModule`/`CorePrefsAndroidModule` (`AppKoinModule.kt:78-79`) and so does the watch (`WearKoinApp.kt:33-34`).
- **Why** `WatchPrefsModule` re-scans `org.meshtastic.core.prefs.watch`, which `CorePrefsModule`
  (`@ComponentScan("org.meshtastic.core.prefs")`) already covers for the same `WatchPrefsImpl` single,
  and `WatchPrefsAndroidModule` re-provides the same `WatchDataStore` type
  (`CorePrefsAndroidModule.kt:122-124`). Adding either alongside the core modules is a Koin
  duplicate/override error at startup, and the file's stated intent ("only the watch preference store
  instead of every domain") is not realized anywhere.
- **Smallest change** Delete both files, or swap the wear app's prefs modules for them deliberately
  (re-verifying with the wear `KoinVerificationTest`).

### C10 — Manual "Sync now" flips the state to Checking optimistically, with no timeout and no acknowledgement
- **Severity** low · **Confidence** likely
- **Evidence** `WatchViewModel.kt:72` `if (!watchSyncStateHolder.beginManualSyncRequest()) return` then
  `watchPrefs.requestSync()` (`:75`), which only bumps a datastore counter
  (`WatchPrefsImpl.kt:122-128`) that the service observes at `WearableSyncService.kt:168`.
- **Why** The only writer that can leave `CheckingConnection` is the service, and nothing times the
  state out (`WatchSyncStateHolder.kt:78-95` sets Checking and returns). If the service was never
  started (`MeshUtilApplication.kt:136-146` is the sole trigger, skipped when `isSupportedDevice` is
  false, `:113-117`) the settings screen stays on "Checking connection" forever and Sync Now is refused
  permanently.
- **Smallest change** Let the service own the transition, or add a timeout that falls back to
  Connected/Disconnected if no check or sync starts within a few seconds.

### C11 — The Data Layer path set has three sources of truth on the phone alone
- **Severity** low · **Confidence** certain
- **Evidence** `WearableSyncService.kt:493` `private const val PATH_NODES = "/nodes"` (…`:497`, used for
  writes) versus `:544` `WatchSyncCategory.Nodes -> "/nodes"` (the private `dataPath` extension used for
  clears) versus the shared `WatchSyncProtocol.kt:23`; the same literals appear again in the wear
  module's `when` branches and manifest.
- **Why** Writes and clears derive the same path from two independent literals, and neither is the
  shared constant that exists for this purpose. Changing one and not the other silently makes
  "category off" delete a path that was never written.
- **Smallest change** Extend `WatchSyncPaths` with the five payload paths and `/clear_sync_category`
  (keeping `WatchSyncCategory.dataPath` derived) and reference them everywhere.

### C12 — Manual-sync request counter is observed without a bound
- **Severity** low · **Confidence** likely
- **Evidence** `WatchPrefsImpl.kt:122-128` writes `KEY_SYNC_REQUEST`; `WearableSyncService.kt:168`
  `scope.launch { watchPrefs.syncRequest.drop(1).collect { manualSyncRequests.trySend(Unit) } }`.
- **Why** A request is a counter bump, not an event: two requests collapsing into one observed value
  (or a request made while the service is dead) is indistinguishable from one, so the UI's
  "Checking connection" and the service's work can disagree about how many syncs were asked for.
- **Smallest change** Make the request an observable event the service consumes exactly once (or carry
  a request id the service acknowledges).

### C13 — Protocol carries no version and no unknown-enum tolerance
- **Severity** low · **Confidence** likely
- **Evidence** `core/model/.../WearableMessage.kt:32` `val status: MessageStatus = MessageStatus.RECEIVED,`
  (enum serialized by name) and `WatchSyncProtocol.kt:30` `enum class WatchSyncCategory`; no version
  field on any payload. See also `D10`.
- **Why** The watch decodes whole lists and treats any decode failure as a sync failure
  (`DataLayerListenerService.kt:183-186`). The two APKs update independently, so adding a
  `MessageStatus` entry (or renaming a category) on the phone makes an older watch fail the entire
  category instead of ignoring the value it does not know.
- **Smallest change** Add a `protocolVersion` and serialize `status`/`category` as a string with a
  fallback, so the watch rejects only what it cannot understand.

### C14 — Each category is one uninterruptible DataItem+Message, with no size guard or chunking
- **Severity** low · **Confidence** unsure
- **Evidence** `WearableSyncService.kt:476` `safeCatching { messageClient.sendMessage(it, path, bytes).await() }`
  (and `:468` for the DataItem) send the whole JSON list per category, with the largest list size
  selectable by the user (`WatchPrefs.kt:26` `ONE_HUNDRED(100),`).
- **Why** The Data Layer gives each item a fixed budget; a large node DB or 100 messages of text can
  exceed it. There is no size check and no chunking, and the failure is all-or-nothing:
  `allPayloadsSent` false routes to `syncFailed(SynchronizationFailed)` (`:334-336`), so one oversized
  category fails every sync with no partial success. The exact platform limit was not verified here.
- **Smallest change** Measure the encoded payload and chunk it (with the watch applying each part), or
  fail only that category with its own error.

### C15 — An empty capability update resets an in-flight sync, and a later success can re-mark it Connected with no watch
- **Severity** low · **Confidence** certain
- **Evidence** `WatchSyncStateHolder.kt:106-112` `if (watchNames.isEmpty()) { current.copy(connectionState = WatchConnectionState.Disconnected, ...) }` called from the capability listener (`WearableSyncService.kt:340-346`), while `syncSucceeded` (`:152-162`) sets `Connected` unconditionally.
- **Why** The empty branch does not check for `Checking`/`Syncing` (unlike the non-empty branch at
  `:116-120`), so a capability flap mid-sync drops progress and shows Disconnected; the sync coroutine
  then continues and, on success, flips the UI back to Connected while `reachableWatchCount` is 0 — the
  settings screen renders `isReachable = false` next to "Connected" (`WatchViewModel.kt:117-120`).
- **Smallest change** Leave an active state alone when the list is empty, and only mark Connected when
  the last known reachable count is non-zero.

### C16 — The router validates the node address but trusts the watch's channel index
- **Severity** low · **Confidence** likely
- **Evidence** `WearableIncomingActionRouter.kt:61` `?: ContactKey.broadcast(reply.channelIndex).value`
  (and `:55`); the channel is read back as one character (`NodeAddress.kt:108`), and
  `isSupportedContactKey` (`:86-98`) checks only the address kind.
- **Why** `reply.channelIndex` comes off the untrusted Wear Message API (the service is
  `android:exported="true"`, `AndroidManifest.xml:317-328`) and is never range-checked. Because
  `ContactKey` encodes the channel as its first character, `channelIndex = 42` yields key `"42^all"`
  and the send reads channel 4 — a silent misroute — while out-of-range values like 9 are accepted.
- **Smallest change** Reject the reply unless `channelIndex` is within the configured channel range
  (0..7, or 0..8 with PKC), returning `WearableIncomingAction.Malformed` otherwise.

---

## 5. Area D — Watch settings, tests, privacy

### D1 (P1) — Precise location is sent to the watch under the generic telemetry toggle
- **Severity** high · **Confidence** certain
- **Evidence** `core/model/src/commonMain/.../WatchSyncProtocol.kt:46-47` `val latitude: Double? = null,` /
  `val longitude: Double? = null,`; populated at `WearableSyncService.kt:422-423` from
  `nodeRepository.ourNodeInfo`; the only gate is `WatchPrefsImpl.kt:153`
  (`booleanPreferencesKey("watch_sync_radio_status")`), **defaulted on** at `WatchPrefs.kt:41`
  `val syncRadioStatus: Boolean = true`; the copy is `strings.xml:2100-2101` "Radio status and telemetry" /
  "Connection, battery, and supported telemetry information." — no mention of location, and none of the
  54 `watch_*` strings concerns location.
- **Why** On a default install the user's own precise coordinates leave the phone for the watch as part
  of a category whose copy mentions only connection/battery/telemetry, and the phone's own
  location-sharing control (`shouldProvideNodeLocation`, `feature/settings/.../SettingsViewModel.kt:153`)
  is never consulted, so a user who deliberately turned location sharing off can still have a position
  synced if the node holds one. `AGENTS.md` states privacy first: never expose location.
- **Smallest change** One of: (a) stop populating the two fields (smallest — and nothing reads them, `C6`),
  (b) add a separate "share my location with the watch" preference and gate on it, or (c) disclose
  location in the description **and** gate on the existing location-sharing preference. Not fixable
  blind: this cannot be tested on the audit host.

### D2 — The Radio Status category is retained on the watch for no user-facing feature
- See `C6` (same defect, found from both sides).

### D3 — Turning "Watch connectivity" off does not clear the watch, contradicting the app's own copy
- See `C7` (same defect, found from both sides).

### D4 — The phone-side sync engine (`WearableSyncService`) has no tests at all
- **Severity** medium · **Confidence** certain
- **Evidence** No test in the tree references `WearableSyncService` (grep returns only sources:
  the service, `PhoneWearableListenerService`, and the state-holder DI). All watch-adjacent tests cover
  prefs/plan/ViewModel/router/holders instead.
- **Why** `performSynchronization`, `clearDisabledCategories`, `sendPayload`, `findReachableWatchNodes`
  (retry/back-off), `createWearableRadioStatus` (including the location mapping) and the
  `allPayloadsSent` accounting (`:274-337`) are the highest-risk code here and are entirely unverified.
  The three failing CI shards cannot tell you which of these behaviours regressed, because none is pinned.
- **Smallest change** Extract the pure parts (`buildSyncPayloads(prefs, repos)` → path→json pairs, plus
  the stage list) into `commonTest` and assert the zero-category, radio-status-included and
  partial-send cases.

### D5 — `WatchSyncStateHolder`, the state machine behind the settings screen, has no dedicated test
- **Severity** medium · **Confidence** certain
- **Evidence** `core/repository/src/commonTest` has only `WatchSyncPlanTest` for this feature;
  `WatchSyncStateHolder` is exercised only indirectly (settings `WatchViewModelTest`/`WatchConfigurationScreenTest`).
  Its logic: `WatchSyncStateHolder.kt:78-95` (the `beginManualSyncRequest` CAS guard) and `:179-194`
  (`syncFailed`/reachability clearing).
- **Why** The branch logic that decides what the settings screen shows is untested directly, so an edit
  can silently change the UI contract with no red test.
- **Smallest change** Add `WatchSyncStateHolderTest` in `core/repository` commonTest: `beginManualSyncRequest`
  returning false while Checking/Syncing, and each `WatchConnectionError`'s effect on `reachableWatchCount`.

### D6 — The zero-enabled-category guard lives only in the ViewModel
- See `C3` (same defect, found from both sides).

### D7 — Scan permission gap that can make the standalone device list silently empty on Wear OS 4+
- See `B10` (same defect, found from both sides; `PlatformUtils.kt:420-434` requests only
  `BLUETOOTH_SCAN`+`BLUETOOTH_CONNECT` on API ≥ 31).

### D8 — Negative UI assertions key off hardcoded English literals
- **Severity** low · **Confidence** certain
- **Evidence** `wear/src/test/.../NodeDetailsScreenTest.kt:228-229` `onAllNodesWithText("Battery").assertCountEquals(0)` /
  `onAllNodesWithText("SNR").assertCountEquals(0)`, while the real labels come from resources
  (`wear/src/main/.../NodeDetailsScreen.kt:143-149` `stringResource(Res.string.snr)`); likewise
  `SettingsScreenTest.kt:60` `onAllNodesWithText("Experimental features").assertCountEquals(0)`.
- **Why** `assertCountEquals(0)` on a literal cannot distinguish "the row is correctly hidden" from "the
  label text no longer matches": reword the resource and the row could render while the test still passes.
- **Smallest change** Assert absence with `getString(Res.string.battery)`/the relevant resource, or use test tags.

### D9 — `WatchPrefsImpl` owns a `SupervisorJob` scope nothing cancels
- **Severity** low · **Confidence** likely
- **Evidence** `WatchPrefsImpl.kt:43` `private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)`
  with eight `stateIn(scope, SharingStarted.Eagerly, ...)` collectors and no `close()`;
  `WatchPrefsImplTest.kt:154-161` constructs a second instance and never cancels it (tearDown cancels only the test scope).
- **Why** Each instance keeps eagerly-started collectors alive for its lifetime; in tests the second
  instance leaks a scope per run, and the `WatchPrefs` interface offers no lifecycle hook for a future
  property-scoped replacement. (This is one of the sites failing the konsist gate — `C1`.)
- **Smallest change** Inject a `CoroutineScope` (or add `close()`/`AutoCloseable`) and use it in tests.

### D10 — The Data Layer protocol has no version marker; both endpoints rely on `ignoreUnknownKeys` and defaults
- **Severity** low · **Confidence** likely
- **Evidence** `WatchSyncProtocol.kt:22-26` defines three path constants and no protocol version; both
  sides parse with `Json { ignoreUnknownKeys = true }` (`WearableSyncService.kt:110`,
  `wear/.../DataLayerListenerService.kt:65`). See also `C13`.
- **Why** After a one-sided update a renamed field or path silently becomes a default or is dropped,
  and neither side can detect the mismatch — the sync still reports success.
- **Smallest change** Include a protocol/version integer in the sync-start payload and fail loudly on
  mismatch; add a test that an unknown version is rejected.

### D11 — The radio-status stage never increments `completedStages`, so the progress count is off by one
- **Severity** low · **Confidence** certain
- **Evidence** `WearableSyncService.kt:313-322` calls `watchSyncStateHolder.syncing(WatchSyncStage.RadioStatus, completedStages, totalStages)`
  but, unlike the four preceding categories, never does `completedStages++`;
  `WatchConnectionSection.kt:280-288` renders "%1$s — %2$d of %3$d steps complete" and the fraction bar.
- **Why** The last stage reports e.g. "4 of 5 steps" and the bar never reaches 100% for the final
  category — a small but user-visible inconsistency in the settings connection card. No test asserts on
  stage progress.
- **Smallest change** Increment after the radio-status send (or report completion explicitly), and
  assert the final stage count in the `D4` test.

### D12 — Three hand-written `WatchPrefs` test doubles can drift from the interface's real semantics
- **Severity** low · **Confidence** certain
- **Evidence** `core/testing/src/commonMain/.../FakeWatchPrefs.kt:24`,
  `feature/settings/src/commonTest/.../WatchViewModelTest.kt:201` (`RecordingWatchPrefs`) and
  `wear/src/test/.../SettingsViewModelTest.kt:103` (`RecordingWatchPrefs`) each implement `WatchPrefs`
  independently, and the two recording fakes differ from `FakeWatchPrefs`.
- **Why** Behaviour asserted through these fakes is defined by the fake, not by `WatchPrefsImpl`, so a
  change to the real update semantics would not be caught; the three copies can diverge in what they model.
- **Smallest change** Build the settings/wear tests on the shared `core/testing` fake (or promote one
  recording fake into `core/testing`).

---

## 6. Area E — CI findings (run 38512870483)

### E1 — The watch tests do not compile, which is what fails two of the three shards
- **Severity** high · **Confidence** certain
- **Evidence** `feature/settings/src/commonTest/.../WatchConfigurationScreenTest.kt:20` →
  `Unresolved reference 'assertDoesNotExist'`, failing `:feature:settings:compileTestKotlinJvm` and
  `:feature:settings:compileTestKotlinIosSimulatorArm64`.
- **Smallest change** Use an assertion available in the project's test stack (check what the other
  Compose tests import) instead of a Compose-UI-test-only matcher in a `commonTest` source set.

### E2 — The unsupported-device guard no longer covers every entry point
- **Severity** high · **Confidence** certain for the failure; likely for the runtime consequence
- **Evidence** `androidApp/src/main/AndroidManifest.xml:318` declares
  `org.meshtastic.core.service.PhoneWearableListenerService` (`exported="true"`), while
  `androidApp/src/test/.../BundledSqliteGuardTest.kt:56-67` asserts the entry-point set is **exactly**
  a fixed list and fails at `:67`.
- **Why** That guard exists so an unsupported device starts **no** entry point; an entry point outside
  the set is one the guard will not disable.
- **Smallest change** Add the wearable service to the expected set (one line) and confirm
  `appEntryPoints()` still covers it.

### E3 — Startup tests reach Koin on a device that should not get there
- **Severity** high · **Confidence** certain for the failure; cause unverified
- **Evidence** `UnsupportedDeviceStartupTest > MainActivity opens on an unsupported device without reaching Koin`
  and `> a device that cannot load SQLite starts no Koin and stops every entry point` both fail with
  `java.lang.IllegalStateException at GlobalContext.kt:36`.
- **Smallest change** Determine which new call resolves Koin before the guard runs — the wear wiring in
  `MeshUtilApplication` is the natural suspect — and make that path lazy or guarded.

### E4 — Spotless is red in three modules
- **Severity** medium · **Confidence** certain
- **Evidence** `:androidApp:spotlessKotlinCheck`, `:feature:settings:spotlessKotlinCheck`,
  `:wear:spotlessKotlinCheck` FAILED (format violations).
- **Smallest change** `spotlessApply` before the push — the repo's own gate expects it.

### E5 — Detekt findings on the watch/wear code (~20, deduped)
- **Severity** medium · **Confidence** certain
- **Swallowed `CancellationException`** (the class with real consequences — it keeps a cancelled
  coroutine alive): `core/service/.../WearableSyncService.kt:224`, `wear/.../data/WearDataLayerClient.kt:135`,
  `wear/.../presentation/ChatViewModel.kt:66,79`, `wear/.../presentation/NodeDetailsViewModel.kt:36`,
  `wear/.../standalone/StandaloneRadioViewModel.kt:122,176`.
- **Other:** `WearableSyncService.kt:53` unused import, `:90` constructor has 9 parameters (max 7),
  `:190` spread operator copies the array; `ChatViewModel.kt:26` unused import;
  `DataLayerListenerService.kt:29` and `WearApplication.kt:29` unused import `kotlinx.coroutines.cancel`;
  `DesignTokens.kt:42,44,46,48,50` should be `@ReadOnlyComposable`;
  `MainActivity.kt:152` `?: ""` → `orEmpty()`; `StandaloneRadioScreen.kt:85` `LaunchedEffect` whose body
  only calls non-suspending functions; `WatchConnectionSection.kt:240` MaxLineLength, `:293` FunctionSignature.
- **Still red from the earlier commit:** `MeshUtilApplication.kt:139` and `CorePrefsAndroidModule.kt:128`
  MaxLineLength (both files the forward-merge resolved by hand), `FakeWatchPrefs.kt:24`
  TooManyFunctions (13 > 11), `WatchConfigurationScreen.kt:157` LongMethod (72 > 70), `:258` ParameterNaming.

### E6 — Two detekt findings are in upstream files, not this fork's code
- **Severity** low · **Confidence** certain
- **Evidence** `core/data/src/commonMain/.../repository/PacketRepositoryImpl.kt:230` and
  `core/repository/src/commonMain/.../PacketRepository.kt:149` `[SuspendFunWithFlowReturnType]`.
- **Why** Recorded so the red lint run is not misattributed to the Wear work — the fix belongs upstream.

### E7 — `wear/` has no `google-services.json`
- **Severity** medium · **Confidence** certain
- **Evidence** `:wear:processGoogleDebugGoogleServices` fails ("File google-services.json is missing").
  Cause: `build-logic/.../AndroidApplicationConventionPlugin.kt:33` applies `meshtastic.analytics` to
  every application module, which applies the google-services plugin for the google flavor; upstream
  only ever had `androidApp/google-services.json` (a tracked placeholder, force-added past
  `.gitignore:39 **/google-services.json`).
- **Smallest change** The same placeholder at `wear/google-services.json` — its clients cover
  `com.geeksville.mesh` and `com.geeksville.mesh.google.debug`, which is what wear's debug variants use.

---

## 7. What this audit could not verify

- **Everything compile- or runtime-dependent.** The audit host is a Raspberry Pi with no JDK and no
  Android SDK: no build, no unit tests, no emulator, no watch. That is why `B1`, `B12`, `C14`, `A12`
  and parts of `E3` carry `likely`/`unsure` rather than a verdict.
- **The three failing shards' test output** beyond the compile error in `E1`; the shard logs are large
  and their failure is explained by the test-compile error, but the tests that never ran cannot be
  cleared by reading.
- **Device behaviour on real hardware**: BLE scan cost, foreground-service promotion, ambient mode and
  the Data Layer size limit are all platform behaviours that need a phone + watch pair.
- **Anything requiring the Wear OS app to be installed**, including all of Area A's accessibility
  findings (`A7`, `A8`) which are semantic-tree conclusions, not TalkBack sessions.

## 8. Provenance and how to re-derive

- Four read-only code reads over `fbd3242bf0` (watch UI/state; standalone radio + DI + Data Layer;
  phone-side sync; settings + tests + privacy), each tracing `path:line` evidence and labelling confidence.
- CI evidence: `gh run view 38512870483 --repo coltarch/Meshtastic-WearOS --job=<id> --log` for the four
  failing jobs; per-run logs also saved under `/opt/data/cache/scratch/ci38512870483/`.
- No file in this repository was modified to produce this audit, and no finding here was fixed.
