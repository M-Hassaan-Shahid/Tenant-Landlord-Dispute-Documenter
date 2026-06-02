# ProofNest UI Prototype

Open **`proofnest-ui-prototype.html`** in any modern browser (Chrome recommended).

```bash
xdg-open docs/proofnest-ui-prototype.html
# or serve locally (avoids file:// clipboard warnings):
python3 -m http.server 8765 --directory docs
# then open http://localhost:8765/proofnest-ui-prototype.html
```

Icons are inline **SVG** (Material-style paths), not emoji—works offline on `file://`.

## Controls

- **Left sidebar** — jump to any screen
- **In-phone buttons** — follow the real app navigation flow
- **▶ Auto-play user journey** — animated walkthrough (splash → report)
- **Keyboard** — `←` `→` to move between screens in sidebar order

## Text contrast

Phone content uses **dark on-surface text** (`#0F1418`) isolated from the page’s light-on-green sidebar. Muted copy uses explicit grey tokens—not inherited `opacity` from the outer page—so cards (especially **Alerts**) stay readable on white backgrounds.

## Motion & UX

- Screen change: slide + fade; toolbar and content stagger in
- Cards: chevron on tappable rows; chip pop on rating select
- Bottom nav: active pill + icon scale
- Camera: brief flash + check, then return to inspection (adds photo thumb)
- Copy invite: inline “Copied!” (no toast overlay)
- Mark all read on Alerts; inspection progress bar updates as you rate items
- No global toast spam — feedback stays in-context

## What it reflects

Built from the Android XML layouts, `colors.xml`, `themes.xml`, and `strings.xml` in the app module.
