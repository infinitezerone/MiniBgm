# minibgm-ech

Rust 实现的 ECH（Encrypted Client Hello）原生库，由独立构建脚本 `tools/build-native` 编译为 `libminibgm_ech.so`（产物落在 `:core:network/build/ech-native/jniLibs`，**不要写进 `src/`**——那会撞上 `core/testing` 架构红线测试的 `core/*/src/**` 输入声明）。Gradle 仅通过 `registerPrebuiltJniLibs` 挂载产物目录并在打包时校验 `.so` 存在，绝不跨界调度 Cargo / NDK。

## 构建要求

编译原生库需要 Rust 工具链：rustup + `cargo install cargo-ndk` + NDK，执行 `bash tools/build-native`（Windows：`.\tools\build-native.ps1`；`jgate` 会自动执行）。

- NDK 解析顺序：`ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT` → `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `local.properties(sdk.dir)` 下最高可用版本。
- `tools/build-native` 内置源码 SHA-256 指纹缓存（`.native-build.stamp`）：源码未变且 `.so` 齐全时毫秒级短路跳过。
- Windows 宿主需 MSVC C++ 构建工具与 NASM。

**缺库一律硬失败**——Gradle 打包时若检测到 `.so` 缺失直接中断构建，禁止静默回退 CIO。

## Rust 门禁

`cargo fmt --check` / `cargo clippy -D warnings` / `cargo test`（`tools/jgate` 触及 `crates/` 或 `--all` 时本地执行，命令与 CI 逐字一致）。

## Windows 排障

若构建 aws-lc-sys 遇 `0xc0000142`（STATUS_DLL_INIT_FAILED），系目录被沙盒工具标记了低完整性级别（Low Mandatory Level），需在管理员 PowerShell 执行：

```powershell
icacls . /setintegritylevel "(OI)(CI)M" /t /c /q
```

恢复正常完整性级别即可，jgate 保持全量硬门禁，不要为此放宽。
