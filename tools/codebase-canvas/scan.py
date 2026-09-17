#!/usr/bin/env python3
"""
Codebase State Canvas —— 静态代码库扫描器（零依赖，stdlib only）

把一个 Gradle 多模块工程的「结构 / 变更 / 证据」三层事实抽成一份画布数据：

  结构层  settings.gradle.kts 的模块清单 + 每个模块 build.gradle.kts 的显式依赖
          + build-logic 里 convention plugin 注入的*隐式*依赖（同样带 file:line 证据）
          + Kotlin 顶层声明（class / interface / object / fun / typealias）及真实行号
  变更层  每模块提交数、最后一次变更、工作区未提交文件
  证据层  测试源集、测试文件数、Gradle build/test-results 里真实跑过的用例数与失败数
          + 架构红线检查（对齐 AGENTS.md 中由 ArchitectureRulesTest 强制的规则集）

所有结论都带 evidence 字段（file:line / git hash）。扫描器自己不做推断。

用法:
    python3 scan.py [项目根目录] [-o 输出目录]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from datetime import datetime, timezone

# --------------------------------------------------------------------------
# 基础工具
# --------------------------------------------------------------------------

SKIP_DIRS = {"build", ".git", ".gradle", ".idea", "node_modules", ".workbuddy"}
MAIN_SETS = {"main", "commonMain", "androidMain", "iosMain", "jvmMain"}
TEST_SETS = {
    "test",
    "commonTest",
    "androidTest",
    "androidHostTest",
    "androidUnitTest",  # 历史误命名，AGENTS.md 规则 4 记录了这次事故
    "androidInstrumentedTest",
    "iosTest",
    "jvmTest",
}


def sh(args: list[str], cwd: str) -> str:
    try:
        out = subprocess.run(
            args, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True
        )
        return out.stdout
    except Exception:
        return ""


def read_text(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except OSError:
        return ""


def rel(root: str, path: str) -> str:
    return os.path.relpath(path, root).replace(os.sep, "/")


# --------------------------------------------------------------------------
# 1. settings.gradle.kts —— 模块清单
# --------------------------------------------------------------------------


def parse_settings(root: str) -> tuple[str, list[str]]:
    text = read_text(os.path.join(root, "settings.gradle.kts"))
    name = re.search(r'rootProject\.name\s*=\s*"([^"]+)"', text)
    includes = re.findall(r'include\s*\(\s*"([^"]+)"\s*\)', text)
    return (name.group(1) if name else os.path.basename(root)), includes


# --------------------------------------------------------------------------
# 2. build-logic —— convention plugin 的隐式依赖注入
# --------------------------------------------------------------------------


def parse_convention_plugins(root: str) -> dict[str, dict]:
    """返回 {pluginId: {cls, applies: [pluginId], injected: [{coord, declaration, evidence}]}}"""
    base = os.path.join(root, "build-logic", "convention")
    registry_path = os.path.join(base, "build.gradle.kts")
    registry = read_text(registry_path)

    id_to_cls: dict[str, str] = {}
    for m in re.finditer(
        r'id\s*=\s*"([^"]+)"\s*\n\s*implementationClass\s*=\s*"([^"]+)"', registry
    ):
        id_to_cls[m.group(1)] = m.group(2)

    src_root = os.path.join(base, "src", "main", "kotlin")
    cls_to_file: dict[str, str] = {}
    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(".kt"):
                cls_to_file[fn[: -len(".kt")]] = os.path.join(dirpath, fn)

    plugins: dict[str, dict] = {}
    for pid, cls in id_to_cls.items():
        simple = cls.rsplit(".", 1)[-1]
        path = cls_to_file.get(simple)
        entry = {"id": pid, "cls": cls, "applies": [], "injected": [], "source": None}
        if path:
            entry["source"] = rel(root, path)
            text = read_text(path)
            for i, line in enumerate(text.splitlines(), 1):
                stripped = line.strip()
                if stripped.startswith("//"):
                    continue
                m = re.search(r'apply\(\s*"([^"]+)"\s*\)', line)
                if m:
                    entry["applies"].append(m.group(1))
                m = re.search(
                    r'add\(\s*"([^"]+)"\s*,\s*project\(\s*"([^"]+)"\s*\)\s*\)', line
                )
                if m:
                    entry["injected"].append(
                        {
                            "declaration": m.group(1),
                            "coord": m.group(2),
                            "evidence": f'{entry["source"]}:{i}',
                        }
                    )
        plugins[pid] = entry

    # 递归展开：android.feature 会 apply android.library + android.library.compose，后者的注入也要算上
    def expand(pid: str, seen: set[str]) -> list[dict]:
        if pid in seen or pid not in plugins:
            return []
        seen.add(pid)
        acc = list(plugins[pid]["injected"])
        for child in plugins[pid]["applies"]:
            if child in plugins:
                acc.extend(expand(child, seen))
        for child in plugins[pid]["applies"]:
            plugins[pid].setdefault("applied_by_chain", []).append(child)
        return acc

    for pid in plugins:
        plugins[pid]["effective"] = expand(pid, set())

    # 传递性 applied set，供模块解析时判断「这个模块属于哪一类」
    def chain(pid: str, seen: set[str]) -> list[str]:
        if pid in seen or pid not in plugins:
            return []
        seen.add(pid)
        acc = [pid]
        for child in plugins[pid]["applies"]:
            acc.extend(chain(child, seen))
        return acc

    for pid in plugins:
        plugins[pid]["chain"] = chain(pid, set())

    return plugins


# --------------------------------------------------------------------------
# 3. 模块解析
# --------------------------------------------------------------------------


def plugin_alias_ids(build_text: str) -> list[str]:
    ids = re.findall(r"alias\(\s*libs\.plugins\.([\w.]+)\s*\)", build_text)
    ids = [".".join(p.split(".")) for p in ids]
    ids += re.findall(r'\bid\(\s*"([a-z][\w.]*\.[\w.]+)"\s*\)', build_text)
    return ids


EDGE_RE = re.compile(
    r'^\s*(?P<decl>implementation|api|compileOnly|runtimeOnly|testImplementation|'
    r'androidTestImplementation|debugImplementation|ksp)\s*\(\s*project\(\s*"(?P<coord>[^"]+)"\s*\)\s*\)'
)
SS_OPEN_RE = re.compile(r"^\s*([A-Za-z_]\w*)\.dependencies\s*\{")
# 测试源集的命名线索：token 以大写 Test 结尾（commonTest / androidHostTest / jvmTest），
# 或独立的 test 块。刻意不用「包含 test 子串」——latest / unitTests / testOptions 都会误命中。
TEST_CTX_RE = re.compile(r"\b(?:[A-Za-z_]\w*Test|test)\b")


def extract_edges(text: str, build_rel: str, coord: str) -> list[dict]:
    """抽显式 project(...) 依赖，并判定它落在哪个源集作用域。

    KMP / AGP-KMP 模块把依赖写在嵌套块里，同一行 `implementation(project(...))`
    在不同块里是「生产依赖」还是「测试依赖」完全不同：

        kotlin { sourceSets { commonMain.dependencies { implementation(project(":x")) } } }
        kotlin { sourceSets { matching { it.name == "androidHostTest" }.configureEach {
            dependencies { implementation(project(":core:testing")) } } } }

    不区分作用域会把测试依赖当成生产依赖 —— 既虚增产物依赖面，又会凭空造出依赖环
    （:core:testing 反过来 api 依赖 :core:data）。这里用「块头栈」判定：任何一层
    块头里出现源集名/Test 关键字，这条边就是测试作用域。
    """
    edges: list[dict] = []
    frames: list[str] = []  # 块头栈：每个未闭合的 { 对应的块头文本
    in_block_comment = False

    for i, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        line_is_comment = (
            in_block_comment or stripped.startswith("//") or stripped.startswith("*")
        )
        if stripped.startswith("/*") and "*/" not in stripped:
            in_block_comment = True

        if not line_is_comment:
            m = EDGE_RE.match(line)
            if m:
                decl = m.group("decl")
                ctx = " ".join(frames)
                ss_name = ""
                m_ss_prefix = re.match(r"\s*([A-Za-z_]\w*)\.dependencies", line)
                if m_ss_prefix:
                    ss_name = m_ss_prefix.group(1)
                if ss_name:
                    scope = "test" if ss_name.lower().endswith("test") else "main"
                elif TEST_CTX_RE.search(ctx):
                    scope = "test"
                else:
                    scope = "test" if "test" in decl.lower() else "main"
                if scope == "main" and "test" in decl.lower():
                    scope = "test"
                edges.append(
                    {
                        "declaration": decl,
                        "coord": m.group("coord"),
                        "scope": scope,
                        "sourceSet": ss_name or None,
                        "evidence": f"{build_rel}:{i}",
                    }
                )

        # 括号深度与块头栈：注释行同样要计（注释里可能带花括号）
        if in_block_comment and "*/" in stripped:
            in_block_comment = False
        # 块头取整行（去掉行尾注释）：一行可能开多个 `{`，
        # 只取首个 `{` 之前的文本会丢掉 `matching { it.name == "androidHostTest" }` 里的关键信息
        frame_text = "" if line_is_comment else re.sub(r"//.*$", "", line).strip()
        for ch in line:
            if ch == "{":
                frames.append(frame_text)
            elif ch == "}" and frames:
                frames.pop()

    return edges


def parse_module(root: str, coord: str, plugins: dict) -> dict:
    path = coord.lstrip(":").replace(":", "/")
    abs_dir = os.path.join(root, path)
    build_path = os.path.join(abs_dir, "build.gradle.kts")
    text = read_text(build_path)
    build_rel = rel(root, build_path)

    ns = re.search(r'namespace\s*=\s*"([^"]+)"', text)

    aliases = plugin_alias_ids(text)
    applied_ids = sorted({a for a in aliases if a in plugins})

    chain: list[str] = []
    for aid in applied_ids:
        for c in plugins.get(aid, {}).get("chain", [aid]):
            if c not in chain:
                chain.append(c)

    # 模块类型判定：取应用链里最具体的一个
    if "minibgm.android.application" in chain or "minibgm.android.application.compose" in chain:
        mtype = "application"
        group = "app"
    elif "minibgm.android.feature" in chain:
        mtype = "android-feature"
        group = "feature"
    elif "minibgm.kmp.library" in chain:
        mtype = "kmp-library"
        group = coord.split(":")[1] if coord.count(":") >= 2 else "other"
    elif "minibgm.android.library" in chain:
        mtype = "android-library"
        group = coord.split(":")[1] if coord.count(":") >= 2 else "other"
    else:
        mtype = "unknown"
        group = "other"

    explicit: list[dict] = []
    for e in extract_edges(text, build_rel, coord):
        explicit.append(
            {
                "from": coord,
                "to": e["coord"],
                "origin": "explicit",
                "declaration": e["declaration"],
                "scope": e["scope"],
                "sourceSet": e["sourceSet"],
                "evidence": e["evidence"],
            }
        )

    injected: list[dict] = []
    seen_injected: set[tuple[str, str, str]] = set()
    for aid in applied_ids:
        for inj in plugins[aid]["effective"]:
            scope = "test" if "test" in inj["declaration"].lower() else "main"
            key = (inj["coord"], aid, scope)
            if key in seen_injected:
                continue
            seen_injected.add(key)
            injected.append(
                {
                    "from": coord,
                    "to": inj["coord"],
                    "origin": "convention",
                    "declaration": inj["declaration"],
                    "scope": scope,
                    "sourceSet": None,
                    "plugin": aid,
                    "evidence": inj["evidence"],
                }
            )

    return {
        "id": coord,
        "path": path,
        "name": coord.split(":")[-1],
        "group": group,
        "type": mtype,
        "namespace": ns.group(1) if ns else None,
        "plugins": applied_ids,
        "pluginChain": chain,
        "buildFile": build_rel,
        "explicitEdges": explicit,
        "injectedEdges": injected,
        "buildLines": len(text.splitlines()),
    }


# --------------------------------------------------------------------------
# 4. Kotlin 源码扫描
# --------------------------------------------------------------------------

MODS = (
    r"(?:public|internal|private|protected|expect|actual|abstract|open|sealed|data|"
    r"value|inline|annotation|enum|external|const|override|suspend|operator|infix|"
    r"tailrec|lateinit|companion|fun|vararg|crossinline|noinline|reified)"
)
DECL_RE = re.compile(
    r"^(?P<mods>(?:(?:@\w+(?:\([^)]*\))?)\s+)*(?:" + MODS + r"\s+)*)"
    r"(?P<kind>class|interface|object|typealias)\s+(?P<name>[A-Za-z_]\w*)"
)
FUN_RE = re.compile(
    r"^(?P<mods>(?:(?:@\w+(?:\([^)]*\))?)\s+)*)"
    r"(?:(?:public|internal|private|protected|expect|actual|inline|suspend|operator|"
    r"infix|tailrec|external|override|abstract|open)\s+)*fun\s+(?P<rest>.+)$"
)
PROP_RE = re.compile(
    r"^(?:(?:public|internal|private|protected|expect|actual|const|lateinit|override)\s+)*"
    r"(?:val|var)\s+(?P<name>[A-Za-z_]\w*)"
)


def parse_fun_name(rest: str) -> str | None:
    """从 `fun` 之后的部分取出函数名。

    必须处理扩展函数：`fun List<SiteLink>.sortedBySitePriority()` 的名字是
    sortedBySitePriority，不是 List——早先按「可选 receiver.」的正则会把 List 当成函数名，
    于是画布上凭空出现一个名为 List 的声明。
    """
    i = 0
    if rest.startswith("<"):
        depth = 0
        for j, ch in enumerate(rest):
            if ch == "<":
                depth += 1
            elif ch == ">":
                depth -= 1
                if depth == 0:
                    i = j + 1
                    break
    p = rest.find("(", i)
    if p < 0:
        return None
    head = rest[i:p].strip()
    if not head:
        return None
    head = head.split(".")[-1].strip()  # 去掉 receiver（可能带泛型/可空标记）
    m = re.match(r"[A-Za-z_]\w*", head)
    return m.group(0) if m else None


def classify(name: str, kind: str, package: str, path: str) -> str:
    if kind == "fun":
        return "function"
    if kind == "typealias":
        return "typealias"
    if kind == "property":
        return "di-module" if name.endswith("Module") else "property"
    if kind == "interface":
        if name.endswith("Repository"):
            return "repository-interface"
        if name.endswith("Dao"):
            return "dao"
        if name.endswith("Service"):
            return "service-interface"
        return "interface"
    if kind == "object":
        if name.endswith("Module"):
            return "di-module"
        return "object"
    low = path.lower()
    if name.endswith("ViewModel"):
        return "viewmodel"
    if name.endswith("UiState") or name.endswith("State"):
        return "state"
    if name.endswith("Screen"):
        return "screen"
    if name.endswith("RepositoryImpl") or name.endswith("Repository"):
        return "repository"
    if name.endswith("UseCase") or name.startswith("Get") or name.startswith("Observe"):
        return "usecase"
    if name.endswith("DataSource"):
        return "datasource"
    if name.endswith("Dao"):
        return "dao"
    if name.endswith("Entity"):
        return "entity"
    if name.endswith("Worker"):
        return "worker"
    if name.endswith("Service"):
        return "service"
    if name.endswith("Client") or name.endswith("Api") or name.endswith("ApiService"):
        return "network"
    if name.endswith("Test"):
        return "test"
    if "/components/" in low or name.endswith("Card") or name.endswith("Row"):
        return "component"
    if package.endswith(".model") or "/model/" in low:
        return "model"
    return "class"


LAYER_OF = [
    ("viewmodel", "UI"),
    ("screen", "UI"),
    ("component", "UI"),
    ("state", "UI"),
    ("repository", "Domain"),
    ("repository-interface", "Domain"),
    ("usecase", "Domain"),
    ("network", "Data"),
    ("service", "Data"),
    ("service-interface", "Data"),
    ("datasource", "Data"),
    ("dao", "Data"),
    ("entity", "Data"),
    ("worker", "Background"),
    ("model", "Model"),
    ("di-module", "DI"),
    ("property", "Other"),
    ("function", "Other"),
    ("typealias", "Model"),
]


def layer_of(kind: str) -> str:
    for k, v in LAYER_OF:
        if k == kind:
            return v
    return "Other"


def scan_kotlin(root: str, mod_dir: str) -> dict:
    """扫描单个模块的 Kotlin 源码，返回声明/统计。"""
    src_root = os.path.join(mod_dir, "src")
    files: list[dict] = []
    decls: list[dict] = []
    sets: dict[str, Counter] = defaultdict(Counter)

    if not os.path.isdir(src_root):
        return {"files": [], "declarations": [], "loc": 0, "sourceSets": [], "counts": {}}

    for dirpath, dirnames, filenames in os.walk(src_root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in sorted(filenames):
            if not fn.endswith(".kt"):
                continue
            abs_p = os.path.join(dirpath, fn)
            rp = rel(root, abs_p)
            # 路径形如 <modulePath>/src/<sourceSet>/kotlin/...，模块层级数不固定，
            # 必须定位 "src" 段而不是写死下标
            parts = rp.split("/")
            srcset = "?"
            if "src" in parts:
                si = parts.index("src")
                if si + 1 < len(parts):
                    srcset = parts[si + 1]
            text = read_text(abs_p)
            lines = text.splitlines()
            pkg = ""
            imports: list[str] = []
            for line in lines[:80]:
                if line.startswith("package "):
                    pkg = line[8:].strip()
                elif line.startswith("import "):
                    imports.append(line[7:].strip())

            is_test = srcset in TEST_SETS
            file_decls: list[str] = []
            pending_annotations: list[str] = []
            for i, line in enumerate(lines, 1):
                s = line.strip()
                if not s or s.startswith("//") or s.startswith("*") or s.startswith("/*"):
                    continue
                if s.startswith("@"):
                    pending_annotations.append(s.split("(")[0].split(" ")[0])
                    continue
                if line[0].isspace() or line[0] in "\t)":
                    pending_annotations = []
                    continue
                m = DECL_RE.match(line)
                kind = None
                name = None
                if m:
                    kind, name = m.group("kind"), m.group("name")
                else:
                    m2 = FUN_RE.match(line)
                    if m2:
                        fn = parse_fun_name(m2.group("rest"))
                        if fn:
                            kind, name = "fun", fn
                    else:
                        m3 = PROP_RE.match(line)
                        if m3 and not line.rstrip().endswith("("):
                            # 顶层属性：Koin 的 `val scheduleModule = module { ... }` 就靠它才能进引用图
                            kind, name = "property", m3.group("name")

                if kind and name:
                    k = classify(name, kind, pkg, rp)
                    decls.append(
                        {
                            "id": f"{rp}:{i}",
                            "name": name,
                            "kind": k,
                            "raw": kind,
                            "module": None,  # 后续回填
                            "file": rp,
                            "line": i,
                            "layer": "Test" if is_test else layer_of(k),
                            "annotations": pending_annotations[:3],
                            "composable": "@Composable" in pending_annotations,
                            "isTest": is_test,
                            "loc": 0,
                        }
                    )
                    file_decls.append(name)
                pending_annotations = []

            loc = sum(1 for l in lines if l.strip() and not l.strip().startswith("//"))
            sset = sets[srcset]
            sset["files"] += 1
            sset["loc"] += loc

            files.append(
                {
                    "path": rp,
                    "srcSet": srcset,
                    "package": pkg,
                    "loc": loc,
                    "declCount": len(file_decls),
                    "imports": imports,
                    "isTest": is_test,
                    "hasExpect": bool(re.search(r"^\s*expect\s", text, re.M)),
                    "hasActual": bool(re.search(r"^\s*actual\s", text, re.M)),
                }
            )

    # 把单文件的 loc 平摊回它的声明（供节点显示规模）
    per_file_loc = {f["path"]: f["loc"] for f in files}
    per_file_decls = Counter()
    for d in decls:
        per_file_decls[d["file"]] += 1
    for d in decls:
        n = per_file_decls[d["file"]] or 1
        d["loc"] = max(1, per_file_loc.get(d["file"], 0) // n)

    # 声明体文本：从本声明行到同文件下一个声明行，用于静态引用解析。
    # 只在本进程内使用，不写入 JSON（体积太大）。
    bodies: dict[str, str] = {}
    by_file: dict[str, list[dict]] = defaultdict(list)
    for d in decls:
        by_file[d["file"]].append(d)
    for path, ds in by_file.items():
        ds.sort(key=lambda x: x["line"])
        text = read_text(os.path.join(root, path))
        lines = text.splitlines()
        for i, d in enumerate(ds):
            end = ds[i + 1]["line"] - 1 if i + 1 < len(ds) else len(lines)
            bodies[d["id"]] = "\n".join(lines[d["line"] - 1 : end])
        # 文件尾部没有声明的代码也算进最后一个声明，避免漏掉底部调用

    return {
        "files": files,
        "declarations": decls,
        "bodies": bodies,
        "loc": sum(f["loc"] for f in files),
        "sourceSets": [
            {"name": k, "files": v["files"], "loc": v["loc"]} for k, v in sorted(sets.items())
        ],
        "counts": {
            "files": len(files),
            "mainFiles": len([f for f in files if not f["isTest"]]),
            "testFiles": len([f for f in files if f["isTest"]]),
            "declarations": len(decls),
            "composables": len([d for d in decls if d["composable"]]),
        },
    }


IDENT_RE = re.compile(r"[A-Za-z_][A-Za-z_0-9]*")


def build_reference_graph(all_decls: list[dict], bodies: dict[str, str]) -> dict:
    """基于符号名的静态引用解析（不是运行时调用图）。

    对每个声明的函数体做标识符切词，与全局声明名取交集。同名遮蔽、扩展函数、
    反射调用都会漏，所以画布把这条结果的证据强度标为「推断」。
    """
    name_to_idx: dict[str, list[int]] = defaultdict(list)
    for i, d in enumerate(all_decls):
        d["globalIdx"] = i
        n = d["name"]
        if len(n) >= 3:
            name_to_idx[n].append(i)

    unresolved = 0
    for i, d in enumerate(all_decls):
        body = bodies.get(d["id"], "")
        found: list[int] = []
        seen: set[int] = set()
        for m in IDENT_RE.finditer(body):
            tok = m.group(0)
            for j in name_to_idx.get(tok, ()):
                if j == i or j in seen:
                    continue
                seen.add(j)
                found.append(j)
        d["callees"] = sorted(found, key=lambda j: all_decls[j]["name"])[:16]
        d["calleeCount"] = len(found)
        if not found:
            unresolved += 1
    return {"resolved": len(all_decls) - unresolved, "unresolved": unresolved}


# --------------------------------------------------------------------------
# 5. 测试证据 —— Gradle build/test-results XML
# --------------------------------------------------------------------------


def scan_test_results(root: str, mod_dir: str) -> list[dict]:
    base = os.path.join(mod_dir, "build", "test-results")
    out: list[dict] = []
    if not os.path.isdir(base):
        return out
    for task in sorted(os.listdir(base)):
        task_dir = os.path.join(base, task)
        if not os.path.isdir(task_dir):
            continue
        cases = fails = errors = skipped = 0
        suites: list[str] = []
        for fn in sorted(os.listdir(task_dir)):
            if not (fn.startswith("TEST-") and fn.endswith(".xml")):
                continue
            try:
                root_el = ET.parse(os.path.join(task_dir, fn)).getroot()
            except ET.ParseError:
                continue
            n = int(root_el.get("tests", 0))
            cases += n
            fails += int(root_el.get("failures", 0))
            errors += int(root_el.get("errors", 0))
            skipped += int(root_el.get("skipped", 0))
            suites.append(root_el.get("name", fn))
        out.append(
            {
                "task": task,
                "cases": cases,
                "failures": fails,
                "errors": errors,
                "skipped": skipped,
                "suites": len(suites),
                "evidence": rel(root, task_dir),
                "empty": cases == 0,
                # 目录 mtime ≈ 本机最近一次真实执行该测试任务的时间，
                # 是「有没有真跑过」最直接的物证
                "ranAt": datetime.fromtimestamp(os.path.getmtime(task_dir))
                .astimezone()
                .strftime("%Y-%m-%d %H:%M"),
            }
        )
    return out


# --------------------------------------------------------------------------
# 6. 架构红线检查（对齐 AGENTS.md / ArchitectureRulesTest）
# --------------------------------------------------------------------------


def check_rules(root: str, modules: list[dict], mod_src: dict[str, dict]) -> list[dict]:
    findings: list[dict] = []
    mod_by_id = {m["id"]: m for m in modules}
    feature_ids = {m["id"] for m in modules if m["group"] == "feature"}
    app_ids = {m["id"] for m in modules if m["group"] == "app"}

    def add(rule, severity, title, detail, evidence, module=None, snippet=None):
        findings.append(
            {
                "rule": rule,
                "severity": severity,
                "title": title,
                "detail": detail,
                "evidence": evidence,
                "module": module,
                "snippet": snippet,
            }
        )

    # R1 feature 之间不得互相依赖（含 convention 注入；只看生产作用域）
    for m in modules:
        if m["id"] not in feature_ids:
            continue
        for e in m["explicitEdges"] + m["injectedEdges"]:
            if e.get("scope") != "main":
                continue
            if e["to"] in feature_ids:
                add(
                    "R1",
                    "P1",
                    "feature 依赖另一个 feature",
                    f'{m["id"]} → {e["to"]}（应该走 type-safe route contract）',
                    e["evidence"],
                    m["id"],
                )

    # R11 测试专用模块不得进入生产作用域
    for m in modules:
        for e in m["explicitEdges"] + m["injectedEdges"]:
            if e.get("scope") == "main" and e["to"].split(":")[-1] == "testing":
                add(
                    "R11",
                    "P1",
                    "生产作用域依赖了测试专用模块",
                    f'{m["id"]} → {e["to"]}（声明 {e["declaration"]}），'
                    f'测试替身会被打进产物',
                    e["evidence"],
                    m["id"],
                )

    # R2 feature 不得直接依赖 network/database/datastore 工程，也不得 import ktor/room
    forbidden_coords = {":core:network", ":core:database", ":core:datastore"}
    for m in modules:
        if m["id"] not in feature_ids:
            continue
        for e in m["explicitEdges"] + m["injectedEdges"]:
            if e.get("scope") != "main":
                continue
            if e["to"] in forbidden_coords:
                add(
                    "R2",
                    "P1",
                    "feature 绕过 :core:data 直接依赖底层模块",
                    f'{m["id"]} → {e["to"]}（UI 层只允许经 :core:data repository）',
                    e["evidence"],
                    m["id"],
                )
    for m in modules:
        if m["id"] not in feature_ids:
            continue
        for f in mod_src[m["id"]]["files"]:
            if f["isTest"]:
                continue
            for imp in f["imports"]:
                if imp.startswith("io.ktor.") or imp.startswith("androidx.room."):
                    add(
                        "R2",
                        "P1",
                        "feature 源码出现底层库 import",
                        f'{m["id"]} / {f["path"]} → import {imp}',
                        f'{f["path"]}:import {imp}',
                        m["id"],
                        f"import {imp}",
                    )

    # R3 :core:model 必须是纯 Kotlin
    for m in modules:
        if m["id"] != ":core:model":
            continue
        for f in mod_src[m["id"]]["files"]:
            for imp in f["imports"]:
                if imp.startswith("android.") or imp.startswith("androidx."):
                    add(
                        "R3",
                        "P1",
                        ":core:model 出现 Android 依赖",
                        f'{f["path"]} → import {imp}',
                        f'{f["path"]}:import {imp}',
                        m["id"],
                        f"import {imp}",
                    )

    # R4 ViewModel 不得暴露 public MutableStateFlow
    for m, src in mod_src.items():
        for d in src["declarations"]:
            if d["kind"] != "viewmodel":
                continue
            text = read_text(os.path.join(root, d["file"]))
            for i, line in enumerate(text.splitlines(), 1):
                if "MutableStateFlow" not in line or "private" in line:
                    continue
                if not re.match(r"^\s{4,}(val|var)\s", line):
                    continue  # 只统计类成员层级
                add(
                    "R4",
                    "P2",
                    "ViewModel 暴露了非 private 的 MutableStateFlow",
                    f'{d["name"]} 的 {line.strip()[:60]}（应只暴露 StateFlow<UiState>）',
                    f'{d["file"]}:{i}',
                    d["module"],
                    line.strip(),
                )

    # R5 feature 内不得硬编码颜色
    for m in modules:
        if m["id"] not in feature_ids:
            continue
        for f in mod_src[m["id"]]["files"]:
            if f["isTest"]:
                continue
            text = read_text(os.path.join(root, f["path"]))
            for i, line in enumerate(text.splitlines(), 1):
                if re.search(r"Color\(0x", line) and not line.strip().startswith("//"):
                    add(
                        "R5",
                        "P2",
                        "feature 内硬编码颜色",
                        f'{f["path"]}: {line.strip()[:70]}',
                        f'{f["path"]}:{i}',
                        m["id"],
                        line.strip(),
                    )

    # R6 feature 内不得手写裸 IO
    raw_io = re.compile(
        r"HttpURLConnection|java\.net\.URL|java\.net\.Socket|"
        r"context\.cacheDir|context\.filesDir|FileOutputStream|FileInputStream"
    )
    for m in modules:
        if m["id"] not in feature_ids:
            continue
        for f in mod_src[m["id"]]["files"]:
            if f["isTest"]:
                continue
            text = read_text(os.path.join(root, f["path"]))
            for i, line in enumerate(text.splitlines(), 1):
                if raw_io.search(line) and not line.strip().startswith("//"):
                    add(
                        "R6",
                        "P1",
                        "feature 内出现裸 IO",
                        f'{f["path"]}: {line.strip()[:70]}',
                        f'{f["path"]}:{i}',
                        m["id"],
                        line.strip(),
                    )

    # R7 测试源集误命名（AGENTS.md 规则 4 记录的事故）
    for m in modules:
        for s in mod_src[m["id"]]["sourceSets"]:
            if s["name"] == "androidUnitTest":
                add(
                    "R7",
                    "P1",
                    "测试源集使用了会导致静默零用例的名字 androidUnitTest",
                    f'{m["id"]}/src/androidUnitTest（{s["files"]} 个文件永远不会被执行）',
                    f'{m["path"]}/src/androidUnitTest',
                    m["id"],
                )

    # R8 零测试模块 / 僵尸测试源集（有测试文件但没有任何 test-results 证据）
    for m in modules:
        if m["type"] == "unknown":
            continue
        counts = mod_src[m["id"]]["counts"]
        res = m["testResults"]
        if counts["mainFiles"] > 0 and counts["testFiles"] == 0:
            add(
                "R8",
                "P2",
                "模块没有任何测试文件",
                f'{m["id"]}：{counts["mainFiles"]} 个生产文件，0 个测试文件',
                m["buildFile"],
                m["id"],
            )
        elif counts["testFiles"] > 0 and not res:
            add(
                "R8",
                "P3",
                "存在测试文件但缺少已执行证据",
                f'{m["id"]}：{counts["testFiles"]} 个测试文件，build/test-results 下无 XML —— '
                f'未在本机跑过，Green ≠ tested',
                f'{m["path"]}/src',
                m["id"],
            )
        elif res and all(r["empty"] for r in res):
            add(
                "R8",
                "P1",
                "测试任务执行了 0 个用例",
                f'{m["id"]}：task {[r["task"] for r in res]} 的 XML 中 tests=0',
                res[0]["evidence"],
                m["id"],
            )

    # R9 :app 未接线（feature 写了但没被 app 依赖）
    for m in modules:
        if m["id"] not in feature_ids:
            continue
        depended = any(
            e["to"] == m["id"] and e.get("scope") == "main"
            for a in modules
            if a["id"] in app_ids
            for e in a["explicitEdges"]
        )
        if not depended:
            add(
                "R9",
                "P2",
                "feature 模块未被 :app 接线",
                f'{m["id"]} 不在 :app 的生产依赖列表里，代码可能进不了产物',
                mod_by_id[":app"]["buildFile"] if ":app" in mod_by_id else "settings.gradle.kts",
                m["id"],
            )

    # R10 依赖环 —— 只看生产作用域，测试源集对 public API 模块的依赖不构成环
    graph: dict[str, set[str]] = {m["id"]: set() for m in modules}
    for m in modules:
        for e in m["explicitEdges"] + m["injectedEdges"]:
            if e.get("scope") != "main":
                continue
            if e["to"] in graph:
                graph[m["id"]].add(e["to"])

    color: dict[str, int] = {}

    def dfs(node: str, stack: list[str]):
        color[node] = 1
        stack.append(node)
        for nxt in sorted(graph.get(node, ())):
            if color.get(nxt, 0) == 0:
                dfs(nxt, stack)
            elif color.get(nxt) == 1:
                cycle = stack[stack.index(nxt) :] + [nxt]
                add(
                    "R10",
                    "P1",
                    "模块依赖存在环",
                    " → ".join(cycle),
                    mod_by_id[cycle[0]]["buildFile"],
                    cycle[0],
                )
        stack.pop()
        color[node] = 2

    for n in sorted(graph):
        if color.get(n, 0) == 0:
            dfs(n, [])

    return findings


# --------------------------------------------------------------------------
# 7. Git 变更层
# --------------------------------------------------------------------------


def parse_git(root: str, modules: list[dict]) -> dict:
    head = sh(["git", "rev-parse", "--short", "HEAD"], root).strip()
    branch = sh(["git", "rev-parse", "--abbrev-ref", "HEAD"], root).strip()
    if branch == "HEAD":
        branch = "(detached HEAD)"
    porcelain = sh(["git", "status", "--porcelain"], root)
    dirty: list[dict] = []
    for line in porcelain.splitlines():
        if len(line) < 4:
            continue
        code, path = line[:2].strip(), line[3:].strip()
        if " -> " in path:
            path = path.split(" -> ")[-1]
        dirty.append({"code": code or "??", "path": path})

    recent_raw = sh(
        ["git", "log", "--pretty=format:%h%x1f%ad%x1f%an%x1f%s", "--date=short", "-30"], root
    )
    recent: list[dict] = []
    for line in recent_raw.splitlines():
        parts = line.split("\x1f")
        if len(parts) == 4:
            recent.append({"hash": parts[0], "date": parts[1], "author": parts[2], "subject": parts[3]})

    # 文件级「最后变更」：一次 git log --name-only 遍历，避免逐文件调用（267 次子进程）
    # 同一次遍历顺带记录最近 30 个提交各自碰过的文件，供画布的时间轴做影响面高亮
    file_history: dict[str, dict] = {}
    log_raw = sh(
        [
            "git",
            "log",
            "-n",
            "600",
            "--pretty=format:@%h%x1f%ad%x1f%an%x1f%s",
            "--date=short",
            "--name-only",
        ],
        root,
    )
    cur: dict | None = None
    cur_index = -1
    for line in log_raw.splitlines():
        if line.startswith("@"):
            parts = line[1:].split("\x1f")
            if len(parts) == 4:
                cur = {"hash": parts[0], "date": parts[1], "author": parts[2], "subject": parts[3]}
            cur_index += 1
            if 0 <= cur_index < len(recent):
                recent[cur_index]["touched"] = []
            continue
        p = line.strip()
        if p and cur:
            if p not in file_history:
                file_history[p] = cur
            if 0 <= cur_index < len(recent):
                t = recent[cur_index].setdefault("touched", [])
                if p.endswith(".kt") and len(t) < 60:
                    t.append(p)

    for m in modules:
        p = m["path"]
        m["git"] = {
            "commits": int(sh(["git", "rev-list", "--count", "HEAD", "--", p], root).strip() or 0),
            "last": None,
            "dirty": [d for d in dirty if d["path"].startswith(p + "/")],
        }
        last_raw = sh(
            ["git", "log", "-1", "--pretty=format:%h%x1f%ad%x1f%an%x1f%s", "--date=short", "--", p],
            root,
        ).strip()
        if last_raw:
            parts = last_raw.split("\x1f")
            if len(parts) == 4:
                m["git"]["last"] = {
                    "hash": parts[0],
                    "date": parts[1],
                    "author": parts[2],
                    "subject": parts[3],
                }
        recent90 = sh(
            ["git", "rev-list", "--count", "--since=90.days", "HEAD", "--", p], root
        ).strip()
        m["git"]["commits90d"] = int(recent90 or 0)
        for f in m.get("files", []):
            f["lastCommit"] = file_history.get(f["path"])

    return {
        "head": head,
        "branch": branch,
        "recent": recent,
        "dirty": dirty,
        "dirtyCount": len(dirty),
    }


# --------------------------------------------------------------------------
# 8. 汇总
# --------------------------------------------------------------------------


def main() -> int:
    ap = argparse.ArgumentParser(description="Codebase State Canvas scanner")
    ap.add_argument("root", nargs="?", default=".", help="工程根目录")
    ap.add_argument("-o", "--out", default=None, help="输出目录，默认 <root>/tools/codebase-canvas/out")
    args = ap.parse_args()

    root = os.path.abspath(args.root)
    out_dir = os.path.abspath(args.out or os.path.join(root, "tools", "codebase-canvas", "out"))
    os.makedirs(out_dir, exist_ok=True)

    print(f"[scan] root = {root}", file=sys.stderr)
    project_name, includes = parse_settings(root)
    if not includes:
        print("[scan] 未在 settings.gradle.kts 找到 include(...)", file=sys.stderr)
        return 1
    print(f"[scan] 模块 {len(includes)} 个", file=sys.stderr)

    plugins = parse_convention_plugins(root)
    print(f"[scan] convention plugin {len(plugins)} 个", file=sys.stderr)

    modules: list[dict] = []
    mod_src: dict[str, dict] = {}
    for coord in includes:
        m = parse_module(root, coord, plugins)
        mod_dir = os.path.join(root, m["path"])
        src = scan_kotlin(root, mod_dir)
        for d in src["declarations"]:
            d["module"] = coord
        m["counts"] = src["counts"]
        m["declarations"] = src["declarations"]
        m["files"] = src["files"]
        m["sourceSets"] = src["sourceSets"]
        m["loc"] = src["loc"]
        m["testResults"] = scan_test_results(root, mod_dir)
        modules.append(m)
        mod_src[coord] = src
        print(
            f"  - {coord:<22} {m['type']:<16} files={m['counts'].get('files',0):<4} "
            f"loc={m['loc']:<6} decls={m['counts'].get('declarations',0)}",
            file=sys.stderr,
        )

    edges: list[dict] = []
    for m in modules:
        edges.extend(m["explicitEdges"])
        edges.extend(m["injectedEdges"])

    all_decls: list[dict] = []
    bodies: dict[str, str] = {}
    for m in modules:
        for d in mod_src[m["id"]]["declarations"]:
            all_decls.append(d)
        bodies.update(mod_src[m["id"]].get("bodies", {}))
    ref_stats = build_reference_graph(all_decls, bodies)
    print(
        f"[scan] 静态引用图：{len(all_decls)} 个声明，其中 {ref_stats['unresolved']} 个无解析结果",
        file=sys.stderr,
    )

    findings = check_rules(root, modules, mod_src)
    print(f"[scan] 规则命中 {len(findings)} 条", file=sys.stderr)

    git = parse_git(root, modules)

    # 上下游关系（含隐式边），供画布做影响面高亮
    incoming: dict[str, list[dict]] = defaultdict(list)
    outgoing: dict[str, list[dict]] = defaultdict(list)
    for e in edges:
        if e["to"] in mod_src:
            incoming[e["to"]].append(e)
        if e["from"] in mod_src:
            outgoing[e["from"]].append(e)
    for m in modules:
        m["fanIn"] = len([e for e in incoming.get(m["id"], []) if e["scope"] == "main"])
        m["fanOut"] = len([e for e in outgoing.get(m["id"], []) if e["scope"] == "main"])

    test_total = sum(
        r["cases"] for m in modules for r in m["testResults"] if not r["empty"]
    )
    test_fail = sum(
        r["failures"] + r["errors"] for m in modules for r in m["testResults"] if not r["empty"]
    )
    modules_with_results = [m["id"] for m in modules if m["testResults"]]

    payload = {
        "meta": {
            "project": project_name,
            "root": root,
            "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
            "scanner": "tools/codebase-canvas/scan.py",
            "head": git["head"],
            "branch": git["branch"],
        },
        "stats": {
            "modules": len(modules),
            "loc": sum(m["loc"] for m in modules),
            "files": sum(m["counts"].get("files", 0) for m in modules),
            "declarations": sum(m["counts"].get("declarations", 0) for m in modules),
            "edges": len(edges),
            "explicitEdges": len([e for e in edges if e["origin"] == "explicit"]),
            "conventionEdges": len([e for e in edges if e["origin"] == "convention"]),
            "mainEdges": len([e for e in edges if e["scope"] == "main"]),
            "testEdges": len([e for e in edges if e["scope"] == "test"]),
            "testFiles": sum(m["counts"].get("testFiles", 0) for m in modules),
            "testCases": test_total,
            "testFailures": test_fail,
            "modulesWithTestEvidence": len(modules_with_results),
            "findings": len(findings),
            "findingsP1": len([f for f in findings if f["severity"] == "P1"]),
            "dirtyFiles": git["dirtyCount"],
            "referenceEdges": sum(d["calleeCount"] for d in all_decls),
        },
        "modules": modules,
        "edges": edges,
        "findings": findings,
        "git": git,
        "referenceGraph": ref_stats,
        "conventionPlugins": {
            k: {
                "id": v["id"],
                "cls": v["cls"],
                "source": v["source"],
                "applies": v["applies"],
                "injected": v["injected"],
            }
            for k, v in plugins.items()
        },
        "evidenceNotes": [
            "结构层：settings.gradle.kts 的 include(...) + 各模块 build.gradle.kts 的 project(\"...\")。",
            "隐式边：build-logic/convention 下 convention plugin 的 add(\"implementation\", project(...))，"
            "这类依赖不出现在模块自己的 build 文件里。",
            "变更层：git rev-list / git log -- <module>；工作区脏文件来自 git status --porcelain。",
            "证据层：Gradle build/test-results/<task>/TEST-*.xml 中真实存在的 tests/failures 计数。",
            "静态调用链是符号名解析结果，不是运行时追踪；画布中一律标为「推断」。",
            "运行状态层未接入运行时探针（需 Debug.startMethodTracing / 自定义 Kermit sink 等埋点），"
            "画布只展示可被文件系统与 git 证实的事实。",
        ],
    }

    json_path = os.path.join(out_dir, "canvas-data.json")
    with open(json_path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=1)
    js_path = os.path.join(out_dir, "canvas-data.js")
    with open(js_path, "w", encoding="utf-8") as fh:
        fh.write("window.CANVAS_DATA = ")
        json.dump(payload, fh, ensure_ascii=False)
        fh.write(";\n")

    print(
        f"[scan] 完成 → {json_path}\n"
        f"       模块 {payload['stats']['modules']} / 声明 {payload['stats']['declarations']} / "
        f"依赖边 {payload['stats']['edges']}（显式 {payload['stats']['explicitEdges']} + "
        f"隐式 {payload['stats']['conventionEdges']}）\n"
        f"       测试用例 {test_total}（失败 {test_fail}）/ 规则命中 {len(findings)}",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
