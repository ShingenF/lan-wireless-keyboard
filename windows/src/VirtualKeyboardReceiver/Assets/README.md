# Icon assets

## Application, window, and notification area

- Design: Hugeicons `KeyboardIcon`, Stroke Rounded
- Official package: `@hugeicons/core-free-icons@4.2.2`
- Preserved SVG source: `hugeicons-keyboard-stroke-rounded.svg`
- Geometry: six official 24x24 paths, `strokeWidth=1.5`, rounded line caps/joins
- Generated resource: `receiver.ico`
- License: MIT; see the repository `THIRD_PARTY_NOTICES.md`.

`tools/generate-icons.py` keeps the application and tray sources separate. Select `--target application`, `--target tray`, or `--target all`. Each selected icon is independently rendered at 16, 20, 24, 32, 40, 48, 64, and 256 pixels before its PNG frames are packaged into a transparent ICO:

- `receiver.ico`: black application/window icon
- `tray-hugeicons-keyboard-black.ico`: black notification-area icon for a light Windows system theme
- `tray-hugeicons-keyboard-white.ico`: white notification-area icon for a dark Windows system theme

No background, shadow, outline container, or gradient is added.
