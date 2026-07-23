# Shortcut panel design QA

## Target

- Reference: the user-provided `922 x 2048` dark-theme screenshot in this task
- State: dark theme, connected, IME visible, shortcut panel expanded
- Implementation viewport: `1440 x 3200`, normalized to the reference's aspect ratio for comparison

The screenshot records the previously faulty build. The user's written corrections are the source of truth for the shortcut-row padding, arrow rotation, and IME-close timing.

The comparison screenshots and recordings were generated as run artifacts rather than repository fixtures. The durable checks are the measurements and automated tests listed below.

## Comparison history

| Severity | Initial mismatch | Fix | Final evidence |
| --- | --- | --- | --- |
| P1 | The arrow label and shortcut body lagged behind the closing IME; the body appeared only after the IME had disappeared. | On API 30+, the body is moved to its final bottom layout as soon as IME closing begins, and arrow edge motion completes at 75% of the close transition. On API 26-29, the first downward visible-frame movement places the body at the final keyboard-off position using the remaining IME height, hides the arrow, and removes the old 300ms debounce. | Final device recording showed the arrow gone while the IME was still visible and the modifier row already waiting behind the descending IME. `ImePanelMotionStateTest` and `LegacyImePanelMotionStateTest` cover both paths, including the legacy geometry offset. |
| P2 | The shortcut row had about `24dp` top and `42dp` bottom padding. | The body now uses symmetric `33dp` vertical padding around the `44dp` buttons. | Final button bounds are `[1668,1833]` inside the `110dp` body, leaving `33dp` above and below. |
| P2 | Rotating the toggle view inverted the complete label shape. | The label background remains fixed; only its arrow `ImageView` rotates between expanded and collapsed states. | Final expanded capture shows an upright rounded label with only the icon pointing down. |
| P1 | Reversing an IME close could leave a collapsed shortcut body visible. | IME motion now emits an explicit body restore action, and both body and toggle animators are atomically settled when IME motion takes control. | `ImePanelMotionStateTest.reversing an animation...` asserts `RESTORE_FOR_SHOW`; a final close-to-open reversal recording ended with the body hidden and the arrow attached to the visible IME. |

## Required surfaces

- Layout and spacing: passed; the shortcut row is centered vertically and remains within the available width.
- Typography and copy: passed; existing app text styles and labels are unchanged.
- Color and theme tokens: passed; the correction does not introduce hard-coded colors and continues using the active theme.
- Assets: passed; the existing arrow drawable is reused, with rotation isolated to the icon layer.
- Full-view regression: passed; header, input field, controls, touchpad, and keyboard alignment remain unchanged in the side-by-side check.
- Motion: passed on the connected Android device for keyboard opening, keyboard closing, expanded/collapsed panel states, and close-to-open direction reversal.
- Legacy fallback: passed by unit test for early close preparation, per-frame geometry updates, close reversal, idle rollback after a non-closing keyboard-height change, and invisible-session filtering on API 26-29 logic.

## Final result

Passed. No remaining P0-P2 visual mismatch was found for the requested corrections.
