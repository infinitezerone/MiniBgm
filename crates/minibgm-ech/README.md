# minibgm-ech

Rust 实现的 ECH（Encrypted Client Hello）原生库，编译为 `libminibgm_ech.so`，由 `:core:network` 的 `buildEchNative` 任务在 jniLibs 合并前接入打包。产物落在 `:core:network/build/ech-native/jniLibs`，**不要写进 `src/`**——那会撞上 `core/testing` 架构红线测试的 `core/*/src/**` 输入声明。

## 构建要求

任何 Android 打包都需要 Rust 工具链：rustup + `cargo install cargo-ndk` + NDK。

- NDK 解析顺序：`-Pminibgm.ech.ndkDir` → `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT` → `ANDROID_HOME/local.properties(sdk.dir)` 下最高版本。
- 已有产物时可用 `-Pminibgm.ech.skipBuild=true` 跳过编译，但产物必须与 Rust 源码指纹一致，否则失败。
- Windows 宿主需 MSVC C++ 构建工具与 NASM。

**缺库一律硬失败**——禁止静默回退 CIO，那等于 ECH 在发布产物里不存在（v0.3 之前的老问题）。

## Rust 门禁

`cargo fmt --check` / `cargo clippy -D warnings` / `cargo test`（`tools/jgate` 触及 `crates/` 或 `--all` 时本地执行，命令与 CI 逐字一致）。

## Windows 排障

若构建 aws-lc-sys 遇 `0xc0000142`（STATUS_DLL_INIT_FAILED），系目录被沙盒工具标记了低完整性级别（Low Mandatory Level），需在管理员 PowerShell 执行：

```powershell
icacls . /setintegritylevel "(OI)(CI)M" /t /c /q
```

恢复正常完整性级别即可，jgate 保持全量硬门禁，不要为此放宽。
