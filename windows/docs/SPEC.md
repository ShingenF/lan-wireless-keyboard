# Virtual Keyboard Receiver — Protocol and Product Specification

## Scope and build target

- Windows receiver implemented as a compact, visible WPF application.
- Target: .NET 10, `win-x64`, framework-dependent single-file publish.
- The application manifest requests administrator privileges so input can reach both normal and elevated applications. Do not use `uiAccess`, a service, a scheduled task, or any silent-elevation mechanism; the receiver cannot control the UAC secure desktop.
- During project development, all source, tests, documentation, logs, runtime data, tools, artifacts, and the executable remain inside this repository. A standalone published copy keeps its runtime data beside its executable.
- `runtime-data/` and `.tools/` are not tracked by Git.
- Published executable: `artifacts/windows-x64/VirtualKeyboardReceiver.exe`.

## Transport and session rules

- Protocol v1 uses TCP with TLS, listening on port 39421 by default.
- Only one authenticated client is accepted at a time; another connection receives `busy` and closes.
- At most four connections may be pending TLS/authentication. Excess pending connections are closed, and the permit is always released when that authentication attempt ends.
- TLS negotiation through receipt of the first authentication message has one 10-second deadline. The deadline does not apply after authentication succeeds.
- TCP_NODELAY is enabled.
- Messages are UTF-8 JSON Lines. A line may contain at most 65,536 bytes, excluding the line terminator.
- Unknown fields, unknown message types, wrong versions, malformed JSON, invalid UTF-8, and oversized frames are rejected.
- Commands received before authentication are rejected.
- Commands are never replayed after disconnect.
- Disconnect, shutdown, and exceptional termination of a session release every mouse button still held by that session.
- A mouse-button release failure cannot prevent the authenticated slot and listening status from being restored.

## First-run identity

On first launch, persist identity data in `runtime-data/`. If an ancestor of the executable directory contains the project marker `docs/SPEC.md`, use that project root's `runtime-data/` so project-local credentials remain stable. Otherwise, use `AppContext.BaseDirectory/runtime-data`; never infer an unrelated directory by walking a fixed number of parent levels.

- A self-signed TLS certificate.
- A random 16-character Base32 pairing key.

The certificate and pairing key are reused on later launches. The displayed key may contain grouping hyphens; cryptographic use removes hyphens.

## Authentication

Immediately after TLS establishment, the server sends:

```json
{"version":1,"type":"challenge","nonce":"<base64>","fingerprint":"<uppercase SHA256 hex of DER cert>"}
```

The client must verify that `fingerprint` equals the SHA-256 fingerprint of the certificate observed in the TLS session, then reply:

```json
{"version":1,"type":"auth","proof":"<uppercase HMAC-SHA256 hex>","device":"..."}
```

Proof calculation:

```text
key  = UTF-8(uppercase(pairing code with hyphens removed))
data = UTF-8(nonce + ":" + fingerprint)
proof = uppercase hexadecimal HMAC-SHA256(key, data)
```

On success the server replies `{"version":1,"type":"ready"}`. On failure it sends an error and disconnects.

## Authenticated commands

Every command contains `version: 1`, `type`, `seq` (JSON signed 64-bit integer), and `timestamp` (JSON signed 64-bit integer). Exact additional fields are:

- `textCommit`: `text` string; optional `preferPhysicalKeys` boolean, defaulting to `false`.
- `replaceTail`: `deleteCodePoints` integer from 0 through 1,024 inclusive, `text` string, and optional `preferPhysicalKeys` boolean, defaulting to `false`. Larger values are rejected before dispatch.
- `keyPress`: `key` is `up`, `down`, `left`, `right`, `backspace`, `enter`, or `escape`.
- `keyState`: `key` is `w`, `a`, `s`, or `d`; `action` is `down` or `up`.
- `systemShortcut`: `shortcut` is `shift`, `controlSpace`, `capsLock`, `windowsSpace`, `controlShift`, or `altShift`.
- `shortcutChord`: `modifiers` is a non-empty array of unique values drawn from `shift`, `control`, `alt`, and `meta`; `key` is one printable ASCII character or `space`, `enter`, or `backspace`.
- `pointerMove`: integer `dx`, `dy`.
- `pointerButton`: `button` is `left` or `right`; `action` is `down` or `up`.
- `wheel`: integer `delta`.
- `ping`: no additional fields.

Every successful command carrying a sequence number receives `{"version":1,"type":"ack","seq":<seq>}`. `ping` additionally receives `pong`.

## Input semantics

- For `textCommit`, each ordinary UTF-16 code unit is injected in its own Win32 `SendInput` down/up batch with `KEYEVENTF_UNICODE`, including both UTF-16 code units for supplementary Unicode characters. A CRLF pair is normalized to one independent `VK_RETURN` down/up batch, while an isolated CR or LF is each injected as one independent `VK_RETURN` batch, supporting asynchronously delivered multiline drafts. The session cancellation token is checked between code-unit batches, including normalized line breaks, so shutdown remains prompt. After every successful text batch, the injector waits 1 ms before sending the next batch; it does not pace non-text input paths. Real-device production-path evidence showed that both tight sends and scheduler yield could repeat the final code unit, while a 1 ms wait preserved every code unit in order.
- When `preferPhysicalKeys` is `true`, ASCII characters that the foreground thread's keyboard layout maps with `VkKeyScanExW` are sent as physical scan-code key down/up events using `MapVirtualKeyExW`, including required Shift/Ctrl/Alt modifiers in reverse release order. The complete sequence for each mapped character is assembled into one `INPUT[]` and sent with one `SendInput` call. If that call fails, the injector makes a best-effort compensating key-up call for the main key and modifiers; the original failure remains authoritative. Unmapped or non-ASCII characters use the existing Unicode path; newline handling is unchanged. When omitted or `false`, the existing Unicode behavior is preserved.
- `replaceTail` sends Backspace once per Unicode code point requested, checking session cancellation between deletions and before committing replacement text. Its replacement-text injection also observes cancellation between code-unit batches so shutdown remains prompt.
- Arrow keys, Backspace, Enter, and Escape map to their Win32 virtual keys; Escape uses `VK_ESCAPE`.
- `keyState` uses fixed hardware scan codes: W `0x11`, A `0x1E`, S `0x1F`, and D `0x20`. A `down` action sends one `KEYEVENTF_SCANCODE` input; an `up` action adds `KEYEVENTF_KEYUP` and does not use Unicode or virtual-key clicks. Repeated state transitions are idempotent. Disconnect, session exceptions, and shutdown release every held mouse button and game key best-effort, continuing after individual release failures and clearing local held state.
- `systemShortcut` sends one scan-code `INPUT[]` chord with reverse release order. The exact mappings are: `shift` = Left Shift (`0x2A`); `controlSpace` = Left Ctrl (`0x1D`) + Space (`0x39`); `capsLock` = Caps Lock (`0x3A`); `windowsSpace` = Left Win (`0x5B`, `KEYEVENTF_EXTENDEDKEY`) + Space (`0x39`); `controlShift` = Left Ctrl (`0x1D`) + Left Shift (`0x2A`); and `altShift` = Left Alt (`0x38`, without `KEYEVENTF_EXTENDEDKEY`) + Left Shift (`0x2A`). Each complete chord is exactly one `INPUT[]` and one `SendInput` call. If the batch fails, a best-effort reverse key-up compensation batch is sent and the original failure remains authoritative.
- `shortcutChord` maps the requested modifiers and printable US-ASCII or special key to one scan-code `INPUT[]` batch. Intrinsic Shift/Ctrl/Alt requirements from the active keyboard mapping are merged without duplicate modifier presses; Left Win uses `KEYEVENTF_EXTENDEDKEY`. The main key and modifiers are released in reverse order, and a failed batch triggers a best-effort compensating key-up batch while preserving the original failure.
- Relative pointer movement, wheel delta, and left/right mouse down/up map to corresponding `SendInput` mouse flags.
- Tests fake only the Win32 system boundary; production uses the real `Win32InputInjector`.

## UI and lifecycle

- Start listening when the application starts.
- Display current LAN IPv4 address(es), port 39421, grouped pairing code, certificate fingerprint, and connection status.
- Continue listening while minimized.
- Clicking the main window close button hides the window to a notification-area icon while listening and the active connection continue.
- Double-clicking the notification-area icon restores and activates the main window.
- The notification-area context menu contains at least `打开` (Open) and `退出` (Exit).
- Only explicit `退出` cancels listening, closes the active connection, releases held mouse buttons, disposes the notification-area icon, and terminates the process.
- Explicit exit uses best-effort cleanup: receiver stop, tray disposal, window close, and application shutdown are each attempted once even if an earlier step fails.
- No service, autostart, or hidden background self-start is permitted.
- The built manifest must contain `requireAdministrator` with `uiAccess="false"`; elevation must occur only through the standard Windows UAC flow.

## Required verification

Development follows test-driven vertical slices through public boundaries:

1. UTF-8 JSON Lines protocol/authentication input to authenticated `ReceiverCommand`.
2. `ReceiverCommand` to `IInputInjector`, faking only the Win32 boundary in tests.
3. Real `Win32InputInjector` to `SendInput`, verified end-to-end.

Automated tests cover successful and incorrect-code authentication; oversized, malformed, wrong-version and strict-schema rejection; every command parser; text, emoji, replace-tail, arrows, Backspace, Enter, Escape, game-key state, pointer movement/buttons/wheel mapping; mouse and game-key release on disconnect; and no listener after shutdown. Final gates are `dotnet test`, `dotnet publish`, and standards/spec review.
