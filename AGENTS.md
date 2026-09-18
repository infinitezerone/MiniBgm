# MiniBgm AI Agent & Developer Guidelines

Architectural context, coding standards, and verification workflow for **MiniBgm** — a modern Bangumi (bgm.tv) on-air schedule, anime tracking, and collection management client. Modular Clean Architecture modeled on Google's *Now in Android*.

## Hard Redlines (enforced, not suggested)

The rules below are **deterministically enforced by `ArchitectureRulesTest`** in `:core:testing` — a violation fails `./gradlew :core:testing:testAndroid`. When introducing a new redline, add its test there first; do not rely on prose alone.

1. **Feature isolation**: `:feature:A` must never depend on `:feature:B`; inter-feature navigation goes through type-safe route contracts.
2. **Single source of truth**: UI layers go through `:core:data` repositories only — no `:core:network` / `:core:database` / `:core:datastore` project dependencies, and no `io.ktor.*` / `androidx.room.*` imports, in feature sources. User-preference reads/writes go through `SettingsRepository` (login state through `AuthRepository`).
3. **`:core:model` is pure Kotlin**: no `android.*` / `androidx.*` imports.
4. **MVI**: a ViewModel never exposes a `MutableStateFlow` publicly (this is the half enforced by `viewModels_never_expose_mutable_state_flow`). How many read-only `StateFlow`s it exposes is a design choice — independent slices may stay separate; don't fold unrelated state into one object just to have a single flow. One-off events (snackbar text, transient messages) go through a `Channel` + `receiveAsFlow()`, never through state; navigation stays a callback lambda on the screen.
5. **Credential isolation**: OAuth tokens only ever live in `AuthTokensDataSource` (AndroidKeyStore-encrypted, excluded from backups) — never in `UserPreferences` or plain DataStore keys.
6. **Theming**: no hardcoded `Color(0x...)` in features — build under `MiniBgmTheme` with tokens from `:core:designsystem`.
7. **No raw IO in features**: feature sources must never hand-roll raw networking (`HttpURLConnection`, `java.net.URL`, `java.net.Socket`) or private disk I/O (`context.cacheDir`, `context.filesDir`, `FileOutputStream`) — all networking and persistence belong in `:core:network` / `:core:database` / `:core:datastore` and are coordinated exclusively through `:core:data` repositories.
8. **Every route declares its stacking layer**: route contracts are sealed under `BgmRoute` in `:core:navigation` (`BgmRoutes.kt`) and rendered by `NavDisplay` in `:app`; per-tab `NavBackStack`s live in `BgmNavState` (exit-through-home, survives process death). `BgmNavState`'s stacking-semantics `when` is compiler-exhaustive, so a new route must declare its layer (detail replace / second-level same-class replace / drill-down push) or it won't compile. Enforced by `ArchitectureRulesTest.navigation_routes_are_sealed_and_declared_only_in_bgm_routes`, with stacking/back invariants by `BgmNavStatePropertyTest`. Don't relocate route contracts or rewire the navigation stack without a deliberate decision.

Equally binding, but enforced by build config or code structure rather than the test suite:

- **Critical writes are non-cancellable**: mutating repository operations (check-ins, collection status updates) must be guarded with `withContext(NonCancellable)` so navigating away does not abort in-flight sync.
- **Never bypass or weaken** the Worker-enforced PKCE equivalent (`BgmPkce`): the exchange must carry `code + state + verifier` or the Worker rejects with `verifier_mismatch`. Never create an alternate token or OAuth flow.
- **Never strip or override `User-Agent`**: `DefaultRequest` injects `MiniBgm/<versionName> (android) (...)` from `BuildConfig.VERSION_NAME`; it is a required parameter on `BgmHttpClient.create` / `networkModule`.
- **Convention plugins first**: new modules apply a `minibgm.*` plugin from the catalog; no circular dependencies.

## Build & Verification

```bash
./gradlew :core:testing:testAndroid           # architecture redlines (ArchitectureRulesTest) — run always
./gradlew crapCheck                           # CRAP score quality gate (KMP modules <= 30.0)
./gradlew spotlessCheck                       # ktlint + whitespace gate (spotlessApply to auto-fix)
./gradlew :core:network:testAndroid           # KMP module unit tests; substitute a touched KMP module
./gradlew :core:navigation:testDebugUnitTest  # Android-only module unit tests; substitute a touched Android module
./gradlew :app:assembleDebug                  # assemble debug APK — also the cross-module compile gate
./gradlew allTests testDebugUnitTest crapCheck # FULL test suite — only for cross-cutting changes (see rule 2)
scripts/dual-screen-verify.sh                 # dynamic gate: dual window-size emulator smoke (needs a running device/emulator)
```

**Rules for AI agents:**

1. **Verify before claiming**: the default loop for everyday changes is `spotlessCheck` → `:core:testing:testAndroid` (architecture redlines) → the matching test task for each touched module (`testAndroid` for KMP; normally `testDebugUnitTest` for Android-only) → `:app:assembleDebug`. Report failures honestly.
2. **Full `./gradlew allTests testDebugUnitTest` only for cross-cutting changes**: touching `build-logic/`, `gradle/libs.versions.toml`, or the shared bases `:core:model` / `:core:common` (everything depends on them), and before opening a PR. `allTests` alone is a Kotlin Multiplatform aggregate and only covers KMP modules — Android-only modules (`:app`, `:feature:*`, `:sync:work`, `:core:designsystem`, `:core:navigation`) have no `allTests` task, so the root-level `testDebugUnitTest` must be named alongside it or their suites silently don't run.
3. **Green ≠ tested**: a misnamed or empty test source set fails silently — KMP test source sets are `androidHostTest` / `androidDeviceTest` and host tests are **opt-in**, so a stale name simply stops being collected (the `androidUnitTest` → `androidHostTest` incident shipped a build where tests ran zero cases, all green). After any build-script or source-set change, verify the selected task's `build/test-results/<task>/*.xml` exists with `tests > 0` before claiming tests pass — BUILD SUCCESSFUL alone proves nothing.
4. **Safe ADB screenshots**: never use bare `adb exec-out screencap -p > file.png` (emulator multi-display warnings corrupt PNG magic headers and break multimodal API calls). Always specify `-d 0` (`adb exec-out screencap -d 0 -p > ...`) or capture on-device first (`adb shell screencap -p /data/local/tmp/s.png && adb pull ...`), and verify with `file <file>.png` before passing to `view_file`.
5. **Dynamic gate for layout/navigation/skeleton changes**: static checks cannot catch split-pane transitions, back-stack behaviour or frame jank — the incidents of 2026-09 (split-mode shared transition, detail-route stacking) were all "statically green, behaviourally broken". Changes touching `:app` navigation (`BgmNavHost`, `BgmAdaptiveScenes`, `BgmNavTransitions`), `:core:navigation`, or skeleton/loading UI must additionally run `scripts/dual-screen-verify.sh` on a booted emulator (it walks Compact + Expanded window profiles, asserts no crash + rendered UI, and saves evidence screenshots to `build/verification/`); a deeper interactive walkthrough (tap-through list → detail → back) via emulator tooling is expected when the change touches back-stack semantics — navigation invariants are otherwise covered by `BgmNavStatePropertyTest`.

## Canonical Precedents and New Decisions

Before implementing a new pattern, find the closest applicable precedent and state it in the change summary. Do not treat a superficially similar file as a precedent when its lifecycle, navigation shape, or persistence behaviour differs.

- **Top-level read-only feature**: `:feature:schedule` — stateful screen, repository-backed ViewModel, navigation callback, and ViewModel test.
- **Parameterized detail destination**: `:feature:subject` — typed route argument, entry-scoped ViewModel, back callback, fetch-then-observe repository flow, and test coverage.
- **User-triggered mutation**: `CollectionRepository` in `:core:data` — use this for cancellation, error, and local/remote consistency decisions.
- **Security-sensitive authentication**: `AuthRepository` in `:core:data` plus `BgmPkce` in `:core:network` — never create an alternate token or OAuth flow.
- **Feature test shape**: the corresponding `*ViewModelTest` beside each feature is the default precedent for state transitions and error handling.
- **Architecture redline enforcement**: `ArchitectureRulesTest` in `:core:testing` — extend it with a new test whenever a cross-module rule is added to this document.

If none of these precedents fits, stop before introducing a new architectural pattern. Propose a concise decision containing: the problem, the rejected closest precedent, the proposed module/API boundary, persistence and error behaviour, and the verification plan. Implement it only after that decision is accepted and add the new canonical precedent here when it is intended for reuse.

## Tech Stack

Nothing here is a fact worth keeping in sync: dependency versions live in `gradle/libs.versions.toml`, shared module config in `build-logic` convention plugins, and the platform surface (JVM target + desugaring, which governs the `java.*` you may use; minSdk, which governs `android.*`) in those same files. Read them instead.

Two things a build file does not tell you:

- Every KMP module in this repo configures **`androidTarget` only** — there is no second platform target, so an `expect` has exactly one `actual` and nothing needs a multiplatform design.
- A module being KMP vs Android-only changes its source sets, its test task name, and whether an `android.*` import is legal at all. Read the `minibgm.*` plugin in that module's own `build.gradle.kts`; inventories of this drifted twice before being deleted.

## Module Layout

No inventory here — `settings.gradle.kts` and the directory tree are authoritative, and every list maintained in this document has drifted. What the tree does not tell you:

- **The OAuth token-exchange/refresh proxy is not in this repository** — it lives in a separate private Cloudflare Workers project, so its code will never appear in a search here. Only token exchange/refresh goes through it; business API calls go direct to `api.bgm.tv`.
- Dependency direction between layers is not a convention — it is mechanically enforced by redlines 1–3 and 7, so a misplaced import fails the build rather than merely looking wrong.

## Domain Notes

- Reuse `BgmHttpClient.jsonConfig` (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`, ...) instead of hand-rolling `Json` instances — nothing fails when you don't, so this is one of the few conventions still without a test; it belongs in `ArchitectureRulesTest` once someone writes it.
- The Ktor auth plugin auto-refreshes on 401 and **clears credentials on an unrecoverable refresh failure** (auto-logout). A caller that catches a 401 will not see this happen.
- **Air Schedule Ground Truth (No predicted episodes)**: Single-episode air events (`AirEventEntity`) and next-episode tracking (`nextEpisode`, `nextEpisodeAtUtc`, `nextEpisodeKind`, `timeCst`, `timeJst`, `weekday`) are driven exclusively by verified episode events from AniList (`actual`/`scheduled`) and Bilibili (`pub_time`). Arithmetic prediction (`P7D` loop / `broadcastRule`) is completely deprecated and eliminated; never generate fake episodes. `bangumi-data` serves strictly as metadata and cross-platform relation mappings (`anilistId`, Bilibili IDs, `sitesJson`, `titleCn`), and must never overwrite or pollute official broadcast dates/times. AniList broadcast times and split-cour offsets are derived deterministically from verified air events without heuristic tolerance dropouts.

## Git & Commits

- Conventional Commits with module scope: `feat(core:datastore): ...`, `fix(app): ...`, `build: ...`.
- One commit = one purpose: don't mix unrelated reformatting or churn into a functional change.
- **This repo is colocated with jj** (`.jj/` shares the same working copy; Git cannot see it, so never add `.jj` to `.gitignore`). Never run mutating Git commands here — `git commit`, `git rebase`, `git reset`, `git checkout` bypass jj's operation log and can leave the working-copy commit pointing at a stale parent, making changes look lost. Use `jj` for history (`jj commit -m`, `jj describe -m`, `jj bookmark set main -r @`, `jj undo`) and `git` only for clone / tags / CI.
- **Content gates are unaffected by the VCS**: `spotlessCheck` / `crapCheck` / `:core:testing:testAndroid` / the per-module test tasks / `assembleDebug` all validate file content, not history. Keep running `./gradlew spotlessApply` before committing — CI runs them regardless of which client made the commit.

## Release Process

- **Tag is the single source of truth for versions**: pushing a `vX.Y.Z` tag injects `versionName=X.Y.Z` and `versionCode=X*10000+Y*100+Z` into the build via `-Pminibgm.versionName/-Pminibgm.versionCode` (see `AndroidApplicationConventionPlugin`, which falls back to hardcoded defaults for local builds). Never bump versions by editing build-logic for a release — that reintroduced the "tag ≠ artifact version" and "versionCode not monotonic" incidents of v0.2.8.
- **Release gates** (`release.yml`): tag-triggered releases re-run the full static gauntlet (spotless + allTests + testDebugUnitTest + crapCheck) before publishing, and **fail hard** if signing secrets are missing — a debug-signed release cannot upgrade real installs and must never be published (debug fallback is allowed only for `workflow_dispatch` build rehearsals). `mapping.txt` is attached to every release for crash de-obfuscation.
- **Release-variant compilation is part of CI** (`assembleRelease` in `ci.yml`): R8/keep-rule breakage surfaces on every push, not only at tag time.
- CI and release verify independently of local state: tags may point at any historical commit, so the release workflow re-verifies rather than trusting that CI ran on main.
