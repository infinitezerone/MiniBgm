# MiniBgm AI Agent & Developer Guidelines

Architectural context, coding standards, and verification workflow for **MiniBgm** — a modern Bangumi (bgm.tv) on-air schedule, anime tracking, and collection management client. Modular Clean Architecture modeled on Google's *Now in Android*.

## Hard Redlines (enforced, not suggested)

The rules below are **deterministically enforced by `ArchitectureRulesTest`** in `:core:testing` — a violation fails `./gradlew :core:testing:testAndroid`. When introducing a new redline, add its test there first; do not rely on prose alone.

1. **Feature isolation**: `:feature:A` must never depend on `:feature:B`; inter-feature navigation goes through type-safe route contracts.
2. **Single source of truth**: UI layers go through `:core:data` repositories only — no `:core:network` / `:core:database` / `:core:datastore` project dependencies, and no `io.ktor.*` / `androidx.room.*` imports, in feature sources. User-preference reads/writes go through `SettingsRepository` (login state through `AuthRepository`).
3. **`:core:model` is pure Kotlin**: no `android.*` / `androidx.*` imports.
4. **MVI**: ViewModels expose a single, immutable `StateFlow<UiState>` — never a public `MutableStateFlow`; one-off events (snackbars, navigation) go through `Channel`/`SharedFlow`.
5. **Credential isolation**: OAuth tokens only ever live in `AuthTokensDataSource` (AndroidKeyStore-encrypted, excluded from backups) — never in `UserPreferences` or plain DataStore keys.
6. **Theming**: no hardcoded `Color(0x...)` in features — build under `MiniBgmTheme` with tokens from `:core:designsystem`.
7. **No raw IO in features**: feature sources must never hand-roll raw networking (`HttpURLConnection`, `java.net.URL`, `java.net.Socket`) or private disk I/O (`context.cacheDir`, `context.filesDir`, `FileOutputStream`) — all networking and persistence belong in `:core:network` / `:core:database` / `:core:datastore` and are coordinated exclusively through `:core:data` repositories.

Equally binding, but enforced by build config or code structure rather than the test suite:

- **Critical writes are non-cancellable**: mutating repository operations (check-ins, collection status updates) must be guarded with `withContext(NonCancellable)` so navigating away does not abort in-flight sync.
- **Never bypass or weaken** the Worker-enforced PKCE equivalent (`BgmPkce`): the exchange must carry `code + state + verifier` or the Worker rejects with `verifier_mismatch`. Never create an alternate token or OAuth flow.
- **Never strip or override `User-Agent`**: `DefaultRequest` injects `MiniBgm/<versionName> (android) (...)` from `BuildConfig.VERSION_NAME`; it is a required parameter on `BgmHttpClient.create` / `networkModule`.
- **Convention plugins first**: new modules apply a `minibgm.*` plugin from the catalog; no circular dependencies.

## Build & Verification

```bash
./gradlew :core:testing:testAndroid           # architecture redlines (ArchitectureRulesTest) — run always
./gradlew spotlessCheck                       # ktlint + whitespace gate (spotlessApply to auto-fix)
./gradlew :core:network:testAndroid           # KMP module unit tests; substitute a touched KMP module
./gradlew :core:navigation:testDebugUnitTest  # Android-only module unit tests; substitute a touched Android module
./gradlew :app:assembleDebug                  # assemble debug APK — also the cross-module compile gate
./gradlew allTests testDebugUnitTest          # FULL test suite — only for cross-cutting changes (see rule 2)
```

**Rules for AI agents:**

1. **Verify before claiming**: the default loop for everyday changes is `spotlessCheck` → `:core:testing:testAndroid` (architecture redlines) → the matching test task for each touched module (`testAndroid` for KMP; normally `testDebugUnitTest` for Android-only) → `:app:assembleDebug`. Report failures honestly.
2. **Full `./gradlew allTests testDebugUnitTest` only for cross-cutting changes**: touching `build-logic/`, `gradle/libs.versions.toml`, or the shared bases `:core:model` / `:core:common` (everything depends on them), and before opening a PR. `allTests` alone is a Kotlin Multiplatform aggregate and only covers KMP modules — Android-only modules (`:app`, `:feature:*`, `:sync:work`, `:core:designsystem`, `:core:navigation`) have no `allTests` task, so the root-level `testDebugUnitTest` must be named alongside it or their suites silently don't run.
3. **Declare dependencies in the catalog first**: add versions/libraries/plugins to `gradle/libs.versions.toml`, then reference them via type-safe accessors (`libs.xxx`).
4. **Green ≠ tested**: a misnamed or empty test source set fails silently (the `androidUnitTest` → `androidHostTest` incident shipped a build where tests ran zero cases, all green). After any build-script or source-set change, verify the selected task's `build/test-results/<task>/*.xml` exists with `tests > 0` before claiming tests pass — BUILD SUCCESSFUL alone proves nothing.

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

Exact versions live in `gradle/libs.versions.toml` and `build-logic` convention plugins — treat those as the source of truth. Only these constraints affect everyday coding decisions: Kotlin (K2) with JVM target 25 + desugaring (governs `java.*` surface); minSdk 31, compileSdk/targetSdk 37 (governs `android.*` surface); Kotlin Multiplatform for `:core:model/common/network/database/datastore/data/testing` (androidTarget only), Android-only for `:app`, `:core:designsystem`, `:core:navigation`; Jetpack Compose + Material 3 Expressive; Ktor 3, Room 3 (KMP) + DataStore, Coil 3, Koin 4.

## Module Map

- `:app` — entry point: MainActivity, Koin init, OAuth deep-link handling. Navigation 3 (`androidx.navigation3`): `@Serializable` `NavKey` routes live in `:core:navigation` (`BgmRoutes.kt`), rendered by `NavDisplay` in `:app`; per-tab `NavBackStack`s live in `BgmNavState` (exit-through-home, survives process death). Don't relocate route contracts or rewire the navigation stack without a deliberate decision.
- `build-logic` — convention plugins (`minibgm.*` ids); all shared module config lives here.
- `:core:*` — `model` (pure data classes), `common` (AppResult, BgmDispatchers, TimeUtils), `network` (Ktor dual-client, Bangumi REST v0, OAuth refresh loop, ETag cache), `database` (Room), `datastore` (UserPreferences + Keystore-encrypted AuthTokensDataSource), `data` (repositories, SyncManager), `testing` (fakes, TestData, ArchitectureRulesTest).
- `:sync:work` — WorkManager background sync (`BgmSyncWorker`).
- `:feature:*` — `schedule`, `subject`, `user`, `search`, `widget`; scaffolded with the `minibgm.android.feature` plugin.
- OAuth token-exchange proxy: maintained in a separate private Cloudflare Workers repo, not in this codebase.

## Domain Notes

- Wrap data/domain operations in `AppResult<T>` (`Success`/`Error`/`Loading`, defined in `:core:common`).
- Room DAO reads return `Flow<T>`; writes/upserts are `suspend` functions.
- Reuse `BgmHttpClient.jsonConfig` (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`, ...) instead of hand-rolling `Json` instances; errors surface as typed `BgmNetworkException` subclasses mapped from HTTP status codes.
- Business API calls go direct to `api.bgm.tv`; only token exchange/refresh goes through the Cloudflare Worker proxy. The Ktor auth plugin auto-refreshes on 401 and clears credentials on unrecoverable refresh failures (auto-logout).
- Keep recomposition cheap: immutable state classes, `@Stable` where useful, stable lambdas; support edge-to-edge (`enableEdgeToEdge()` + proper `WindowInsets` padding).

## Build System (AGP 9)

- `:app` / `:core:designsystem` use **AGP built-in Kotlin** — never re-add `org.jetbrains.kotlin.android`; Kotlin compile config goes through `android.compileOptions` (jvmTarget defaults to `targetCompatibility`).
- KMP modules use `com.android.kotlin.multiplatform.library` (applied by `minibgm.kmp.library`), which is **single-variant** (no debug/release) and has **no top-level `android {}` extension** — Android config (namespace, desugaring, host tests) goes through `Project.kmpAndroidLibrary { }` (finalizeDsl) in `build-logic`, and test source sets are named `androidHostTest` / `androidDeviceTest` with tests **opt-in** (`withHostTest` is already enabled in the convention plugin).
- KMP modules use `testAndroid` (per module) or `allTests` (aggregate); Android-only modules use the standard variant task such as `testDebugUnitTest`. Select the task from the module's applied convention plugin instead of assuming one task name for all modules.

## Git & Commits

- Conventional Commits with module scope: `feat(core:datastore): ...`, `fix(app): ...`, `build: ...`.
- One commit = one purpose: don't mix unrelated reformatting or churn into a functional change.
