#!/usr/bin/env python3
"""MiniBgm 架构模块查看器生成器。

扫描 settings.gradle.kts 与各模块 build.gradle.kts，构建真实模块依赖图；
镜像 ArchitectureRulesTest 的 8 条红线做静态检查；把依赖图、规则结果与
全部 Kotlin 源码嵌入单个自包含 HTML（零外部依赖，浏览器直接打开）。

用法:  python3 tools/archviewer/generate.py
输出:  tools/archviewer/architecture.html
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# ---------------------------------------------------------------- 扫描

def parse_modules() -> list[str]:
    text = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    return re.findall(r'include\("([^"]+)"\)', text)


def module_dir(module: str) -> Path:
    return ROOT / module.strip(":").replace(":", "/")


# convention plugin 隐式注入的模块依赖（build-logic 中静态声明，build.gradle.kts 里看不到）
PLUGIN_IMPLIED = {
    "minibgm.android.feature": {
        "impl": [":core:model", ":core:common", ":core:data", ":core:designsystem", ":core:navigation"],
        "test": [":core:testing"],
    },
}


def parse_build_script(module: str) -> dict:
    """返回 {deps, testDeps, plugins, build_text}。"""
    build = module_dir(module) / "build.gradle.kts"
    text = build.read_text(encoding="utf-8") if build.exists() else ""
    deps = sorted(set(re.findall(r'project\("([^"]+)"\)', text)))
    test_deps = sorted(set(re.findall(r'testImplementation\(project\("([^"]+)"\)\)', text)))
    plugins = sorted(set(re.findall(r"alias\(libs\.plugins\.([A-Za-z0-9_.]+)\)", text)))
    for p in plugins:
        implied = PLUGIN_IMPLIED.get(p, {})
        deps += implied.get("impl", [])
        test_deps += implied.get("test", [])
    return {
        "deps": sorted(set(deps)),
        "testDeps": sorted(set(test_deps)),
        "plugins": plugins,
        "build_text": text,
    }


def group_of(module: str) -> str:
    if module == ":app":
        return "app"
    if module.startswith(":feature"):
        return "feature"
    if module.startswith(":core"):
        return "core"
    if module.startswith(":sync"):
        return "sync"
    return "other"


def collect_files(module: str) -> list[dict]:
    """收集模块内 .kt/.kts 源文件（glob 模式限定 src/，不会进入 build/）。"""
    files = []
    mdir = module_dir(module)
    for pattern in ("src/**/*.kt", "src/**/*.kts", "build.gradle.kts"):
        for p in mdir.glob(pattern):
            try:
                content = p.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            rel = p.relative_to(ROOT).as_posix()
            files.append(
                {
                    "path": rel,
                    "lines": content.count("\n") + 1,
                    "content": content,
                }
            )
    files.sort(key=lambda f: f["path"])
    return files


# ---------------------------------------------------------------- 规则检查（镜像 ArchitectureRulesTest）

def check_rules(modules: dict[str, dict]) -> list[dict]:
    violations: list[dict] = []

    def add(module: str, rule: str, where: str, detail: str, edge_to: str | None = None):
        violations.append(
            {"module": module, "rule": rule, "where": where, "detail": detail, "edgeTo": edge_to}
        )

    for name, m in modules.items():
        if m["group"] == "feature":
            for dep in m["deps"]:
                if dep.startswith(":feature"):
                    add(name, "R1 Feature 隔离", m["dir"] + "/build.gradle.kts", f"依赖了其他 feature 模块 {dep}", dep)
                if dep in (":core:network", ":core:database"):
                    add(name, "R2 单一数据源", m["dir"] + "/build.gradle.kts", f"越级依赖底层库 {dep}", dep)
        kt_files = [f for f in m["files"] if f["path"].endswith(".kt")]
        if m["group"] == "feature":
            for f in kt_files:
                for i, line in enumerate(f["content"].splitlines(), 1):
                    t = line.strip()
                    if t.startswith("//") or t.startswith("*"):
                        continue
                    if t.startswith("import com.infinitezerone.minibgm.core.network") or t.startswith(
                        "import com.infinitezerone.minibgm.core.database"
                    ):
                        add(name, "R2 单一数据源", f["path"] + ":" + str(i), f"违规直连底库 import -> {t}", f["path"])
                    if t.startswith("import io.ktor") or t.startswith("import androidx.room"):
                        add(name, "R6 框架隔离", f["path"] + ":" + str(i), f"违规引入传输/存储框架 -> {t}", f["path"])
                    if "Color(0x" in line:
                        add(name, "R7 主题一致性", f["path"] + ":" + str(i), f"硬编码颜色 -> {t}", f["path"])
                    for token, why in (
                        ("HttpURLConnection", "手写 HttpURLConnection 裸网络连接"),
                        ("java.net.URL", "直接使用 java.net.URL"),
                        ("java.net.Socket", "直接使用 java.net.Socket"),
                        (".cacheDir", "私自操作 cacheDir"),
                        (".filesDir", "私自操作 filesDir"),
                        ("FileOutputStream", "手写文件流写入"),
                    ):
                        if token in t:
                            add(name, "R8 裸 IO 隔离", f["path"] + ":" + str(i), f"{why} -> {t}", f["path"])
                if f["path"].endswith("ViewModel.kt"):
                    for i, line in enumerate(f["content"].splitlines(), 1):
                        t = line.strip()
                        if (t.startswith("val ") or t.startswith("var ")) and not t.startswith("private "):
                            if "MutableStateFlow" in t:
                                add(name, "R4 MVI 单向流", f["path"] + ":" + str(i), f"暴露可变状态流 -> {t}", f["path"])
        if name == ":core:model":
            for f in kt_files:
                for i, line in enumerate(f["content"].splitlines(), 1):
                    t = line.strip()
                    if any(t.startswith(p) for p in ("import android.", "import androidx.", "import com.google.android.")):
                        add(name, "R3 纯模型层", f["path"] + ":" + str(i), f"平台/UI 依赖 -> {t}", f["path"])
        if name == ":core:datastore":
            up = next((f for f in kt_files if f["path"].endswith("UserPreferences.kt")), None)
            if up:
                for i, line in enumerate(up["content"].splitlines(), 1):
                    field = re.search(r"\bval\s+(\w+)", line)
                    if field:
                        n = field.group(1).lower()
                        if any(k in n for k in ("token", "accesstoken", "refreshtoken", "authsecret")):
                            add(name, "R5 凭据隔离", up["path"] + ":" + str(i), f"疑似凭据字段 {field.group(1)}", up["path"])
    return violations


def edge_violation(src: str, dst: str) -> str | None:
    if src_group := GROUPS.get(src):
        if src_group == "feature" and dst.startswith(":feature"):
            return "R1"
        if src_group == "feature" and dst in (":core:network", ":core:database"):
            return "R2"
    return None


GROUPS: dict[str, str] = {}


# ---------------------------------------------------------------- 布局与生成

def build_graph() -> dict:
    names = parse_modules()
    modules: dict[str, dict] = {}
    for n in names:
        info = parse_build_script(n)
        files = collect_files(n)
        # 源集分布（KMP: commonMain/androidMain/commonTest/androidHostTest；Android-only: main/test）
        source_sets: dict[str, dict] = {}
        for f in files:
            if "/src/" not in f["path"]:
                continue
            ss = f["path"].split("/src/")[1].split("/")[0]
            bucket = source_sets.setdefault(ss, {"files": 0, "lines": 0})
            bucket["files"] += 1
            bucket["lines"] += f["lines"]

        m = {
            "module": n,
            "group": group_of(n),
            "kind": "kmp" if any("kmp" in p for p in info["plugins"]) else "android",
            "sourceSets": source_sets,
            "deps": [d for d in info["deps"] if d in names and d != ":core:testing"],
            # :core:testing 是纯测试支撑模块（testImplementation 或 androidHostTest 源集内 implementation），一律按测试边处理
            "testDeps": sorted(
                {d for d in info["testDeps"] if d in names}
                | ({":core:testing"} if ":core:testing" in info["deps"] and n != ":core:testing" else set())
            ),
            "externalDeps": [d for d in info["deps"] if d not in names],
            "plugins": info["plugins"],
            "dir": n.strip(":").replace(":", "/"),
            "files": files,
        }
        modules[n] = m
        GROUPS[n] = m["group"]

    # 分层：叶子（无依赖）为 0 层，app 最深；渲染时 app 在顶
    depth: dict[str, int] = {}
    def calc(n: str, seen: frozenset) -> int:
        if n in depth:
            return depth[n]
        deps = [d for d in modules[n]["deps"] if d != n and d not in seen]
        depth[n] = 1 + max((calc(d, seen | {n}) for d in deps), default=-1)
        return depth[n]
    for n in names:
        calc(n, frozenset())

    violations = check_rules(modules)
    vcount: dict[str, int] = {}
    for v in violations:
        vcount[v["module"]] = vcount.get(v["module"], 0) + 1

    loc = sum(f["lines"] for m in modules.values() for f in m["files"])
    nfiles = sum(len(m["files"]) for m in modules.values())

    data = {
        "modules": [
            {
                "module": n,
                "group": modules[n]["group"],
                "kind": modules[n]["kind"],
                "sourceSets": modules[n]["sourceSets"],
                "layer": depth[n],
                "deps": modules[n]["deps"],
                "testDeps": modules[n]["testDeps"],
                "externalDeps": modules[n]["externalDeps"],
                "plugins": modules[n]["plugins"],
                "dir": modules[n]["dir"],
                "violations": vcount.get(n, 0),
                "files": [{"path": f["path"], "lines": f["lines"]} for f in modules[n]["files"]],
            }
            for n in names
        ],
        "sources": {f["path"]: f["content"] for m in modules.values() for f in m["files"]},
        "violations": violations,
        "stats": {"modules": len(names), "files": nfiles, "loc": loc, "violations": len(violations)},
    }
    return data


HTML_TEMPLATE = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>MiniBgm 架构模块查看器</title>
<style>
  :root{
    --bg:#f6f8fa; --panel:#ffffff; --border:#e2e8f0; --text:#1e293b; --muted:#64748b;
    --app:#2563eb; --feature:#7c3aed; --core:#0d9488; --sync:#d97706; --ext:#94a3b8; --bad:#dc2626;
  }
  *{box-sizing:border-box;margin:0;padding:0}
  body{font:13px/1.5 -apple-system,"PingFang SC","Helvetica Neue",sans-serif;background:var(--bg);color:var(--text);overflow:hidden;height:100vh;display:flex;flex-direction:column}
  header{display:flex;align-items:center;gap:16px;padding:10px 16px;background:var(--panel);border-bottom:1px solid var(--border);flex-shrink:0}
  header h1{font-size:15px;font-weight:600}
  .stat{color:var(--muted)} .stat b{color:var(--text)}
  .stat.bad b{color:var(--bad)}
  #search{margin-left:auto;padding:5px 10px;border:1px solid var(--border);border-radius:8px;width:200px;font:inherit;outline:none}
  #search:focus{border-color:var(--app)}
  .toggle{display:flex;align-items:center;gap:5px;color:var(--muted);font-size:12px;cursor:pointer;user-select:none}
  .legend{display:flex;gap:10px;color:var(--muted);font-size:12px}
  .legend i{display:inline-block;width:10px;height:10px;border-radius:3px;margin-right:4px;vertical-align:-1px}
  main{flex:1;display:flex;min-height:0}
  #canvasWrap{flex:1;position:relative;overflow:hidden;background:var(--bg)}
  svg{width:100%;height:100%;cursor:grab} svg.panning{cursor:grabbing}
  .layerBand{fill:#ffffff;opacity:.5} .layerBand:nth-child(odd){fill:#eef2f7;opacity:.7}
  .layerLabel{font-size:11px;fill:#94a3b8;letter-spacing:.5px}
  .node rect{fill:var(--panel);stroke:var(--border);stroke-width:1.5;rx:10;filter:drop-shadow(0 1px 2px rgba(15,23,42,.08))}
  .node{cursor:pointer}
  .node.dim{opacity:.15} .edge.dim{opacity:.04} .node text{pointer-events:none}
  .node.sel rect{stroke-width:2.5}
  .node:hover rect{stroke:#475569}
  .badge{font-size:10px}
  .aside{width:420px;background:var(--panel);border-left:1px solid var(--border);display:flex;flex-direction:column;flex-shrink:0}
  .aside .hd{padding:12px 14px;border-bottom:1px solid var(--border)}
  .aside .hd .t{font-size:14px;font-weight:600;display:flex;align-items:center;gap:8px}
  .aside .bd{flex:1;overflow:auto;padding:10px 14px 20px}
  .aside .muted{color:var(--muted)}
  .aside h3{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.4px;margin:14px 0 6px}
  .chip{display:inline-block;margin:2px 4px 2px 0;padding:3px 9px;border-radius:20px;border:1px solid var(--border);font-size:12px;cursor:pointer;background:#fff}
  .chip:hover{border-color:#475569} .chip.sel{border-color:var(--app);color:var(--app);font-weight:600}
  .chip.bad{border-color:#fecaca;background:#fef2f2;color:var(--bad)}
  .vitem{padding:7px 9px;border:1px solid var(--border);border-radius:8px;margin-bottom:6px;cursor:pointer}
  .vitem:hover{border-color:var(--bad);background:#fef2f2}
  .vitem .r{font-weight:600;color:var(--bad);font-size:12px} .vitem .w{color:var(--muted);font-size:11px;font-family:ui-monospace,monospace}
  .file{display:flex;justify-content:space-between;padding:4px 6px;border-radius:6px;cursor:pointer;font-family:ui-monospace,SFMono-Regular,monospace;font-size:11.5px}
  .file:hover{background:#f1f5f9} .file .n{color:var(--muted)}
  .pre{background:#0f172a;color:#e2e8f0;border-radius:10px;overflow:auto;font:12px/1.55 ui-monospace,SFMono-Regular,Menlo,monospace;padding:12px}
  .pre .ln{display:inline-block;width:3.2em;color:#475569;text-align:right;margin-right:12px;user-select:none}
  .k{color:#7dd3fc}.s{color:#bef264}.c{color:#64748b;font-style:italic}.a{color:#fbbf24}.num{color:#f0abfc}
  .back{font:inherit;border:1px solid var(--border);background:#fff;border-radius:8px;padding:5px 12px;cursor:pointer}
  .back:hover{border-color:#475569}
  #tip{position:absolute;pointer-events:none;background:#0f172a;color:#e2e8f0;font-size:11px;padding:4px 8px;border-radius:6px;opacity:0;transition:opacity .12s;white-space:pre;z-index:5}
  .hint{position:absolute;left:12px;bottom:10px;color:var(--muted);font-size:11px;background:rgba(255,255,255,.8);padding:3px 8px;border-radius:6px}
</style>
</head>
<body>
<header>
  <h1>MiniBgm 架构模块查看器</h1>
  <span class="stat">模块 <b id="stM"></b></span>
  <span class="stat">源文件 <b id="stF"></b></span>
  <span class="stat">行数 <b id="stL"></b></span>
  <span class="stat bad">红线违规 <b id="stV"></b></span>
  <input id="search" placeholder="搜索模块…  (Esc 清除)">
  <label class="toggle"><input type="checkbox" id="tRed" checked> 仅直连依赖</label>
  <div class="legend">
    <span><i style="background:var(--app)"></i>app</span>
    <span><i style="background:var(--feature)"></i>feature</span>
    <span><i style="background:var(--core)"></i>core</span>
    <span><i style="background:var(--sync)"></i>sync</span>
    <span><i style="background:var(--bad)"></i>违规边/节点</span>
    <span class="muted">副标题 KMP = 多平台模块（commonMain+androidMain）</span>
  </div>
</header>
<main>
  <div id="canvasWrap"><svg id="svg"></svg><div id="tip"></div>
    <div class="hint">滚轮缩放 · 拖拽平移 · 悬停聚焦 · 点击下钻 · Esc 复位</div>
  </div>
  <aside class="aside"><div class="bd" id="asideBody" style="padding-top:14px"></div></aside>
</main>
<script>
const DATA = __DATA__;
const GC = {app:"var(--app)",feature:"var(--feature)",core:"var(--core)",sync:"var(--sync)",other:"var(--ext)",ext:"var(--ext)"};
const GN = {app:"App",feature:"Feature",core:"Core",sync:"Sync",other:"Other",ext:"External"};
const svg = document.getElementById('svg'), tip = document.getElementById('tip'), aside = document.getElementById('asideBody');

/* ---------- 数据预处理 ---------- */
const byName = {}; DATA.modules.forEach(m => byName[m.module] = m);
const maxLayer = Math.max(...DATA.modules.map(m => m.layer));
const NW = 176, NH = 56, GAPX = 46, GAPY = 120;
const fmt = s => s.slice(1).replace(':','/');

// 传递归约：若存在另一条路径到达同一目标，则该边为冗余，直连模式下隐藏
const ADJ = {}; DATA.modules.forEach(m => ADJ[m.module] = new Set(m.deps));
function reachSet(c){const seen=new Set(),st=[...(ADJ[c]||[])];while(st.length){const n=st.pop();if(seen.has(n))continue;seen.add(n);(ADJ[n]||[]).forEach(d=>st.push(d));}return seen;}
const REACH = {}; Object.keys(ADJ).forEach(k => REACH[k] = reachSet(k));
DATA.modules.forEach(m => m._red = m.deps.filter(d => ![...ADJ[m.module]].some(c => c!==d && REACH[c].has(d))));

function currentEdges(){
  const red = document.getElementById('tRed').checked;
  const list = [];
  DATA.modules.forEach(m => { (red?m._red:m.deps).forEach(d => list.push([m.module,d,0])); m.testDeps.forEach(d => list.push([m.module,d,1])); });
  return list;
}
function nodeEdges(name){ return currentEdges().filter(([a,b]) => a===name || b===name); }

// 每层一行，层内按组排序
const layers = {};
DATA.modules.forEach(m => (layers[m.layer] = layers[m.layer] || []).push(m));
const order = {app:0, feature:1, sync:2, core:3, other:4};
Object.values(layers).forEach(arr => arr.sort((a,b) => (order[a.group]-order[b.group]) || a.module.localeCompare(b.module)));
let W = 0;
Object.entries(layers).forEach(([ly, arr]) => {
  const rowW = arr.length*NW + (arr.length-1)*GAPX;
  W = Math.max(W, rowW);
  const x0 = (Math.max(W, 1000)-rowW)/2;
  arr.forEach((m,i) => { m.x = x0+i*(NW+GAPX); m.y = (maxLayer-+ly)*(NH+GAPY) + 40; });
});
const H = (maxLayer+1)*(NH+GAPY) + 60;
if (W < 1000) W = 1000;

// 外部节点（includeBuild 等）
const externals = [{module:"build-logic", group:"ext", note:"includeBuild 约定插件，配置所有模块"}];
externals.forEach((e,i) => { e.x = 20; e.y = 20 + i*70; });

/* ---------- 渲染 ---------- */
let view = {x:0, y:0, k:1}, selected = null, query = "", hovered = null;
const NS = "http://www.w3.org/2000/svg";
const el = (t,a) => { const e = document.createElementNS(NS,t); for(const k in a) e.setAttribute(k,a[k]); return e; };

svg.appendChild(el('g',{id:'viewport'}));
const vp = svg.querySelector('#viewport');
vp.appendChild(el('g',{id:'edges'}));
vp.appendChild(el('g',{id:'nodes'}));
const defs = el('defs',{});
defs.innerHTML = '<marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#94a3b8"/></marker><marker id="arrBad" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="#dc2626"/></marker>';
svg.insertBefore(defs, vp);

function nodeEdges(name){
  return DATA.modules.filter(m => m.deps.includes(name)).map(m => [m.module, name])
    .concat(byName[name].deps.map(d => [name, d]));
}

function edgePath(a,b,idx,total){
  const A = byName[a]||externals.find(e=>e.module===a), B = byName[b]||externals.find(e=>e.module===b);
  const x1 = A.x+NW/2, y1 = A.y+NH, x2 = B.x+NW/2, y2 = B.y;
  const dy = y2-y1;
  const off = (idx - (total-1)/2) * 14;
  return `M${x1},${y1} C${x1+off},${y1+dy*0.4} ${x2+off},${y2-dy*0.4} ${x2},${y2}`;
}

function render(){
  // 层级底色带 + 标签（底层 → 应用）
  const old = document.getElementById('bands'); if(old) old.remove();
  const bandG = el('g',{id:'bands'});
  const GN_L = {app:'App 应用层',feature:'Feature 功能层',sync:'Sync 同步',core:'Core 核心层',other:''};
  for(let ly=0; ly<=maxLayer; ly++){
    const y = (maxLayer-ly)*(NH+GAPY) + 40 - 22, h = NH + 44;
    bandG.appendChild(el('rect',{x:-4000,y,width:W+8000,height:h,class:'layerBand',rx:14}));
    const groups = [...new Set((layers[ly]||[]).map(m=>m.group))].map(g=>GN_L[g]).filter(Boolean).join(' · ');
    const t = el('text',{x:14,y:y+h/2+4,class:'layerLabel'});
    t.textContent = `L${ly}  ${groups}`; bandG.appendChild(t);
  }
  vp.insertBefore(bandG, vp.firstChild);

  // edges
  const eg = vp.querySelector('#edges'); eg.innerHTML = '';
  const allEdges = currentEdges();
  allEdges.forEach(([a,b,test],i) => {
    const bad = test ? null : isBadEdge(a,b);
    const p = el('path',{d:edgePath(a,b,i%7,Math.min(7,allEdges.length)),fill:'none',
      stroke: bad?'#f87171':(test?'#c4b5fd':'#b6c2d2'),'stroke-width':bad?2:1.3,'stroke-opacity':bad?0.95:0.7,
      'stroke-dasharray':test?'4 4':'none',
      'marker-end':bad?'url(#arrBad)':'url(#arr)',class:'edge','data-a':a,'data-b':b});
    p.addEventListener('mousemove',ev => {const wr=document.getElementById('canvasWrap').getBoundingClientRect();tip.style.opacity=1;tip.style.left=ev.clientX-wr.left+12+'px';tip.style.top=ev.clientY-wr.top+12+'px';tip.textContent=`${a}  →  ${b}${test?'   [testImplementation]':bad?'   [违规 '+bad+']':''}`;});
    p.addEventListener('mouseleave',() => tip.style.opacity=0);
    eg.appendChild(p);
  });
  // nodes
  const ng = vp.querySelector('#nodes'); ng.innerHTML = '';
  [...DATA.modules, ...externals].forEach(m => {
    const g = el('g',{class:'node','data-m':m.module,transform:`translate(${m.x},${m.y})`});
    const bad = (byName[m.module]&&byName[m.module].violations>0);
    g.appendChild(el('rect',{width:NW,height:NH,rx:10,
      stroke: bad?'var(--bad)':'var(--border)','stroke-dasharray':m.group==='ext'?'5 4':'none'}));
    g.appendChild(el('rect',{x:1,y:8,width:5,height:NH-16,rx:2.5,fill:GC[m.group]||GC.ext}));
    const label = m.group==='ext' ? m.module : fmt(m.module);
    const t1 = el('text',{x:17,y:24,'font-size':13,'font-weight':600,fill: bad?'var(--bad)':'var(--text)'});
    t1.textContent = label; g.appendChild(t1);
    const sub = m.group==='ext' ? 'includeBuild' : (byName[m.module] ? `${byName[m.module].kind==='kmp'?'KMP':'Android'} · ${byName[m.module].files.length} 文件` : '');
    const t2 = el('text',{x:17,y:41,'font-size':10.5,fill:'var(--muted)'});
    t2.textContent = sub; g.appendChild(t2);
    if (bad){ const t3 = el('text',{x:NW-10,y:17,'text-anchor':'middle',class:'badge',fill:'#fff'});
      const bg = el('circle',{cx:NW-10,cy:13,r:8,fill:'var(--bad)'});
      t3.textContent = byName[m.module].violations; g.appendChild(bg); g.appendChild(t3); }
    g.addEventListener('click',ev => {ev.stopPropagation(); select(m.module);});
    g.addEventListener('mouseenter',() => {hovered=m.module;applyFilter();});
    g.addEventListener('mouseleave',() => {hovered=null;applyFilter();});
    ng.appendChild(g);
  });
  applyFilter();
}

function isBadEdge(a,b){
  const A = byName[a]; if(!A) return null;
  if (A.group==='feature' && b.startsWith(':feature')) return 'R1';
  if (A.group==='feature' && (b===':core:network'||b===':core:database')) return 'R2';
  return null;
}

function applyFilter(){
  const focus = hovered || selected;
  const inEdge = a => !focus || a===focus;
  vp.querySelectorAll('.edge').forEach(p=>{
    const hit = inEdge(p.dataset.a)&&inEdge(p.dataset.b);
    const q = !query || p.dataset.a.includes(query)||p.dataset.b.includes(query);
    p.classList.toggle('dim', !(hit&&q));
    if (focus && hit && q){ p.setAttribute('stroke-opacity',1); p.setAttribute('stroke-width',1.8); }
    else { p.setAttribute('stroke-opacity', p.getAttribute('stroke')==='var(--bad)'?0.95:0.7); p.setAttribute('stroke-width', p.getAttribute('stroke')==='var(--bad)'?2:1.3); }
  });
  vp.querySelectorAll('.node').forEach(g=>{
    const m = g.dataset.m;
    const hit = !focus || m===focus || nodeEdges(focus).some(([a,b])=>a===m||b===m);
    const q = !query || m.replace(/[:_\/]/g,'').includes(query.replace(/[:_\/]/g,''));
    g.classList.toggle('dim', !(hit&&q));
    g.classList.toggle('sel', m===selected);
    g.querySelector('rect').setAttribute('stroke', (byName[m]&&byName[m].violations>0)?'var(--bad)':(m===selected?'var(--app)':'var(--border)'));
  });
}

/* ---------- 侧栏 ---------- */
function showOverview(){
  selected = null; applyFilter();
  const vs = DATA.violations;
  aside.innerHTML = `<div class="hd"><div class="t">项目总览</div></div>
  <div class="bd">
    <p class="muted" style="margin:6px 0 10px">${DATA.stats.modules} 个模块 · ${DATA.stats.files} 个源文件 · ${DATA.stats.loc} 行。依赖方向：上层依赖下层（App → Feature → Core）。</p>
    <h3>红线规则（镜像 ArchitectureRulesTest）</h3>
    ${['R1 Feature 隔离','R2 单一数据源','R3 纯模型层','R4 MVI 单向流','R5 凭据隔离','R6 框架隔离(ktor/room)','R7 主题一致性(Color 0x)','R8 裸 IO 隔离'].map(r=>`<div style="margin:2px 0">• ${r}</div>`).join('')}
    <h3>违规明细 ${vs.length?`(${vs.length})`:'— 全部通过 ✓'}</h3>
    ${vs.length ? vs.map((v,i)=>`<div class="vitem" data-i="${i}"><div class="r">${v.module} · ${v.rule}</div><div>${v.detail}</div><div class="w">${v.where}</div></div>`).join('') : '<p class="muted">当前代码库零违规。</p>'}
    <h3>模块</h3>
    ${DATA.modules.map(m=>`<span class="chip ${m.violations?'bad':''}" data-m="${m.module}">${fmt(m.module)}${m.violations?` (${m.violations})`:''}</span>`).join(' ')}
  </div>`;
  aside.querySelectorAll('.vitem').forEach(d=>d.addEventListener('click',()=>{const v=DATA.violations[+d.dataset.i];const f=v.where.split(':')[0];select(v.module,f);}));
  aside.querySelectorAll('.chip').forEach(c=>c.addEventListener('click',()=>select(c.dataset.m)));
}

function showModule(name){
  const m = byName[name];
  const dependents = DATA.modules.filter(x=>x.deps.includes(name)).map(x=>x.module);
  const sel = () => {};
  aside.innerHTML = `<div class="hd"><div class="t"><span style="width:10px;height:10px;border-radius:3px;background:${GC[m.group]};display:inline-block"></span>${m.module}
    <span class="muted" style="font-weight:400;font-size:12px">${GN[m.group]} · L${m.layer} · ${m.kind==='kmp'?'KMP':'Android'}</span>
    ${m.violations?`<span style="color:var(--bad);font-size:12px;font-weight:600">⚠ ${m.violations} 违规</span>`:'<span style="color:var(--muted);font-size:12px">✓ 规则通过</span>'}</div></div>
  <div class="bd">
    <p class="muted" style="margin:6px 0">${m.dir}/ · ${m.files.length} 个源文件 · ${m.files.reduce((s,f)=>s+f.lines,0)} 行</p>
    <h3>源集</h3>
    ${(m.kind==='kmp')?'<p class="muted" style="font-size:11px;margin-bottom:4px">commonMain 为跨平台共享代码，androidMain 仅 Android 可见并复用 commonMain；androidHostTest 在 JVM 上同时测试两者。</p>':''}
    ${Object.entries(m.sourceSets||{}).map(([k,v])=>`<span class="chip" title="${v.lines} 行">${k} <b>${v.files}</b> 文件 · ${v.lines} 行</span>`).join(' ')}
    <h3>依赖 (${m.deps.length})</h3>${m.deps.length?m.deps.map(d=>`<span class="chip ${isBadEdge(name,d)?'bad':''}" data-m="${d}">${fmt(d)}</span>`).join(' '):'<span class="muted">无</span>'}
    ${m.externalDeps.length?`<div class="muted" style="font-size:11px;font-family:ui-monospace,monospace;margin-top:4px">外部: ${m.externalDeps.join(', ')}</div>`:''}
    <h3>被依赖 (${dependents.length})</h3>${dependents.length?dependents.map(d=>`<span class="chip" data-m="${d}">${fmt(d)}</span>`).join(' '):'<span class="muted">无</span>'}
    ${m.plugins.length?`<h3>约定插件</h3><span class="muted" style="font-family:ui-monospace,monospace;font-size:11px">${m.plugins.join(', ')}</span>`:''}
    <h3>文件 (点击查看源码)</h3>
    ${m.files.map((f,i)=>`<div class="file" data-i="${i}"><span>${f.path.split('/').slice(-2).join('/')}</span><span class="n">${f.lines}</span></div>`).join('')}
  </div>`;
  aside.querySelectorAll('.chip').forEach(c=>c.addEventListener('click',()=>select(c.dataset.m)));
  aside.querySelectorAll('.file').forEach(d=>d.addEventListener('click',()=>showSource(m.files[+d.dataset.i].path, name)));
}

function showSource(path, backTo){
  const content = DATA.sources[path] || '';
  const lines = esc(content).split('\n');
  const html = lines.map((l,i)=>{
    let cls='';
    const t=l.trimStart();
    if(t.startsWith('//')||t.startsWith('*')||t.startsWith('/*')) cls='c';
    return `<div><span class="ln">${i+1}</span><span${cls?` class="${cls}"`:''}>${hl(l)||' '}</span></div>`;
  }).join('');
  aside.innerHTML = `<div class="hd"><div class="t" style="gap:10px"><button class="back" id="bk">← ${backTo}</button></div>
    <div class="muted" style="font-family:ui-monospace,monospace;font-size:11px;margin-top:6px">${path}</div></div>
    <div class="bd"><div class="pre">${html}</div></div>`;
  aside.querySelector('#bk').addEventListener('click',()=>showModule(backTo));
}

/* ---------- 极简 Kotlin 高亮 ---------- */
const KW = /\b(package|import|class|object|interface|fun|val|var|if|else|when|for|while|return|is|as|in|!in|suspend|private|internal|public|override|open|abstract|sealed|data|companion|init|constructor|this|super|null|true|false|try|catch|finally|throw|by|lateinit|const|enum|annotation|typealias|operator|inline|reified|expect|actual|external)\b/g;
function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
function hl(line){
  let s = esc(line);
  s = s.replace(/("[^"]*")/g,'<span class="s">$1</span>');
  s = s.replace(/(@[A-Za-z]\w*)/g,'<span class="a">$1</span>');
  s = s.replace(KW,'<span class="k">$1</span>');
  s = s.replace(/\b(\d[\d_.]*[LfU]?)\b/g,'<span class="num">$1</span>');
  return s;
}

/* ---------- 交互 ---------- */
function select(name, jumpFile){
  selected = name; applyFilter();
  showModule(name);
  if (jumpFile){
    const m = byName[name];
    const f = m.files.find(f=>jumpFile.endsWith(f.path)||f.path.endsWith(jumpFile));
    if (f) showSource(f.path, name);
  }
  centerOn(name);
}
function centerOn(name){
  const m = byName[name]||externals.find(e=>e.module===name); if(!m) return;
  const r = svg.getBoundingClientRect();
  view.x = r.width/2 - (m.x+NW/2)*view.k; view.y = r.height/2 - (m.y+NH/2)*view.k; apply();
}
function apply(){ vp.setAttribute('transform',`translate(${view.x},${view.y}) scale(${view.k})`); }
svg.addEventListener('wheel',e=>{
  e.preventDefault();
  const r = svg.getBoundingClientRect(), mx=e.clientX-r.left, my=e.clientY-r.top;
  const k2 = Math.min(4, Math.max(.15, view.k * (e.deltaY<0?1.12:0.89)));
  view.x = mx-(mx-view.x)*k2/view.k; view.y = my-(my-view.y)*k2/view.k; view.k = k2; apply();
},{passive:false});
let pan = null;
svg.addEventListener('mousedown',e=>{pan={x:e.clientX,y:e.clientY,vx:view.x,vy:view.y};svg.classList.add('panning');});
window.addEventListener('mousemove',e=>{if(pan){view.x=pan.vx+e.clientX-pan.x;view.y=pan.vy+e.clientY-pan.y;apply();}});
window.addEventListener('mouseup',()=>{pan=null;svg.classList.remove('panning');});
svg.addEventListener('click',e=>{if(e.target===svg||e.target.id==='viewport'||e.target.parentNode.id==='nodes'&&!e.target.closest('.node'))showOverview();});
window.addEventListener('keydown',e=>{
  if(e.key==='Escape'){query='';document.getElementById('search').value='';showOverview();fit();}
  const s=document.getElementById('search');
  if(e.key==='/'&&document.activeElement!==s){e.preventDefault();s.focus();}
});
document.getElementById('search').addEventListener('input',e=>{query=e.target.value.trim().toLowerCase();applyFilter();});
document.getElementById('tRed').addEventListener('change',()=>{render();fit();});
function fit(){
  const r = svg.getBoundingClientRect();
  view.k = Math.min(r.width/W, r.height/H)*0.96;
  view.x = (r.width-W*view.k)/2; view.y = (r.height-H*view.k)/2; apply();
}
window.addEventListener('resize',fit);

/* ---------- 启动 ---------- */
document.getElementById('stM').textContent = DATA.stats.modules;
document.getElementById('stF').textContent = DATA.stats.files;
document.getElementById('stL').textContent = DATA.stats.loc.toLocaleString();
document.getElementById('stV').textContent = DATA.stats.violations;
render(); showOverview(); fit();
</script>
</body>
</html>
"""


def main() -> int:
    data = build_graph()
    # "</" 转义为 "<\/"（合法 JSON），防止内嵌源码中出现 </script> 撑破页面
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    html = HTML_TEMPLATE.replace("__DATA__", payload)
    out = Path(__file__).resolve().parent / "architecture.html"
    out.write_text(html, encoding="utf-8")
    s = data["stats"]
    print(f"OK  {out}")
    print(f"    模块 {s['modules']} · 源文件 {s['files']} · {s['loc']} 行 · 红线违规 {s['violations']}")
    for v in data["violations"]:
        print(f"    [违规] {v['module']} {v['rule']} @ {v['where']}: {v['detail']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
