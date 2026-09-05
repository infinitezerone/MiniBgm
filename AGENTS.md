# MiniBgm AI Agent & Developer Guidelines

Architectural context, coding standards, and verification workflow for **MiniBgm** — a modern Bangumi (bgm.tv) on-air schedule, anime tracking, and collection management client. Modular Clean Architecture modeled on Google's *Now in Android*. Written for human developers and AI coding agents alike.

## Tech Stack

Exact versions live in `gradle/libs.versions.toml` and `build-logic` convention plugins — treat those as the source of truth. Only the constraints below affect everyday coding decisions:

- **Language**: Kotlin (K2); JVM target 25 with core library desugaring (governs the available `java.*` API surface)
- **SDK levels**: minSdk 31, compileSdk/targetSdk 37 (governs the available `android.*` APIs)
- **Multiplatform**: `:core:model`, `:core:common`, `:core:network`, `:core:database`, `:core:datastore`, `:core:data`, and `:core:testing` are Kotlin Multiplatform (`commonMain`, androidTarget only — no desktop/iOS targets). `:app`, `:core:designsystem`, and `:core:navigation` are Android-only.
- **UI**: Jetpack Compose + Material 3 Expressive
- **Stack**: Ktor 3 + kotlinx.serialization, Room 3 (KMP) + DataStore, Coil 3, Koin 4

## Module Map

```
MiniBgm/
├── app/                        # Entry point, MainActivity, Koin init, OAuth deep-link handling
├── build-logic/                # Convention plugins (minibgm.* ids) — all shared module config lives here
├── core/
│   ├── model/                  # Pure Kotlin data classes (Subject, Episode, AirSchedule, SearchFilter, ...)
│   ├── common/                 # AppResult, BgmDispatchers, TimeUtils, UserDataClearable
│   ├── network/                # Ktor dual-client setup, Bangumi REST v0 API, OAuth token refresh loop, ETag cache
│   ├── database/               # Room 3 entities, DAOs, BgmDatabase
│   ├── datastore/              # UserPreferences (commonMain); Keystore-encrypted AuthTokensDataSource (androidMain)
│   ├── data/                   # Repositories: AuthRepository, ScheduleRepository, SubjectRepository, CollectionRepository, SearchRepository, SyncManager
│   ├── designsystem/           # MiniBgmTheme, M3 tokens, CoverImage
│   ├── navigation/             # Navigation 3 NavKey contracts, BgmNavState, TopLevelDestination, BgmRoutes
│   └── testing/                # Test doubles (Fake repositories), MainDispatcherRule, TestData
├── sync/
│   └── work/                   # WorkManager background sync, BgmSyncWorker, periodic & one-time sync manager
├── worker (moved)              # OAuth token-exchange proxy: maintained in a separate private Cloudflare Workers repo; deploys to bgmplus-auth.shadow2go.dpdns.org via its own CI
└── feature/                    # (Scaffolded with convention plugin minibgm.android.feature)
    ├── schedule/               # Weekly on-air schedule & countdown
    ├── subject/                # Subject detail, cast/staff, episode grouping, bottom sheets & custom tabs
    ├── user/                   # Collections browsing, multi-account management & quick progress tracking
    └── search/                 # Seasonal discovery, search & multi-dimensional tag filtering
```

## Module Rules

1. **Feature isolation**: `:feature:A` must never depend on `:feature:B`; inter-feature navigation goes through type-safe route contracts. Feature modules use the `minibgm.android.feature` convention plugin.
2. **`:core:model` is pure Kotlin**: no `android.*` or UI framework dependencies.
3. **Single source of truth**: repositories in `:core:data` coordinate `:core:network` and `:core:database`; UI layers never touch network or database directly.
4. **Convention plugins first**: new modules apply a `minibgm.*` plugin from the catalog instead of hand-configuring Android/Kotlin settings.
5. **Lightweight module docs**: module-level `README.md` files only declare **scope/responsibilities**, **dependency topology**, and **architectural invariants/redlines** — avoid internal implementation details or class inventories to prevent documentation rot.
6. **Navigation**: app uses **Navigation 3** (`androidx.navigation3`) — `@Serializable` `NavKey` routes declared in `:core:navigation` (`BgmRoutes.kt`), rendered by `NavDisplay` in `:app`; per-tab `NavBackStack`s live in `BgmNavState` (exit-through-home, survives process death). Don't relocate route contracts or rewire the navigation stack without a deliberate decision.

## Coding Guidelines

### Canonical Precedents and New Decisions

Before implementing a new pattern, find the closest applicable precedent and state it in the change summary. Do not treat a superficially similar file as a precedent when its lifecycle, navigation shape, or persistence behaviour differs.

- **Top-level read-only feature**: `:feature:schedule` — stateful screen, repository-backed ViewModel, navigation callback, and ViewModel test.
- **Parameterized detail destination**: `:feature:subject` — typed route argument, entry-scoped ViewModel, back callback, fetch-then-observe repository flow, and test coverage.
- **User-triggered mutation**: `CollectionRepository` in `:core:data` — use this for cancellation, error, and local/remote consistency decisions.
- **Security-sensitive authentication**: `AuthRepository` in `:core:data` plus `BgmPkce` in `:core:network` — never create an alternate token or OAuth flow.
- **Feature test shape**: the corresponding `*ViewModelTest` beside each feature is the default precedent for state transitions and error handling.

If none of these precedents fits, stop before introducing a new architectural pattern. Propose a concise decision containing: the problem, the rejected closest precedent, the proposed module/API boundary, persistence and error behaviour, and the verification plan. Implement it only after that decision is accepted and add the new canonical precedent here when it is intended for reuse.

### Architecture & State
- Unidirectional Data Flow (MVI): ViewModels expose a single, immutable `StateFlow<UiState>`; one-off events (snackbars, navigation) go through `Channel`/`SharedFlow`.
- Wrap data/domain operations in `AppResult<T>` (`Success`/`Error`/`Loading`, defined in `:core:common`).
- Mutating repository operations and critical writes (e.g. check-ins, collection status updates) must be guarded with `withContext(NonCancellable)` so that navigating away from a screen or ViewModel destruction does not cancel in-flight network synchronization.

### Network (Ktor 3)
- Every `BgmHttpClient` request carries `User-Agent: MiniBgm/<versionName> (android) (https://github.com/infinitezerone/MiniBgm)` via `DefaultRequest` — never strip or override it; keep the version string in sync with the app's `versionName`.
- Reuse `BgmHttpClient.jsonConfig` (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`, ...) instead of hand-rolling `Json` instances.
- Errors surface as typed `BgmNetworkException` subclasses (`Unauthorized`, `Forbidden`, `NotFound`, `RateLimited`, `ServerError`, `Unknown`), mapped from HTTP status codes.
- Business API calls go direct to `api.bgm.tv`; only token exchange/refresh goes through the Cloudflare Worker proxy. The Ktor auth plugin auto-refreshes on 401 and clears credentials on unrecoverable refresh failures (auto-logout).
- OAuth code redemption is guarded by a Worker-enforced PKCE equivalent (bgm.tv lacks PKCE): `BgmPkce` generates a local `verifier` whose `sha256` fingerprint rides as the OAuth `state`; the exchange must carry `code + state + verifier` or the Worker rejects with `verifier_mismatch`. Never bypass or weaken this check.

### Persistence (Room 3 & DataStore)
- Room DAO reads return `Flow<T>`; writes/upserts are `suspend` functions.
- OAuth tokens never live in `UserPreferences` — they go through `AuthTokensDataSource` (AndroidKeyStore-encrypted, excluded from device backups).

### Build System (AGP 9)
- `:app` / `:core:designsystem` use **AGP built-in Kotlin** — never re-add `org.jetbrains.kotlin.android`; Kotlin compile config goes through `android.compileOptions` (jvmTarget defaults to `targetCompatibility`).
- KMP modules use `com.android.kotlin.multiplatform.library` (applied by `minibgm.kmp.library`), which is **single-variant** (no debug/release) and has **no top-level `android {}` extension** — Android config (namespace, desugaring, host tests) goes through `Project.kmpAndroidLibrary { }` (finalizeDsl) in `build-logic`, and test source sets are named `androidHostTest` / `androidDeviceTest` with tests **opt-in** (`withHostTest` is already enabled in the convention plugin).
- KMP modules use `testAndroid` (per module) or `allTests` (aggregate); Android-only modules use the standard variant task such as `testDebugUnitTest`. Select the task from the module's applied convention plugin instead of assuming one task name for all modules.
- **Green ≠ tested**: a misnamed or empty test source set fails silently (the `androidUnitTest` → `androidHostTest` incident shipped a build where tests ran zero cases, all green). After any build-script or source-set change, verify the selected task's `build/test-results/<task>/*.xml` exists with `tests > 0` before claiming tests pass — BUILD SUCCESSFUL alone proves nothing.

### Compose & Design System
- Always build under `MiniBgmTheme` using tokens from `:core:designsystem`; no hardcoded colors or typography in features.
- Keep recomposition cheap: immutable state classes, `@Stable` where useful, stable lambdas.
- Support edge-to-edge: `enableEdgeToEdge()` plus proper `WindowInsets` padding.

## Build & Verification

```bash
./gradlew :app:assembleDebug                  # assemble debug APK — also the cross-module compile gate
./gradlew :core:network:testAndroid           # KMP module unit tests; substitute a touched KMP module
./gradlew :core:navigation:testDebugUnitTest  # Android-only module unit tests; substitute a touched Android module
./gradlew spotlessCheck                       # ktlint + whitespace gate (spotlessApply to auto-fix)
./gradlew allTests                             # FULL test suite — only for cross-cutting changes (see rule 2)
./gradlew clean                               # rarely needed
```

**Rules for AI agents:**

1. **Verify before claiming**: the default loop for everyday changes is targeted, not full-suite — `./gradlew spotlessCheck`, then the matching test task for each touched module (`testAndroid` for KMP; normally `testDebugUnitTest` for Android-only), then `./gradlew :app:assembleDebug`. Report failures honestly.
2. **Full `./gradlew allTests` only for cross-cutting changes**: touching `build-logic/`, `gradle/libs.versions.toml`, or the shared bases `:core:model` / `:core:common` (everything depends on them), and before opening a PR.
3. **Declare dependencies in the catalog first**: add versions/libraries/plugins to `gradle/libs.versions.toml`, then reference them via type-safe accessors (`libs.xxx`).
4. **Respect module boundaries**: no circular dependencies; never violate feature isolation (see Module Rules).

## Git & Commits

- Conventional Commits with module scope: `feat(core:datastore): ...`, `fix(app): ...`, `build: ...`.
- One commit = one purpose: don't mix unrelated reformatting or churn into a functional change.
