# shared-ui

UI building blocks that the trading app (`src/`) and the staff app (`projects/staff/`) show the same
way, kept once so the two headers cannot drift apart. Both apps import them as `@lemarket/shared-ui`
(a path alias in each app's `tsconfig.json`); the library is compiled as part of each app, so it has
no build of its own.

| Export | What it is |
|---|---|
| `Theme`, `ThemeName`, `THEME_STORAGE_KEY` | The dark/light theme switch, remembered in `localStorage`. Each app's `index.html` reads the same key before Angular starts. |
| `AccountMenu` (`<lm-account-menu>`) | The avatar menu in each app's header: who is signed in, Appearance and Log out. |

Only presentational pieces and browser-only services belong here. Features, routes, guards and
anything that calls the back end stay in their app, as AGENTS.md describes.

Specs run with the trading app's tests (`ng test lemarket-ui`, which includes
`projects/shared-ui/src/**/*.spec.ts`); styles come from `src/styles/base.css`, which both apps load.
