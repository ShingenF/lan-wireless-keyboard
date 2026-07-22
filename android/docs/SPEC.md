# Virtual Keyboard v1 specification

## Product behaviour

- Connect an Android phone to a Windows receiver by LAN IP address and port.
- Do not compile a development-machine IP into the APK. The host starts empty and
  the user enters the current LAN IPv4 address in settings.
- Present one screen with one native input field, one settings button, two compact
  side-by-side direction modules, and a relative touchpad. The field remains one
  visible line high in both input modes.
- Preserve that single-screen hierarchy. New installations default to a neutral
  light palette with a `#F2F2F2` background, `#B4B4B4` icons, white inputs and
  touchpad, charcoal primary text, and `#8E8E93` secondary text. Also provide a
  dark palette. Follow Android's system dark-mode state by default; settings can
  disable system following and force either the light or dark palette. Do not use
  shadows, border strokes, or gradients.
- Use the official MIT-licensed Hugeicons Free Icons 4.2.2 Stroke Rounded
  geometry for the settings, send, globe, launcher, and stemless direction icons. Arrange
  each module's four chevrons around a small circular joystick. Keep the chevrons
  visually unframed at rest and show a light-grey rounded rectangle while pressed.
  The original module continues to send repeating Windows arrow-key presses and its
  central joystick follows the dominant axis. The adjacent game module labels its four
  buttons W/A/S/D in the configured icon colour and maps them directly to held W/A/S/D
  states; its central joystick supports eight-way
  character movement, including two simultaneously held keys on diagonals. Releasing
  a button or returning the joystick to its dead zone releases only the corresponding
  movement key. Keep both modules compact, with small internal and external spacing.
- Keep the settings button transparent and use a bright saturated green for the
  connected indicator. All tappable buttons and recognised touchpad clicks provide
  Android haptic feedback.
- Replace the individual colour inputs with one editable, copyable configuration
  framework containing `[light]` and `[dark]` sections. Each section exposes page
  background, icon, primary text, secondary text, input background, and touchpad
  background. Show an AI prompt that explains the fields and provide one action
  that copies the prompt together with the current framework for use in ChatGPT,
  Claude, or similar tools. Accept six hexadecimal digits with an optional `#`,
  reject invalid, missing, duplicate, unknown, or alpha values, normalise saved
  values to uppercase `#RRGGBB`, apply them immediately after Save, and persist
  them across app restarts. Keep connection-state indicator colours fixed.
- Put `ESC` at the touchpad's top-left and a dedicated right-click button at its
  top-right without increasing the page height. Put the gesture hint below those
  buttons. A one-finger drag moves the
  pointer and a two-finger slide scrolls until the final finger lifts. A completed
  single tap sends left click immediately; two completed taps therefore remain two
  left clicks and are interpreted by Windows as a standard double-click. Tapping
  again, holding, and moving holds the left button for a drag until release. A
  two-finger tap and the dedicated button send right click.
  Tap-sized motion is never sent to Windows, so a recognised click occurs at the
  pointer position from before that touch.
- Overlay a narrow independent scroll strip along the touchpad's right edge below
  the right-click button. Draw only a thin vertical line with stemless arrows at
  both ends. Sliding in this region sends wheel deltas immediately after touch slop,
  without a preceding tap. Show it by default and persist a settings checkbox that
  can hide it.
- Provide separate pointer-speed and touchpad-acceleration sliders in settings.
  Apply acceleration only to single-finger pointer movement, with slow movement
  staying precise and fast movement gaining progressively more distance. The
  acceleration value is the maximum high-speed gain from `1.0×` (neutral) to
  `3.0×`; existing installations therefore keep their prior pointer feel until
  the user raises it. Persist both values across app restarts.
- Never inspect or infer the Android IME subtype and do not provide an in-app
  Chinese/English switch.
- Place a two-option `同步模式` / `异步模式` selector at the exact horizontal centre
  of the top row, between the connection status and settings button. Show the
  selected option with primary bold text and the other with regular secondary text.
  Synchronous mode sends IME commands immediately and marks text/tail commands to
  prefer physical keyboard injection for mappable ASCII. Non-ASCII or unmappable
  characters retain Unicode injection. Deferred mode keeps composing and committed
  content local until the user presses the send icon and does not request physical keys.
- In deferred mode, show the send icon at the input's bottom-right in a separate
  horizontal cell so text never flows beneath it. Keep the field one visible line
  high and scroll its multiline draft inside that viewport.
  Enter inserts a local line break while a draft exists and never submits that draft.
  When the deferred field is empty, Backspace, Enter, and Space are sent directly to
  the computer. Explicit send emits the whole non-empty draft as one `textCommit`;
  clear it only after the receiver acknowledges the command. Switching back to
  synchronous mode must not send the draft.
- In synchronous mode, replace the send cell with a rounded-stroke globe icon and
  a `中/EN` text button. The globe defaults to `Win + Space` and the language button
  defaults to a direct `Shift` press. Settings persist only predefined common choices:
  language toggle offers `Shift`, `Ctrl/Control + Space`, and macOS `Caps Lock`;
  input-method toggle offers Windows `Win + Space`, `Alt + Shift`, `Ctrl + Shift`,
  and macOS `Control + Space`. Do not accept or transmit arbitrary key chords.
- Follow Sogou's exposed event stream: its Chinese pinyin/candidates remain inside
  Sogou, so only the selected `commitText` reaches the PC. Its English composing
  word is exposed through the input connection and is mirrored with tail updates;
  later delimiter or correction commits must not duplicate that word.
- Do not detect Han text, switch modes, or automatically delete a presumed
  isolated-pinyin tail.
- Keep the soft keyboard focused while arrow, touchpad, and mouse controls are used.
- Do not declare a service, boot receiver, foreground notification, or wake lock.
  Close the network connection as soon as the activity is no longer visible.
- Do not queue input while disconnected.

## Confirmed test seams

1. IME events to public protocol commands.
2. Authenticated protocol frames to receiver commands.
3. Receiver commands to the Windows input injection boundary.

## Protocol v1

- TCP + TLS, UTF-8 JSON Lines, one line no larger than 65,536 bytes.
- Default endpoint port: `39421`.
- The server certificate SHA-256 fingerprint is pinned after the first successful
  pairing.
- The server sends `challenge` with a base64 nonce and uppercase certificate
  fingerprint. The client verifies that fingerprint against the presented TLS
  certificate and responds with an uppercase HMAC-SHA256 proof.
- HMAC key: uppercase 16-character Base32 pairing code without separators.
- HMAC data: UTF-8 bytes of `nonce:fingerprint`.
- Commands: `textCommit`, `replaceTail` (protocol compatibility), `keyPress`, `keyState`,
  `systemShortcut`, `pointerMove`, `pointerButton`, `wheel`, and `ping`.
- `textCommit` and `replaceTail` accept optional Boolean `preferPhysicalKeys`,
  defaulting to `false`; other commands reject it. `keyPress` includes `escape`.
- `keyState` accepts only W/A/S/D and the actions `down`/`up`. The receiver injects
  hardware scan-code state and releases all still-held game keys when a session ends.
- `systemShortcut` accepts only `shift`, `controlSpace`, `capsLock`, `windowsSpace`,
  `controlShift`, or `altShift`. The receiver presses each fixed chord in key-down
  order and releases it in reverse order without leaving a modifier held after failure.
- Commands carry `version`, `type`, `seq`, and `timestamp`; the server replies with
  `ack`, `pong`, `ready`, or `error`.
- Disconnected input is never replayed.
