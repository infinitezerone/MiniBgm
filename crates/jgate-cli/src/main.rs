use regex::Regex;
use serde::Serialize;
use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, ExitStatus};
use std::time::Instant;

mod native;

#[derive(Debug, Clone)]
struct CliOptions {
    force_all: bool,
    explicit_all: bool,
    fast_mode: bool,
    plan_only: bool,
    stack_mode: bool,
    quick_mode: bool,
    only_target: bool,
    modules: Vec<String>,
}

#[derive(Serialize)]
struct Diagnostic {
    status: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    stage: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    summary: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    remediation: Option<String>,
    timestamp: String,
}

fn utc_now_iso() -> String {
    let now = std::time::SystemTime::now();
    let duration = now
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default();
    let secs = duration.as_secs();
    // 简易格式化 UTC ISO 8601
    let days = secs / 86400;
    let rem_secs = secs % 86400;
    let hours = rem_secs / 3600;
    let mins = (rem_secs % 3600) / 60;
    let s = rem_secs % 60;
    format!(
        "{}T{:02}:{:02}:{:02}Z",
        format_epoch_days(days),
        hours,
        mins,
        s
    )
}

fn format_epoch_days(mut days: u64) -> String {
    // 粗略公历算法 (从 1970-01-01 开始)
    let mut year = 1970;
    loop {
        let leap = is_leap_year(year);
        let days_in_year = if leap { 366 } else { 365 };
        if days < days_in_year {
            break;
        }
        days -= days_in_year;
        year += 1;
    }
    let leap = is_leap_year(year);
    let month_days = [
        31,
        if leap { 29 } else { 28 },
        31,
        30,
        31,
        30,
        31,
        31,
        30,
        31,
        30,
        31,
    ];
    let mut month = 1;
    for &d in &month_days {
        if days < d {
            break;
        }
        days -= d;
        month += 1;
    }
    let day = days + 1;
    format!("{:04}-{:02}-{:02}", year, month, day)
}

fn is_leap_year(y: u64) -> bool {
    (y.is_multiple_of(4) && !y.is_multiple_of(100)) || y.is_multiple_of(400)
}

fn record_diagnostic(root: &Path, stage: &str, summary: &str, remediation: &str) {
    let dir = root.join("build/reports/jgate");
    let _ = fs::create_dir_all(&dir);
    let diag = Diagnostic {
        status: "FAILED".to_string(),
        stage: Some(stage.to_string()),
        summary: Some(summary.to_string()),
        remediation: Some(remediation.to_string()),
        timestamp: utc_now_iso(),
    };
    if let Ok(json) = serde_json::to_string_pretty(&diag) {
        let _ = fs::write(dir.join("diagnostic.json"), &json);
    }

    // 格式化输出供 Agent / 人类直观快速定位的自愈诊断卡 (Matt & Poteto Harness Pattern)
    eprintln!(
        "\n┌──────────────────────── 🚨 JGATE SELF-HEALING DIAGNOSTIC ────────────────────────┐"
    );
    eprintln!("│ 阶段: {:<66} │", stage);
    eprintln!("│ 摘要: {:<66} │", summary);
    eprintln!("│ 建议: {:<66} │", remediation);
    eprintln!(
        "│ 报告: build/reports/jgate/diagnostic.json                                         │"
    );
    eprintln!(
        "└──────────────────────────────────────────────────────────────────────────────────┘\n"
    );
}

fn clear_diagnostic(root: &Path) {
    let dir = root.join("build/reports/jgate");
    let _ = fs::create_dir_all(&dir);
    let diag = Diagnostic {
        status: "PASSED".to_string(),
        stage: None,
        summary: None,
        remediation: None,
        timestamp: utc_now_iso(),
    };
    if let Ok(json) = serde_json::to_string_pretty(&diag) {
        let _ = fs::write(dir.join("diagnostic.json"), json);
    }
}

fn find_repo_root() -> PathBuf {
    let mut curr = std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."));
    loop {
        if curr.join("settings.gradle.kts").exists() {
            return curr;
        }
        if let Some(parent) = curr.parent() {
            curr = parent.to_path_buf();
        } else {
            break;
        }
    }
    std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."))
}

fn parse_args() -> CliOptions {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let mut opts = CliOptions {
        force_all: false,
        explicit_all: false,
        fast_mode: false,
        plan_only: false,
        stack_mode: false,
        quick_mode: false,
        only_target: false,
        modules: Vec::new(),
    };

    for arg in args {
        match arg.as_str() {
            "-a" | "--all" => {
                opts.force_all = true;
                opts.explicit_all = true;
            }
            "-f" | "--fast" => opts.fast_mode = true,
            "-n" | "--plan" | "--dry-run" => opts.plan_only = true,
            "-s" | "--stack" => opts.stack_mode = true,
            "-o" | "--only" => opts.only_target = true,
            "quick" => opts.quick_mode = true,
            "-h" | "--help" => {
                print_usage();
                std::process::exit(0);
            }
            _ => {
                if arg.starts_with('-') {
                    eprintln!("[jgate] ❌ 未知选项：{}", arg);
                    print_usage();
                    std::process::exit(2);
                } else {
                    opts.modules.push(arg);
                }
            }
        }
    }
    opts
}

fn print_usage() {
    println!(
        r#"用法：jgate [选项] [模块...] 或 jgate [子命令]

选项：
  -s, --stack   验证当前本地变更栈（trunk()..@ 逐个提交格式与全栈改动汇总）
  -a, --all     强制验证全部模块（跨切面改动、PR 前）
  -f, --fast    仅为本机减负：跳过截图测试与触及模块的增量 lint（等价
                -Pminibgm.skipScreenshots=true）。本机绿 != CI 绿——提交前必须再跑一次不带 --fast 的
  -n, --plan    只打印本次验证目标与将要执行的 gradle 任务，不执行命令（别名 --dry-run）
  -o, --only    在 quick 模式下仅执行目标模块测试，跳过架构红线
  -h, --help    显示本帮助

子命令 / 模式：
  quick         快速自检模式（秒级执行架构红线，可附加模块微验证）
  native        原生库编译与指纹校验模式（编译 libminibgm_ech / libminibgm_torrent）

门禁做什么（默认模式）：
  Rust 门禁（仅触及 crates/ 时）→ spotlessApply → 架构红线
  → 触及模块测试 + :app:testDebugUnitTest + 触及 Android 模块 lintDebug
  → :app:assembleDebug → 结果真实性校验 → 结构化诊断

  目标推断：工作副本 @ → 最近提交 @-，按改动路径推导模块；触及
  :core:model/common/data/network/database/datastore/designsystem/navigation、
  build-logic 与根配置时自动提升为全量。
  纯 *.md / docs/ / .agents/ / .github/ / LICENSE 改动显式跳过装配门 :app:assembleDebug
  （白名单而非"未落到模块目录就跳"：native/、tools/、crates/ 仍照常跑装配门）。
  :app:testDebugUnitTest 承载 AppModuleTest 的 DI 契约红线，横跨全部模块，
  因此任何模块代码改动都会一并复跑。
  失败时打印自愈诊断卡，并写出 build/reports/jgate/diagnostic.json。

与提交的关系：
  tools/jcommit（Windows: .\tools\jcommit.ps1 -m "feat: ..."）强制「先验后提」：
  先跑本门禁，绿了才 jj commit。日常提交不跑 release。

示例：
  jgate                          # 按工作副本/最近提交自动推断门禁
  jgate --stack                  # 全栈审查（逐 commit 格式与全栈改动集成门禁）
  jgate --fast                   # 跳过截图测试的轻量门禁
  jgate quick                    # 秒级架构红线自查（代替 quick-check）
  jgate quick core/network       # 红线 + network 模块单测微验证
  jgate native                   # 编译/更新全部 Rust 原生库（支持指纹增量短路）
  jgate native --check           # 校验原生库产物是否齐全且与源码一致
"#
    );
}

fn handle_native_mode(root: &Path, args: &[String]) {
    let mut force = false;
    let mut check_only = false;
    let mut targets = Vec::new();

    for arg in args {
        match arg.as_str() {
            "-f" | "--force" => force = true,
            "-c" | "--check" => check_only = true,
            "-h" | "--help" => {
                println!(
                    r#"用法：jgate native [选项] [ech|torrent...]

选项：
  -f, --force   忽略源码指纹缓存，强制重新调用 cargo-ndk 编译
  -c, --check   仅校验现有 .so 产物与当前源码指纹是否一致（不编译）
  -h, --help    显示本帮助

不指定目标时默认处理全部原生 crate（minibgm-ech 与 minibgm-torrent）。
目标 ABI 集合来自 native/abis.txt（唯一真源）。
"#
                );
                return;
            }
            "ech" | "minibgm-ech" => targets.push(native::TARGET_ECH),
            "torrent" | "minibgm-torrent" => targets.push(native::TARGET_TORRENT),
            _ => {
                eprintln!("[jgate native] ❌ 未知参数：{}", arg);
                std::process::exit(2);
            }
        }
    }

    if targets.is_empty() {
        targets.push(native::TARGET_ECH);
        targets.push(native::TARGET_TORRENT);
    }

    let abis = match native::load_abis(root) {
        Ok(a) => a,
        Err(e) => {
            eprintln!("[jgate native] ❌ {}", e);
            std::process::exit(1);
        }
    };

    for target in targets {
        if let Err(e) = native::run_native_build(root, &target, &abis, force, check_only) {
            eprintln!("[jgate native] ❌ {}", e);
            std::process::exit(1);
        }
    }
}

fn run_command_capture(cmd: &str, args: &[&str]) -> Result<String, String> {
    let output = Command::new(cmd)
        .args(args)
        .output()
        .map_err(|e| format!("执行 {} 失败: {}", cmd, e))?;
    if output.status.success() {
        Ok(String::from_utf8_lossy(&output.stdout).to_string())
    } else {
        Err(String::from_utf8_lossy(&output.stderr).to_string())
    }
}

fn normalize_module(m: &str) -> String {
    m.trim_start_matches(':').replace(':', "/")
}

fn all_modules(root: &Path) -> Vec<String> {
    let settings = root.join("settings.gradle.kts");
    if let Ok(content) = fs::read_to_string(settings) {
        let re = Regex::new(r#"include\(":(.*?)"\)"#).unwrap();
        re.captures_iter(&content)
            .map(|c| c[1].replace(':', "/"))
            .collect()
    } else {
        Vec::new()
    }
}

fn check_git_hooks() {
    if let Ok(hooks_path) = run_command_capture("git", &["config", "core.hooksPath"]) {
        if hooks_path.trim() != "tools/githooks" {
            let _ = Command::new("git")
                .args(["config", "core.hooksPath", "tools/githooks"])
                .status();
            println!("[jgate] 已自动接线 core.hooksPath → tools/githooks");
        }
    }
}

fn main() {
    let root = find_repo_root();
    std::env::set_current_dir(&root).expect("无法切换到工程根目录");

    let raw_args: Vec<String> = std::env::args().skip(1).collect();
    if !raw_args.is_empty() && raw_args[0] == "native" {
        handle_native_mode(&root, &raw_args[1..]);
        return;
    }

    let start_time = Instant::now();
    let opts = parse_args();

    // 自愈接线 git 钩子
    check_git_hooks();

    // ---- Quick 模式（极速微验证）---------------------------------------------
    if opts.quick_mode {
        handle_quick_mode(&root, &opts);
        return;
    }

    // ---- 常规门禁模式 -------------------------------------------------------
    let commit_re = Regex::new(
        r"^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([^)]*\))?!?: .+|^Merge ",
    )
    .unwrap();

    #[allow(unused_assignments)]
    let mut target_desc = String::new();
    #[allow(unused_assignments)]
    let mut diff_summary = String::new();

    if opts.force_all {
        target_desc = "全量（--all）".to_string();
    } else if opts.stack_mode {
        // 校验栈内每一个 revision 的提交描述
        if let Ok(revs) = run_command_capture(
            "jj",
            &[
                "--no-pager",
                "log",
                "-r",
                "trunk()..@",
                "-T",
                r#"change_id.short() ++ " " ++ commit_id.short() ++ " " ++ description.first_line() ++ "\n""#,
                "--no-graph",
            ],
        ) {
            for line in revs.lines().filter(|l| !l.trim().is_empty()) {
                let parts: Vec<&str> = line.splitn(3, ' ').collect();
                if parts.len() >= 3 {
                    let cid = parts[0];
                    let cshort = parts[1];
                    let cmsg = parts[2].trim();
                    if !cmsg.is_empty() && !commit_re.is_match(cmsg) {
                        eprintln!(
                            "[jgate] ❌ 栈内变更 {} ({}) 提交描述不符合 Conventional Commits 规范：",
                            cid, cshort
                        );
                        eprintln!("[jgate]    当前描述: \"{}\"", cmsg);
                        eprintln!("[jgate]    要求格式: <type>(<scope>): <subject>");
                        eprintln!(
                            "[jgate]    修法：jj describe {} -m \"<type>(<scope>): <summary>\"",
                            cid
                        );
                        record_diagnostic(
                            &root,
                            "COMMIT_LINT",
                            &format!("变更 {} ({}) 描述 \"{}\" 不合规", cid, cshort, cmsg),
                            &format!(
                                "使用 jj describe {} -m \"<type>(<scope>): <summary>\" 修复",
                                cid
                            ),
                        );
                        std::process::exit(1);
                    }
                }
            }
        }

        diff_summary = run_command_capture(
            "jj",
            &[
                "--no-pager",
                "diff",
                "--from",
                "trunk()",
                "--to",
                "@",
                "--summary",
            ],
        )
        .unwrap_or_default();

        if diff_summary.trim().is_empty() {
            eprintln!("[jgate] ❌ 当前本地变更栈（trunk()..@）没有任何改动");
            record_diagnostic(
                &root,
                "TARGET_SELECTION",
                "当前变更栈无改动",
                "使用 jgate --all 或显式指定模块",
            );
            std::process::exit(1);
        }
        target_desc = "本地变更栈（trunk()..@）".to_string();
    } else if !opts.modules.is_empty() {
        target_desc = "显式指定模块".to_string();
    } else {
        // 工作副本优先；干净时退到 @-
        let work_diff =
            run_command_capture("jj", &["--no-pager", "diff", "--summary"]).unwrap_or_default();
        if !work_diff.trim().is_empty() {
            diff_summary = work_diff;
            target_desc = "工作副本 @".to_string();
            // 校验 @ 说明（若有）
            check_single_commit(&root, "@", &commit_re);
        } else {
            let parent_diff =
                run_command_capture("jj", &["--no-pager", "diff", "--summary", "-r", "@-"])
                    .unwrap_or_default();
            if !parent_diff.trim().is_empty() {
                diff_summary = parent_diff;
                target_desc = "最近一次提交 @-（工作副本无改动）".to_string();
                check_single_commit(&root, "@-", &commit_re);
            } else {
                eprintln!("[jgate] ❌ 工作副本与最近一次提交都没有改动，没有可验证的内容");
                eprintln!("[jgate]    强制全量：jgate --all      指定模块：jgate feature/user");
                record_diagnostic(
                    &root,
                    "TARGET_SELECTION",
                    "无改动可验证",
                    "使用 jgate --all 或显式指定模块",
                );
                std::process::exit(1);
            }
        }
    }

    // 提取改动路径（安全解析带空格、重命名）
    let paths = extract_diff_paths(&diff_summary);

    // 跨切面提级全量判定
    let cross_re = Regex::new(
        r"^(build-logic/|gradle/|gradle\.properties$|settings\.gradle\.kts$|[^/]*\.gradle\.kts$|core/(model|common|data|network|database|datastore|designsystem|navigation)/)",
    ).unwrap();

    let mut force_all = opts.force_all;
    if !force_all && !paths.is_empty() && paths.iter().any(|p| cross_re.is_match(p)) {
        force_all = true;
        target_desc.push_str(" ＋ 跨切面/底层核心改动 → 提升为全量");
    }

    // Rust 门禁触发判定
    let need_rust = opts.explicit_all
        || paths
            .iter()
            .any(|p| p.starts_with("crates/") || p == "Cargo.toml" || p == "Cargo.lock");

    // 纯文档跳过装配门
    let doc_only_re =
        Regex::new(r"^(docs/|\.agents/|\.github/|LICENSE$|\.gitignore$|\.gitattributes$|.*\.md$)")
            .unwrap();
    let docs_only =
        !force_all && !paths.is_empty() && paths.iter().all(|p| doc_only_re.is_match(p));

    // 解析触及模块
    let mut modules: Vec<String> = Vec::new();
    if force_all {
        modules = all_modules(&root);
    } else if !opts.modules.is_empty() {
        for m in &opts.modules {
            modules.push(normalize_module(m));
        }
    } else {
        let mut raw_set = HashSet::new();
        for p in &paths {
            if p.starts_with("app/") || p == "app" {
                raw_set.insert("app".to_string());
            } else if p.starts_with("core/") || p.starts_with("feature/") || p.starts_with("sync/")
            {
                let parts: Vec<&str> = p.split('/').collect();
                if parts.len() >= 2 {
                    raw_set.insert(format!("{}/{}", parts[0], parts[1]));
                }
            }
        }
        modules = raw_set.into_iter().collect();
        modules.sort();
    }

    // 构建任务列表
    let mut spotless_targets = Vec::new();
    let mut test_tasks = Vec::new();

    for m in &modules {
        let build_path = root.join(m).join("build.gradle.kts");
        if build_path.exists() {
            let task_prefix = format!(":{}", m.replace('/', ":"));
            spotless_targets.push(format!("{}:spotlessApply", task_prefix));
            let content = fs::read_to_string(&build_path).unwrap_or_default();
            if content.contains("minibgm.kmp") {
                test_tasks.push(format!("{}:testAndroid", task_prefix));
            } else {
                test_tasks.push(format!("{}:testDebugUnitTest", task_prefix));
            }
        }
    }

    let mut gradle_tasks = Vec::new();
    gradle_tasks.push(":core:testing:testAndroid".to_string());
    for t in test_tasks {
        if !gradle_tasks.contains(&t) {
            gradle_tasks.push(t);
        }
    }
    // app 模块住着 AppModuleTest（硬红线 20：真实 Koin 容器解析 platformDataModule 与各
    // feature ViewModel）。它横跨全部模块，因此只要有任何模块代码改动就必须复跑；
    // 只按"改动路径落在哪个模块"推导会漏掉它——feature/user 改动曾让 DownloadViewModel
    // 构造期扫盘的回归静默通过门禁（该提交的门禁只跑了 :feature:user:testDebugUnitTest）。
    if !modules.is_empty() && !gradle_tasks.iter().any(|t| t == ":app:testDebugUnitTest") {
        gradle_tasks.push(":app:testDebugUnitTest".to_string());
    }
    if !docs_only {
        gradle_tasks.push(":app:assembleDebug".to_string());
    }
    if opts.explicit_all {
        gradle_tasks.push(":app:assembleRelease".to_string());
        gradle_tasks.push(":core:testing:crapCheck".to_string());
    }

    // 触及 Android 模块的增量 lint：此前 lint 只在 CI 跑，本地门禁绿灯也可能带着新增
    // lint 违规直接推送。只对声明了 Android 应用/库约定插件的模块附加 lintDebug——
    // 实证 KMP 库模块（minibgm.kmp.library）没有 lintDebug 任务，误加会让整个门禁失败；
    // --fast 本机减负时跳过。
    if !docs_only && !opts.fast_mode {
        for m in &modules {
            let build_file = root.join(m).join("build.gradle.kts");
            let content = fs::read_to_string(&build_file).unwrap_or_default();
            if content.contains("minibgm.android") {
                gradle_tasks.push(format!(":{}:lintDebug", m.replace('/', ":")));
            }
        }
    }

    println!("[jgate] 验证目标：{}", target_desc);
    if modules.is_empty() {
        println!("[jgate] ⚠️ 改动不触及任何 Gradle 模块 → 跳过 spotless 与模块测试，只跑架构红线");
    }
    if docs_only {
        println!(
            "[jgate] ⚠️ 纯文档/元数据改动 → 装配门 :app:assembleDebug 一并跳过（架构红线仍然执行）"
        );
    }
    if opts.fast_mode {
        println!("[jgate] ⚠️ --fast：本轮跳过截图测试（Roborazzi 视觉回归未覆盖）");
    }
    println!("[jgate] 模块 {} 个：{}", modules.len(), modules.join(" "));
    println!("[jgate] 将执行：{}", gradle_tasks.join(" "));

    if opts.plan_only {
        println!("[jgate] --plan：以上仅为计划，未执行任何命令");
        return;
    }

    // ---- 1. Rust 门禁 -------------------------------------------------------
    println!("[jgate] 1/4 Rust 门禁（crates/）");
    if !need_rust {
        println!("[jgate]   ↳ 改动未触及 crates/，跳过");
    } else {
        run_rust_gate(&root);
    }

    // ---- 2. Spotless --------------------------------------------------------
    println!("[jgate] 2/4 spotless");
    if diff_summary.trim().is_empty() {
        println!("[jgate] 工作副本无改动，跳过 spotless");
    } else if !spotless_targets.is_empty() {
        let mut grad_args = spotless_targets.clone();
        grad_args.push("-q".to_string());
        run_gradle(&root, &grad_args, opts.fast_mode);
    }

    // ---- 3. 架构红线 + 模块测试 + 装配门 -------------------------------------
    if docs_only {
        println!("[jgate] 3/4 架构红线 + 触及模块测试（纯文档改动：装配门已跳过）");
    } else {
        println!("[jgate] 3/4 架构红线 + 触及模块测试 + 装配门");
    }

    // 原生库构建就绪保障
    println!("[jgate] 校验/编译预构建原生库...");
    let native_script = if cfg!(windows) {
        root.join("tools/build-native.ps1")
    } else {
        root.join("tools/build-native")
    };
    let native_status = if cfg!(windows) {
        Command::new("powershell")
            .arg("-File")
            .arg(native_script)
            .status()
    } else {
        Command::new("bash").arg(native_script).status()
    };
    if !native_status.map(|s| s.success()).unwrap_or(false) {
        eprintln!("[jgate] ❌ 原生库构建/校验未通过");
        record_diagnostic(
            &root,
            "NATIVE_LIBS",
            "原生库构建或指纹校验失败",
            "检查 tools/build-native 与 cargo-ndk 配置",
        );
        std::process::exit(1);
    }

    let stamp_time = std::time::SystemTime::now();
    let status = run_gradle(&root, &gradle_tasks, opts.fast_mode);
    if !status.success() {
        record_diagnostic(
            &root,
            "GRADLE_BUILD",
            "Gradle 任务执行失败",
            "查看 Gradle 错误日志并修复代码或测试",
        );
        std::process::exit(1);
    }

    // ---- 4. 测试结果真实性校验 -----------------------------------------------
    println!("[jgate] 4/4 测试结果真实性校验");
    verify_test_results(&root, &modules, stamp_time, opts.fast_mode);

    clear_diagnostic(&root);
    let elapsed = start_time.elapsed().as_secs();
    println!(
        "[jgate] ✅ 验证门通过（耗时 {}s，{} 个模块测试通过）",
        elapsed,
        modules.len()
    );
}

fn check_single_commit(root: &Path, rev: &str, re: &Regex) {
    if let Ok(msg) = run_command_capture(
        "jj",
        &[
            "--no-pager",
            "log",
            "-r",
            rev,
            "-T",
            "description",
            "--no-graph",
        ],
    ) {
        let first_line = msg.lines().next().unwrap_or("").trim();
        if !first_line.is_empty() && !re.is_match(first_line) {
            eprintln!("[jgate] ❌ 提交描述不符合 Conventional Commits 规范：");
            eprintln!("[jgate]    当前描述: \"{}\"", first_line);
            eprintln!("[jgate]    要求格式: <type>(<scope>): <subject>");
            eprintln!("[jgate]    修法：jj describe -m \"<type>(<scope>): <summary>\"");
            record_diagnostic(
                root,
                "COMMIT_LINT",
                &format!("提交描述 \"{}\" 不符合规范", first_line),
                "使用 jj describe -m \"<type>(<scope>): <summary>\" 修复",
            );
            std::process::exit(1);
        }
    }
}

fn run_rust_gate(root: &Path) {
    println!("[jgate] 运行 Rust 门禁 (fmt, clippy, test)...");
    let fmt_status = Command::new("cargo")
        .args(["fmt", "--all", "--check"])
        .status();
    if !fmt_status.map(|s| s.success()).unwrap_or(false) {
        eprintln!("[jgate] ❌ cargo fmt 未通过（修法：cargo fmt --all）");
        record_diagnostic(
            root,
            "RUST_GATE",
            "cargo fmt 格式检查失败",
            "运行 cargo fmt --all 修复格式",
        );
        std::process::exit(1);
    }

    let clippy_status = Command::new("cargo")
        .args([
            "clippy",
            "--locked",
            "--workspace",
            "--all-targets",
            "--",
            "-D",
            "warnings",
        ])
        .status();
    if !clippy_status.map(|s| s.success()).unwrap_or(false) {
        eprintln!("[jgate] ❌ cargo clippy 未通过（-D warnings）");
        record_diagnostic(
            root,
            "RUST_GATE",
            "cargo clippy 检查存在 warning 违规",
            "根据日志修复 clippy 警告",
        );
        std::process::exit(1);
    }

    let test_status = Command::new("cargo")
        .args(["test", "--locked", "--workspace"])
        .status();
    if !test_status.map(|s| s.success()).unwrap_or(false) {
        eprintln!("[jgate] ❌ cargo test 未通过");
        record_diagnostic(
            root,
            "RUST_GATE",
            "cargo test 单元测试失败",
            "检查并修复 Rust 测试",
        );
        std::process::exit(1);
    }
}

fn run_gradle(root: &Path, tasks: &[String], fast_mode: bool) -> ExitStatus {
    let mut args: Vec<String> = tasks.to_vec();
    if fast_mode {
        args.push("-Pminibgm.skipScreenshots=true".to_string());
    }
    args.push("--console=plain".to_string());

    println!("[jgate] gradle {}", args.join(" "));
    let mut cmd = if cfg!(windows) {
        let mut c = Command::new("cmd.exe");
        c.arg("/c").arg(root.join("gradlew.bat"));
        c
    } else {
        Command::new(root.join("gradlew"))
    };
    cmd.args(&args).status().expect("无法启动 gradlew")
}

fn verify_test_results(
    root: &Path,
    modules: &[String],
    stamp: std::time::SystemTime,
    fast_mode: bool,
) {
    let tests_re = Regex::new(r#"tests="([0-9]+)""#).unwrap();
    let failures_re = Regex::new(r#"failures="([0-9]+)""#).unwrap();
    let errors_re = Regex::new(r#"errors="([0-9]+)""#).unwrap();

    let mut checked_total = 0;
    let mut has_failure = false;

    for m in modules {
        let m_dir = root.join(m);
        if !m_dir.join("build.gradle.kts").exists() {
            continue;
        }

        let mut new_xmls = 0;
        let test_results_dir = m_dir.join("build/test-results");
        if test_results_dir.exists() {
            for entry in walkdir(&test_results_dir) {
                if let Some(file_name) = entry.file_name().and_then(|s| s.to_str()) {
                    if file_name.starts_with("TEST-") && file_name.ends_with(".xml") {
                        if let Ok(meta) = entry.metadata() {
                            if let Ok(mtime) = meta.modified() {
                                if mtime >= stamp {
                                    new_xmls += 1;
                                    checked_total += 1;
                                    if let Ok(content) = fs::read_to_string(&entry) {
                                        let tests: u64 = tests_re
                                            .captures(&content)
                                            .and_then(|c| c[1].parse().ok())
                                            .unwrap_or(0);
                                        let failures: u64 = failures_re
                                            .captures(&content)
                                            .and_then(|c| c[1].parse().ok())
                                            .unwrap_or(0);
                                        let errors: u64 = errors_re
                                            .captures(&content)
                                            .and_then(|c| c[1].parse().ok())
                                            .unwrap_or(0);

                                        if failures > 0 || errors > 0 {
                                            eprintln!(
                                                "[jgate] ❌ {}: failures={} errors={}",
                                                entry.display(),
                                                failures,
                                                errors
                                            );
                                            has_failure = true;
                                        } else if tests == 0 {
                                            eprintln!("[jgate] ❌ {}: tests=0", entry.display());
                                            has_failure = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // NO-SOURCE 防假绿闭环
        if new_xmls == 0 {
            let total_xmls = count_xmls(&test_results_dir);
            let src_dir = m_dir.join("src");
            let test_srcs = count_test_sources(&src_dir, fast_mode);
            if total_xmls == 0 && test_srcs > 0 {
                eprintln!(
                    "[jgate] ❌ {}：发现 {} 个测试源码文件，但从未生成测试结果 XML（任务 NO-SOURCE）",
                    m, test_srcs
                );
                record_diagnostic(
                    root,
                    "FALSE_GREEN",
                    &format!("{} 存在测试源码但未生成 XML 结果", m),
                    "检查测试源集与 Gradle 任务配置",
                );
                std::process::exit(1);
            }
        }
    }

    if has_failure {
        record_diagnostic(
            root,
            "TEST_FAILURE",
            "单元测试断言失败",
            "修复失败的测试用例",
        );
        std::process::exit(1);
    }

    if checked_total == 0 {
        println!("[jgate] ℹ️ 测试任务无新增结果文件——任务均 UP-TO-DATE 并沿用上次结果");
    }
}

fn walkdir(dir: &Path) -> Vec<PathBuf> {
    let mut files = Vec::new();
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() {
                files.extend(walkdir(&p));
            } else {
                files.push(p);
            }
        }
    }
    files
}

fn count_xmls(dir: &Path) -> usize {
    if !dir.exists() {
        return 0;
    }
    walkdir(dir)
        .iter()
        .filter(|p| {
            p.file_name()
                .and_then(|s| s.to_str())
                .map(|s| s.starts_with("TEST-") && s.ends_with(".xml"))
                .unwrap_or(false)
        })
        .count()
}

fn count_test_sources(src_dir: &Path, fast_mode: bool) -> usize {
    if !src_dir.exists() {
        return 0;
    }
    walkdir(src_dir)
        .iter()
        .filter(|p| {
            if let Some(s) = p.file_name().and_then(|s| s.to_str()) {
                if s.ends_with("Test.kt") || s.ends_with("Tests.kt") {
                    if fast_mode && s.ends_with("ScreenshotTest.kt") {
                        return false;
                    }
                    return true;
                }
            }
            false
        })
        .count()
}

fn handle_quick_mode(root: &Path, opts: &CliOptions) {
    println!("[jgate] 🔍 启动快速自检/微验证...");
    let mut tasks = Vec::new();

    if !opts.only_target {
        tasks.push(":core:testing:testAndroidHostTest".to_string());
    }

    for raw in &opts.modules {
        let norm = normalize_module(raw);
        let build_file = root.join(&norm).join("build.gradle.kts");
        if build_file.exists() {
            let task_prefix = format!(":{}", norm.replace('/', ":"));
            let content = fs::read_to_string(&build_file).unwrap_or_default();
            if content.contains("minibgm.kmp") {
                tasks.push(format!("{}:testAndroid", task_prefix));
            } else {
                tasks.push(format!("{}:testDebugUnitTest", task_prefix));
            }
        } else {
            eprintln!("[jgate] ⚠️ 未找到模块构建文件：{}", build_file.display());
        }
    }

    if tasks.is_empty() {
        eprintln!("[jgate] ❌ 无任务可执行");
        std::process::exit(1);
    }

    let status = run_gradle(root, &tasks, opts.fast_mode);
    if status.success() {
        println!("[jgate] ✅ 快速验证全绿！");
    } else {
        eprintln!("[jgate] ❌ 快速验证未通过");
        std::process::exit(1);
    }
}

fn extract_diff_paths(diff_summary: &str) -> Vec<String> {
    let mut paths = Vec::new();
    for line in diff_summary.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        let after_status = if trimmed.len() > 2 && trimmed.chars().nth(1) == Some(' ') {
            &trimmed[2..]
        } else {
            trimmed
        };
        let final_path = if let Some(idx) = after_status.find(" -> ") {
            &after_status[idx + 4..]
        } else {
            after_status
        };
        paths.push(final_path.replace('\\', "/"));
    }
    paths
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_conventional_commits_regex() {
        let re = Regex::new(
            r"^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([^)]*\))?!?: .+|^Merge ",
        )
        .unwrap();

        assert!(re.is_match("feat(core:ai): add prompt cache"));
        assert!(re.is_match("fix: resolve null pointer in player"));
        assert!(re.is_match("refactor(core:database)!: drop migration table"));
        assert!(re.is_match("docs: update README"));
        assert!(re.is_match("Merge branch 'main' of github.com"));

        assert!(!re.is_match("update code"));
        assert!(!re.is_match("feat:missing space"));
        assert!(!re.is_match("random message without conventional type"));
    }

    #[test]
    fn test_extract_diff_paths_handles_spaces_and_renames() {
        let summary = "\
M regular/file.kt
A feature/user/assets/user avatar banner.png
R old/path/image with space.png -> new/path/renamed image.png
D deleted/file.txt
";
        let paths = extract_diff_paths(summary);
        assert_eq!(
            paths,
            vec![
                "regular/file.kt",
                "feature/user/assets/user avatar banner.png",
                "new/path/renamed image.png",
                "deleted/file.txt",
            ]
        );
    }

    #[test]
    fn test_is_leap_year() {
        assert!(is_leap_year(2000));
        assert!(is_leap_year(2024));
        assert!(!is_leap_year(1900));
        assert!(!is_leap_year(2023));
    }

    #[test]
    fn test_normalize_module() {
        assert_eq!(normalize_module(":core:data"), "core/data");
        assert_eq!(normalize_module("feature:search"), "feature/search");
        assert_eq!(normalize_module("app"), "app");
    }
}
