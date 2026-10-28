use sha2::{Digest, Sha256};
use std::env;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

pub struct NativeTarget {
    #[allow(dead_code)]
    pub short_name: &'static str,
    pub crate_name: &'static str,
    pub crate_dir: &'static str,
    pub platform_api: &'static str,
    pub out_dir: &'static str,
    pub lib_name: &'static str,
}

pub const TARGET_ECH: NativeTarget = NativeTarget {
    short_name: "ech",
    crate_name: "minibgm-ech",
    crate_dir: "crates/minibgm-ech",
    platform_api: "31",
    out_dir: "core/network/build/ech-native/jniLibs",
    lib_name: "libminibgm_ech.so",
};

pub const TARGET_MPV: NativeTarget = NativeTarget {
    short_name: "mpv",
    crate_name: "minibgm-mpv",
    crate_dir: "crates/minibgm-mpv",
    platform_api: "24",
    out_dir: "feature/subject/build/mpv-native/jniLibs",
    lib_name: "libminibgm_mpv.so",
};

pub const TARGET_TORRENT: NativeTarget = NativeTarget {
    short_name: "torrent",
    crate_name: "minibgm-torrent",
    crate_dir: "crates/minibgm-torrent",
    platform_api: "24",
    out_dir: "core/data/build/torrent-native/jniLibs",
    lib_name: "libminibgm_torrent.so",
};

pub fn load_abis(root: &Path) -> Result<Vec<String>, String> {
    let abis_path = root.join("native/abis.txt");
    if !abis_path.is_file() {
        return Err(format!("缺少 ABI 真源文件：{}", abis_path.display()));
    }

    let content = fs::read_to_string(&abis_path)
        .map_err(|e| format!("无法读取 ABI 文件 {}: {}", abis_path.display(), e))?;

    let mut abis = Vec::new();
    for raw_line in content.lines() {
        let line = raw_line.trim();
        let line = line.split('#').next().unwrap_or("").trim();
        if line.is_empty() {
            continue;
        }

        if !line
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_' || c == '-')
        {
            return Err(format!(
                "ABI 真源文件中的条目非法（只允许小写字母/数字/下划线/连字符）：'{line}'"
            ));
        }

        abis.push(line.to_string());
    }

    if abis.is_empty() {
        return Err(format!(
            "ABI 真源文件未声明任何 ABI：{}",
            abis_path.display()
        ));
    }

    Ok(abis)
}

pub fn compute_fingerprint(
    root: &Path,
    crate_rel_dir: &str,
    platform_api: &str,
    abis: &[String],
) -> Result<String, String> {
    let mut hasher = Sha256::new();

    let header = format!("abis={}\napi={}\n", abis.join(" "), platform_api);
    hasher.update(header.as_bytes());

    let mut files_to_hash = Vec::new();

    // 根清单文件
    for manifest_name in &["Cargo.toml", "Cargo.lock"] {
        let manifest_path = root.join(manifest_name);
        if manifest_path.is_file() {
            files_to_hash.push(manifest_name.to_string());
        }
    }

    // 递归收集 crate 源码文件
    let crate_full_dir = root.join(crate_rel_dir);
    if crate_full_dir.is_dir() {
        collect_crate_files(root, &crate_full_dir, &mut files_to_hash)?;
    }

    // 按相对路径升序排序（与 Shell LC_ALL=C 行为一致）
    files_to_hash.sort();

    for rel_path in files_to_hash {
        let full_path = root.join(&rel_path);
        let content = fs::read(&full_path)
            .map_err(|e| format!("读取文件失败 {}: {}", full_path.display(), e))?;

        let entry_header = format!("file={}\n", rel_path);
        hasher.update(entry_header.as_bytes());
        hasher.update(&content);
        hasher.update(b"\0");
    }

    let result = hasher.finalize();
    Ok(format!("{:x}", result))
}

fn collect_crate_files(root: &Path, dir: &Path, collected: &mut Vec<String>) -> Result<(), String> {
    let entries =
        fs::read_dir(dir).map_err(|e| format!("遍历目录失败 {}: {}", dir.display(), e))?;

    for entry in entries {
        let entry = entry.map_err(|e| format!("读取目录项失败: {}", e))?;
        let path = entry.path();
        if path.is_dir() {
            let dir_name = path.file_name().and_then(|n| n.to_str()).unwrap_or("");
            if dir_name != "target" && !dir_name.starts_with('.') {
                collect_crate_files(root, &path, collected)?;
            }
        } else if path.is_file() {
            let file_name = path.file_name().and_then(|n| n.to_str()).unwrap_or("");
            let is_matched =
                file_name.ends_with(".rs") || file_name == "Cargo.toml" || file_name == "build.rs";

            if is_matched {
                if let Ok(rel) = path.strip_prefix(root) {
                    let normalized = rel.to_string_lossy().replace('\\', "/");
                    collected.push(normalized);
                }
            }
        }
    }
    Ok(())
}

pub fn verify_artifacts_exist(out_dir: &Path, lib_name: &str, abis: &[String]) -> bool {
    for abi in abis {
        let so_path = out_dir.join(abi).join(lib_name);
        if let Ok(metadata) = fs::metadata(&so_path) {
            if !metadata.is_file() || metadata.len() == 0 {
                return false;
            }
        } else {
            return false;
        }
    }
    true
}

pub struct ToolchainInfo {
    pub ndk_dir: PathBuf,
    #[allow(dead_code)]
    pub sdk_dir: Option<PathBuf>,
}

pub fn resolve_toolchain(root: &Path) -> Result<ToolchainInfo, String> {
    // 自动追加 ~/.cargo/bin 到 PATH
    if let Ok(home) = env::var("HOME").or_else(|_| env::var("USERPROFILE")) {
        let cargo_bin = Path::new(&home).join(".cargo/bin");
        if cargo_bin.is_dir() {
            if let Some(path_var) = env::var_os("PATH") {
                let mut paths = env::split_paths(&path_var).collect::<Vec<_>>();
                if !paths.contains(&cargo_bin) {
                    paths.insert(0, cargo_bin);
                    if let Ok(new_path) = env::join_paths(paths) {
                        env::set_var("PATH", new_path);
                    }
                }
            }
        }
    }

    if which("cargo").is_none() {
        return Err("未找到 cargo，请先安装 Rust 工具链：https://rustup.rs".into());
    }

    if which("cargo-ndk").is_none() {
        return Err("未找到 cargo-ndk，请运行：cargo install cargo-ndk --locked".into());
    }

    // 解析 Android SDK 路径
    let mut sdk_dir = env::var("ANDROID_HOME")
        .or_else(|_| env::var("ANDROID_SDK_ROOT"))
        .ok()
        .map(PathBuf::from);

    if sdk_dir.is_none() {
        let local_prop = root.join("local.properties");
        if local_prop.is_file() {
            if let Ok(content) = fs::read_to_string(&local_prop) {
                for line in content.lines() {
                    let trimmed = line.trim();
                    if let Some(rest) = trimmed.strip_prefix("sdk.dir=") {
                        let path_str = rest
                            .replace(r"\\", r"\")
                            .replace(r"\:", ":")
                            .replace(r"\=", "=")
                            .trim()
                            .to_string();
                        if !path_str.is_empty() {
                            sdk_dir = Some(PathBuf::from(path_str));
                            break;
                        }
                    }
                }
            }
        }
    }

    // 解析 Android NDK
    let mut candidate_ndk: Option<PathBuf> = None;
    for var_name in &["ANDROID_NDK_HOME", "ANDROID_NDK_ROOT"] {
        if let Ok(val) = env::var(var_name) {
            let p = PathBuf::from(val);
            if p.join("source.properties").is_file() && p.join("toolchains").is_dir() {
                candidate_ndk = Some(p);
                break;
            }
        }
    }

    if candidate_ndk.is_none() {
        if let Some(ref sdk) = sdk_dir {
            let ndk_root = sdk.join("ndk");
            if ndk_root.is_dir() {
                if let Ok(entries) = fs::read_dir(&ndk_root) {
                    let mut versions: Vec<PathBuf> = entries
                        .filter_map(|e| e.ok())
                        .map(|e| e.path())
                        .filter(|p| {
                            p.join("source.properties").is_file() && p.join("toolchains").is_dir()
                        })
                        .collect();

                    versions.sort_by(|a, b| {
                        let aname = a.file_name().and_then(|n| n.to_str()).unwrap_or("");
                        let bname = b.file_name().and_then(|n| n.to_str()).unwrap_or("");
                        natord_cmp(aname, bname)
                    });

                    if let Some(latest) = versions.pop() {
                        candidate_ndk = Some(latest);
                    }
                }
            }
        }
    }

    let ndk_dir = candidate_ndk.ok_or_else(|| {
        "未找到可用的 Android NDK（需包含 source.properties 与 toolchains/）。\n   请设置 ANDROID_NDK_HOME 或通过 SDK Manager 安装 NDK。".to_string()
    })?;

    // 补充 SDK 内置 CMake
    if which("cmake").is_none() {
        if let Some(ref sdk) = sdk_dir {
            let cmake_root = sdk.join("cmake");
            if cmake_root.is_dir() {
                if let Ok(entries) = fs::read_dir(&cmake_root) {
                    let mut cmake_dirs: Vec<PathBuf> = entries
                        .filter_map(|e| e.ok())
                        .map(|e| e.path().join("bin"))
                        .filter(|p| p.is_dir())
                        .collect();
                    cmake_dirs.sort();
                    if let Some(cmake_bin) = cmake_dirs.pop() {
                        if let Some(path_var) = env::var_os("PATH") {
                            let mut paths = env::split_paths(&path_var).collect::<Vec<_>>();
                            paths.insert(0, cmake_bin);
                            if let Ok(new_path) = env::join_paths(paths) {
                                env::set_var("PATH", new_path);
                            }
                        }
                    }
                }
            }
        }
    }

    // macOS CommandLineTools 兜底
    if cfg!(target_os = "macos")
        && Path::new("/Library/Developer/CommandLineTools").is_dir()
        && env::var_os("DEVELOPER_DIR").is_none()
    {
        env::set_var("DEVELOPER_DIR", "/Library/Developer/CommandLineTools");
    }

    Ok(ToolchainInfo { ndk_dir, sdk_dir })
}

pub fn run_native_build(
    root: &Path,
    target: &NativeTarget,
    abis: &[String],
    force: bool,
    check_only: bool,
) -> Result<(), String> {
    let out_dir = root.join(target.out_dir);

    // 清理历史遗留在 src/ 下的旧 jniLibs 目录
    if let Some(parent) = out_dir
        .parent()
        .and_then(|p| p.parent())
        .and_then(|p| p.parent())
    {
        let legacy_dir = parent.join("src/androidMain/jniLibs");
        if legacy_dir.is_dir() {
            let _ = fs::remove_dir_all(&legacy_dir);
        }
    }

    let fingerprint = compute_fingerprint(root, target.crate_dir, target.platform_api, abis)?;
    let stamp_path = out_dir.join(".native-build.stamp");

    let recorded = if stamp_path.is_file() {
        fs::read_to_string(&stamp_path)
            .unwrap_or_default()
            .trim()
            .to_string()
    } else {
        String::new()
    };

    if !force && verify_artifacts_exist(&out_dir, target.lib_name, abis) && recorded == fingerprint
    {
        println!(
            "[native] ✓ {} 产物已是最新（命中源码指纹缓存）",
            target.crate_name
        );
        return Ok(());
    }

    if check_only {
        return Err(format!(
            "{} 原生库缺失或与当前 Rust 源码指纹不一致。\n   目录：{}\n   修复：运行 bash tools/jgate native",
            target.crate_name,
            out_dir.display()
        ));
    }

    let toolchain = resolve_toolchain(root)?;
    fs::create_dir_all(&out_dir)
        .map_err(|e| format!("创建输出目录失败 {}: {}", out_dir.display(), e))?;

    let mut cmd = Command::new("cargo");
    cmd.current_dir(root);
    cmd.arg("ndk");

    for abi in abis {
        cmd.arg("-t").arg(abi);
    }

    cmd.arg("--platform").arg(target.platform_api);
    cmd.arg("-o").arg(&out_dir);
    cmd.arg("build");
    cmd.arg("--release");
    cmd.arg("--locked");
    cmd.arg("-p").arg(target.crate_name);

    if cfg!(windows) {
        if let Ok(temp) = env::var("TEMP").or_else(|_| env::var("TMP")) {
            let tmp_target = PathBuf::from(temp).join("minibgm-cargo-target");
            let _ = fs::create_dir_all(&tmp_target);
            cmd.arg("--target-dir").arg(tmp_target);
        }
    }

    cmd.env("ANDROID_NDK_HOME", &toolchain.ndk_dir);
    cmd.env("ANDROID_NDK_ROOT", &toolchain.ndk_dir);

    let ndk_name = toolchain
        .ndk_dir
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("NDK");
    println!(
        "[native] 🔨 编译 {} ({}, API {}, NDK: {})...",
        target.crate_name,
        abis.join(" "),
        target.platform_api,
        ndk_name
    );

    let status = cmd
        .status()
        .map_err(|e| format!("执行 cargo ndk 失败: {}", e))?;

    if !status.success() {
        return Err(format!(
            "{} 编译失败（exit code: {:?}）",
            target.crate_name,
            status.code()
        ));
    }

    if !verify_artifacts_exist(&out_dir, target.lib_name, abis) {
        return Err(format!(
            "{} 编译完成但未找到预期的 {} 产物（{}）",
            target.crate_name,
            target.lib_name,
            out_dir.display()
        ));
    }

    fs::write(&stamp_path, format!("{}\n", fingerprint))
        .map_err(|e| format!("写入指纹戳文件失败 {}: {}", stamp_path.display(), e))?;

    println!(
        "[native] ✅ {} 产物已更新 -> {}",
        target.crate_name,
        out_dir.display()
    );
    Ok(())
}

fn which(bin_name: &str) -> Option<PathBuf> {
    let path_var = env::var_os("PATH")?;
    for p in env::split_paths(&path_var) {
        let exe = p.join(bin_name);
        if exe.is_file() {
            return Some(exe);
        }
        #[cfg(windows)]
        {
            let exe_with_ext = p.join(format!("{}.exe", bin_name));
            if exe_with_ext.is_file() {
                return Some(exe_with_ext);
            }
        }
    }
    None
}

// 简单自然版本字符串比对（如 25.1.8937393 vs 27.2.12479018）
fn natord_cmp(a: &str, b: &str) -> std::cmp::Ordering {
    let parse_parts = |s: &str| -> Vec<u64> {
        s.split(|c: char| !c.is_ascii_digit())
            .filter_map(|part| part.parse::<u64>().ok())
            .collect()
    };
    let a_parts = parse_parts(a);
    let b_parts = parse_parts(b);
    a_parts.cmp(&b_parts).then_with(|| a.cmp(b))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_natord_cmp() {
        assert_eq!(
            natord_cmp("25.1.8937393", "27.2.12479018"),
            std::cmp::Ordering::Less
        );
        assert_eq!(
            natord_cmp("27.2.12479018", "27.1.0"),
            std::cmp::Ordering::Greater
        );
        assert_eq!(natord_cmp("27.0.0", "27.0.0"), std::cmp::Ordering::Equal);
    }

    #[test]
    fn test_load_abis_parses_real_manifest() {
        let mut cur = std::env::current_dir().unwrap();
        while !cur.join("native/abis.txt").is_file() {
            if let Some(parent) = cur.parent() {
                cur = parent.to_path_buf();
            } else {
                return;
            }
        }

        let abis = load_abis(&cur).expect("应当成功解析 native/abis.txt");
        assert!(abis.contains(&"arm64-v8a".to_string()));
        assert!(abis.contains(&"x86_64".to_string()));
    }

    #[test]
    fn test_fingerprint_is_deterministic() {
        let mut cur = std::env::current_dir().unwrap();
        while !cur.join("native/abis.txt").is_file() {
            if let Some(parent) = cur.parent() {
                cur = parent.to_path_buf();
            } else {
                return;
            }
        }

        let abis = vec!["arm64-v8a".to_string(), "x86_64".to_string()];
        let fp1 = compute_fingerprint(&cur, "crates/minibgm-ech", "31", &abis).unwrap();
        let fp2 = compute_fingerprint(&cur, "crates/minibgm-ech", "31", &abis).unwrap();
        assert_eq!(fp1, fp2);
        assert_eq!(fp1.len(), 64);
    }
}
