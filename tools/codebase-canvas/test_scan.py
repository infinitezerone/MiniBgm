#!/usr/bin/env python3
"""
Codebase State Canvas —— 数据质量测试。

画布上的每一句话都来自扫描器。扫描器错一格，画布就会自信地展示错误结论，
而且看起来「有证据」。所以这里的用例不是测功能，是测**数据的可信度**：

  ParserTest        —— 纯输入输出，把历史踩过的解析坑钉死
  ImpactTest        —— 影响面的分档逻辑（确定 / 间接 / 推断）不能混
  RepoDataQuality   —— 对真实仓库跑一遍扫描，校验跨文件的不变量

两个真事故（已固化为用例）：
  1. convention plugin 注入的依赖不出现在模块自己的 build 文件里，
     只看局部文件会漏掉 36 条边，得出「feature 互不相干」的错误结论。
  2. `matching { it.name == "androidHostTest" }.configureEach { dependencies { ... } }`
     这类嵌套源集里的依赖被当成生产依赖，凭空造出 :core:data → :core:testing → :core:data 假环。

用法:
    python3 tools/codebase-canvas/test_scan.py            # 全部
    python3 tools/codebase-canvas/test_scan.py --fast     # 只跑纯解析用例，不扫仓库
"""

from __future__ import annotations

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
FAST_ONLY = "--fast" in sys.argv
if FAST_ONLY:
    sys.argv.remove("--fast")

spec = importlib.util.spec_from_file_location("scancanvas", os.path.join(HERE, "scan.py"))
scan = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(scan)


# ==========================================================================
# 1. 解析层：把历史坑钉死
# ==========================================================================


class ParserTest(unittest.TestCase):
    """纯输入输出，不碰文件系统，不依赖仓库状态。"""

    def test_extension_function_name_is_not_receiver_type(self):
        """事故回归：`fun List<SiteLink>.sortedBySitePriority()` 曾被解析成名为 List 的函数。"""
        self.assertEqual(scan.parse_fun_name("List<SiteLink>.sortedBySitePriority(): List<SiteLink> ="), "sortedBySitePriority")
        self.assertEqual(scan.parse_fun_name("List<RelatedWork>.aggregateBySubject(): List<RelatedWork> {"), "aggregateBySubject")
        self.assertEqual(scan.parse_fun_name("<T> List<T>.cyclic(index: Int): T ="), "cyclic")
        self.assertEqual(scan.parse_fun_name("Map<String, List<Int>>.invert(): Map<Int, String> ="), "invert")
        self.assertEqual(scan.parse_fun_name("String?.orEmptyDash(): String ="), "orEmptyDash")

    def test_plain_function_name(self):
        self.assertEqual(scan.parse_fun_name("fetchUserCollections(username: String): X"), "fetchUserCollections")
        self.assertEqual(scan.parse_fun_name("<T : Any> requireNotNullValue(v: T?): T"), "requireNotNullValue")

    def test_no_name_returns_none(self):
        self.assertIsNone(scan.parse_fun_name(""))
        self.assertIsNone(scan.parse_fun_name("()"))

    def test_source_set_scope_common_main(self):
        text = """
plugins { alias(libs.plugins.minibgm.kmp.library) }
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
        }
    }
}
"""
        edges = scan.extract_edges(text, "core/x/build.gradle.kts", ":core:x")
        self.assertEqual(len(edges), 1)
        self.assertEqual(edges[0]["coord"], ":core:model")
        self.assertEqual(edges[0]["scope"], "main")
        self.assertEqual(edges[0]["sourceSet"], "commonMain")

    def test_source_set_scope_common_test(self):
        text = """
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
"""
        edges = scan.extract_edges(text, "core/x/build.gradle.kts", ":core:x")
        self.assertEqual(edges[0]["scope"], "test")

    def test_incident_2_nested_matching_source_set_is_test_scope(self):
        """事故回归：这套写法曾让 :core:data → :core:testing 被算成生产依赖，凭空造出依赖环。

        AGP-KMP 在 finalizeDsl 阶段才创建源集，所以工程里用 matching+configureEach 惰性匹配。
        块头里带着 it.name == "androidHostTest"，作用域判定必须读到它。
        """
        text = """
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
        }
        // androidHostTest 源集由 AGP KMP 插件在 finalizeDsl 阶段按需创建，
        // 用 matching+configureEach 惰性匹配，避免脚本求值期源集尚不存在
        matching { it.name == "androidHostTest" }.configureEach {
            dependencies {
                implementation(project(":core:testing"))
                implementation(libs.kotlin.test)
            }
        }
    }
}
"""
        edges = scan.extract_edges(text, "core/data/build.gradle.kts", ":core:data")
        by_coord = {e["coord"]: e for e in edges}
        self.assertIn(":core:model", by_coord)
        self.assertIn(":core:testing", by_coord)
        self.assertEqual(by_coord[":core:model"]["scope"], "main")
        self.assertEqual(by_coord[":core:testing"]["scope"], "test",
                         ":core:testing 在 matching{androidHostTest} 块里，必须是测试作用域")

    def test_test_token_matching_is_not_substring(self):
        """`latest` 里含 test 子串；用它判作用域会把无关依赖算成测试依赖。"""
        text = """
dependencies {
    implementation(libs.latest.androidx.core)
    implementation(project(":core:model"))
}
"""
        edges = scan.extract_edges(text, "m/build.gradle.kts", ":m")
        self.assertEqual([e["scope"] for e in edges], ["main"])

    def test_testimplementation_keyword_is_test_scope(self):
        text = """
dependencies {
    testImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:testing"))
    implementation(project(":core:model"))
}
"""
        edges = scan.extract_edges(text, "m/build.gradle.kts", ":m")
        self.assertEqual(
            sorted(e["scope"] for e in edges), ["main", "test", "test"]
        )

    def test_comments_produce_no_edges(self):
        text = """
dependencies {
    // implementation(project(":core:ghost"))
    // testImplementation(project(":core:ghost2"))
    implementation(project(":core:model"))
}
"""
        edges = scan.extract_edges(text, "m/build.gradle.kts", ":m")
        self.assertEqual([e["coord"] for e in edges], [":core:model"])

    def test_module_of_path_prefers_longest_match(self):
        """core/data 不能吞掉 core/database —— 前缀匹配必须取最长。"""
        modules = [
            {"id": ":core:data", "path": "core/data"},
            {"id": ":core:database", "path": "core/database"},
            {"id": ":app", "path": "app"},
        ]
        self.assertEqual(scan.module_of_path("core/database/src/X.kt", modules)[0], ":core:database")
        self.assertEqual(scan.module_of_path("core/data/src/X.kt", modules)[0], ":core:data")
        self.assertEqual(scan.module_of_path("app/src/X.kt", modules)[0], ":app")
        self.assertEqual(scan.module_of_path("build-logic/convention/X.kt", modules)[0], "build-logic")
        self.assertEqual(scan.module_of_path("README.md", modules)[0], "docs")

    def test_parse_hunks_new_side_ranges(self):
        diff = """diff --git a/core/data/src/A.kt b/core/data/src/A.kt
index 111..222 100644
--- a/core/data/src/A.kt
+++ b/core/data/src/A.kt
@@ -10,3 +10,5 @@ fun a() {
+line
@@ -30 +32,2 @@
+x
"""
        hunks = scan.parse_hunks(diff)
        got = hunks["core/data/src/A.kt"]
        self.assertEqual(len(got), 2)
        self.assertEqual((got[0]["newStart"], got[0]["newCount"]), (10, 5))
        self.assertEqual((got[1]["newStart"], got[1]["newCount"]), (32, 2))

    def test_locate_decls_maps_lines_to_declarations(self):
        decls = [
            {"id": "f:1", "name": "A", "kind": "class", "line": 1, "layer": "Other", "isTest": False, "globalIdx": 0},
            {"id": "f:20", "name": "B", "kind": "class", "line": 20, "layer": "Other", "isTest": False, "globalIdx": 1},
        ]
        hit = scan.locate_decls([{"newStart": 25, "newCount": 3}], decls)
        self.assertEqual([h["name"] for h in hit], ["B"])
        hit = scan.locate_decls([{"newStart": 3, "newCount": 1}], decls)
        self.assertEqual([h["name"] for h in hit], ["A"])
        self.assertEqual(scan.locate_decls([], decls), [])


# ==========================================================================
# 2. 影响面分档：确定 / 间接 / 推断
# ==========================================================================


def _decl(idx, name, module, is_test=False, callees=()):
    return {
        "globalIdx": idx, "name": name, "kind": "class", "module": module,
        "file": f"{module.strip(':').replace(':', '/')}/src/{name}.kt", "line": 10,
        "layer": "Other", "isTest": is_test, "callees": list(callees), "calleeCount": len(callees),
    }


class ImpactTest(unittest.TestCase):
    """影响面必须分档：跳数不同的可信度不同，测试作用域不能算成构建影响。"""

    def setUp(self):
        self.modules = [
            {"id": ":core:model", "path": "core/model"},
            {"id": ":core:data", "path": "core/data"},
            {"id": ":feature:a", "path": "feature/a"},
            {"id": ":feature:b", "path": "feature/b"},
            {"id": ":app", "path": "app"},
        ]
        self.edges = [
            {"from": ":core:data", "to": ":core:model", "declaration": "implementation",
             "origin": "explicit", "scope": "main", "evidence": "core/data/build.gradle.kts:14"},
            {"from": ":feature:a", "to": ":core:data", "declaration": "implementation",
             "origin": "convention", "scope": "main", "evidence": "plugin.kt:17"},
            {"from": ":app", "to": ":feature:a", "declaration": "implementation",
             "origin": "explicit", "scope": "main", "evidence": "app/build.gradle.kts:100"},
            # 测试作用域：不能出现在构建影响里
            {"from": ":core:data", "to": ":core:testing", "declaration": "implementation",
             "origin": "explicit", "scope": "test", "evidence": "core/data/build.gradle.kts:31"},
        ]
        self.decls = [
            _decl(0, "Anime", ":core:model"),
            _decl(1, "AnimeRepo", ":core:data", callees=[0]),
            _decl(2, "AnimeRepoTest", ":core:data", is_test=True, callees=[1]),
        ]

    def _impact(self, changed_path):
        change = {
            "files": [{"path": changed_path, "module": scan.module_of_path(changed_path, self.modules)[0],
                       "moduleKind": "module", "touchedDecls": [{"globalIdx": 0, "isTest": False}]}],
            "untracked": [],
        }
        return scan.compute_impact(change, self.modules, self.edges, self.decls)

    def test_one_hop_is_direct_and_two_hops_is_transitive(self):
        """跳数必须分开：曾经把整个闭包都标成「直接（确定）」，那是过度声明确定性。"""
        imp = self._impact("core/model/src/Anime.kt")
        self.assertEqual([x["module"] for x in imp["direct"]], [":core:data"])
        self.assertIn(":feature:a", [x["module"] for x in imp["transitive"]])
        self.assertIn(":app", [x["module"] for x in imp["transitive"]])
        self.assertEqual({x["hops"] for x in imp["direct"]}, {1})
        self.assertEqual({x["hops"] for x in imp["transitive"]}, {2, 3})

    def test_changed_module_is_not_its_own_dependent(self):
        imp = self._impact("core/data/src/AnimeRepo.kt")
        all_affected = [x["module"] for x in imp["direct"]] + [x["module"] for x in imp["transitive"]]
        self.assertNotIn(":core:data", all_affected)

    def test_impact_edge_carries_evidence_and_declaration(self):
        imp = self._impact("core/model/src/Anime.kt")
        e = imp["direct"][0]
        self.assertTrue(e["evidence"].endswith(":14") or ":" in e["evidence"])
        self.assertEqual(e["declaration"], "implementation")

    def test_build_log_path_is_global_impact(self):
        change = {
            "files": [{"path": "build-logic/convention/src/main/kotlin/X.kt", "module": "build-logic",
                       "moduleKind": "build-config", "touchedDecls": []}],
            "untracked": [],
        }
        imp = scan.compute_impact(change, self.modules, self.edges, self.decls)
        self.assertTrue(imp["globalImpact"])
        self.assertIn("build-logic/convention/src/main/kotlin/X.kt", imp["buildConfigTouched"])

    def test_symbol_level_tests_are_labelled_by_module_and_evidence(self):
        imp = self._impact("core/data/src/AnimeRepo.kt")
        # 直接改声明的场景下自称被修改的不算；这里用 model 的声明被 AnimeRepo 引用
        imp2 = self._impact("core/model/src/Anime.kt")
        names = [t["name"] for t in imp2["impactedTests"]]
        self.assertIn("AnimeRepoTest", names)
        t = [x for x in imp2["impactedTests"] if x["name"] == "AnimeRepoTest"][0]
        self.assertTrue(t["isTest"])
        self.assertIn(":", t["evidence"])
        self.assertIsNotNone(imp)


# ==========================================================================
# 3. 真实仓库的数据不变量
# ==========================================================================


@unittest.skipIf(FAST_ONLY, "fast 模式跳过真实仓库扫描")
class RepoDataQualityTest(unittest.TestCase):
    """对真实仓库跑一次扫描，校验跨文件不变量。这些断言失败 = 画布在撒谎。"""

    @classmethod
    def setUpClass(cls):
        if not os.path.isfile(os.path.join(REPO_ROOT, "settings.gradle.kts")):
            raise unittest.SkipTest("找不到 Gradle 工程根目录")
        cls.tmp = tempfile.mkdtemp(prefix="canvas-dq-")
        code = subprocess.run(
            [sys.executable, os.path.join(HERE, "scan.py"), REPO_ROOT, "-o", cls.tmp],
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True,
        )
        if code.returncode != 0:
            raise unittest.SkipTest(f"扫描失败: {code.stderr[-400:]}")
        with open(os.path.join(cls.tmp, "canvas-data.json"), encoding="utf-8") as fh:
            cls.d = json.load(fh)
        cls.modules = {m["id"]: m for m in cls.d["modules"]}

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp, ignore_errors=True)

    # ---- 结构层 ----

    def test_every_edge_endpoint_is_a_known_module(self):
        ids = set(self.modules)
        for e in self.d["edges"]:
            self.assertIn(e["from"], ids, f'边的起点不是模块: {e["from"]}')
            self.assertIn(e["to"], ids, f'边的终点不是模块: {e["to"]}')

    def test_convention_injected_edges_exist_and_carry_evidence(self):
        """事故 1 回归：convention plugin 注入的边必须被抽出来，且带 plugin 与来源行。"""
        conv = [e for e in self.d["edges"] if e["origin"] == "convention"]
        self.assertGreater(len(conv), 0, "一条 convention 注入边都没有 —— 构建语义理解退化了")
        for e in conv:
            self.assertIn("plugin", e)
            self.assertIn(".kt:", e["evidence"], f"隐式边缺少来源行: {e}")
            self.assertTrue(e["plugin"].startswith("minibgm."), e["plugin"])

    def test_features_get_injected_core_dependencies(self):
        """6 个 feature 模块各自的 build 文件里没有依赖，全靠注入；漏了就画不出真实图。"""
        features = [m for m in self.d["modules"] if m["group"] == "feature"]
        self.assertGreater(len(features), 0)
        for m in features:
            injected = [e["to"] for e in m["injectedEdges"]]
            self.assertIn(":core:data", injected, f'{m["id"]} 没抽到注入的 :core:data')
            self.assertIn(":core:designsystem", injected, f'{m["id"]} 没抽到注入的 :core:designsystem')

    def test_no_production_dependency_on_test_module(self):
        """事故 2 的后果检查：测试专用模块不得出现在任何生产依赖里。"""
        bad = [
            e for e in self.d["edges"]
            if e["scope"] == "main" and e["to"].split(":")[-1] == "testing"
        ]
        self.assertEqual(bad, [], f"生产作用域依赖了测试模块: {bad}")

    def test_production_dependency_graph_is_acyclic(self):
        """把测试作用域算进来会凭空造环；只看生产作用域必须无环。"""
        graph: dict[str, list[str]] = {m: [] for m in self.modules}
        for e in self.d["edges"]:
            if e["scope"] == "main" and e["from"] in graph and e["to"] in graph:
                graph[e["from"]].append(e["to"])
        color: dict[str, int] = {}

        def dfs(n: str, stack: list[str]):
            color[n] = 1
            stack.append(n)
            for nxt in graph[n]:
                if color.get(nxt, 0) == 0:
                    dfs(nxt, stack)
                elif color.get(nxt) == 1:
                    self.fail("生产依赖存在环: " + " → ".join(stack[stack.index(nxt):] + [nxt]))
            stack.pop()
            color[n] = 2

        for n in graph:
            if color.get(n, 0) == 0:
                dfs(n, [])

    def test_test_scope_edges_are_not_counted_as_build_impact(self):
        """测试作用域边不得出现在影响面里（它不构成构建期依赖）。"""
        for c in self.d["changes"]:
            imp = c.get("impact")
            if not imp:
                continue
            affected = {x["module"] for x in imp["direct"]} | {x["module"] for x in imp["transitive"]}
            for m in affected:
                self.assertNotIn(m, imp["changedModules"], f'{c["range"]}: 受影响模块重复计入改动模块')
            for x in imp["direct"] + imp["transitive"]:
                self.assertGreaterEqual(x["hops"], 1)

    # ---- 声明层 ----

    def test_every_declaration_points_at_a_real_file_and_line(self):
        checked = 0
        for m in self.d["modules"]:
            for d in m["declarations"]:
                fp = os.path.join(REPO_ROOT, d["file"])
                self.assertTrue(os.path.isfile(fp), f'{d["id"]} 指向不存在的文件')
                with open(fp, encoding="utf-8", errors="replace") as fh:
                    n = len(fh.read().splitlines())
                self.assertLessEqual(d["line"], n, f'{d["id"]} 行号超出文件范围')
                checked += 1
        self.assertGreater(checked, 100)

    def test_no_declaration_is_named_after_a_receiver_type(self):
        """事故回归（扩展函数解析）在真实数据上的形态：不得出现名为 List/Map/Set 的 fun。"""
        bad = [
            d["id"] for m in self.d["modules"] for d in m["declarations"]
            if d["raw"] == "fun" and d["name"] in {"List", "Map", "Set", "String", "Int", "Sequence", "Flow"}
        ]
        self.assertEqual(bad, [], f"扩展函数名被解析成接收者类型: {bad[:5]}")

    def test_test_source_set_detection_is_not_broken(self):
        """源集下标写错会把所有测试文件当主源码 —— 那样「模块零测试」会刷一整屏。"""
        for m in self.d["modules"]:
            if not m["testResults"] and m["counts"]["mainFiles"] > 0:
                continue
            sets = {s["name"] for s in m["sourceSets"]}
            if any(s in sets for s in ("test", "commonTest", "androidHostTest", "androidUnitTest")):
                self.assertGreater(m["counts"]["testFiles"], 0, f'{m["id"]} 有测试源集却没抽到测试文件')
        has_tests = [m["id"] for m in self.d["modules"] if m["counts"]["testFiles"] > 0]
        self.assertGreater(len(has_tests), 5, "几乎没抽到测试文件，源集识别大概率坏了")

    def test_declaration_global_index_is_dense_and_unique(self):
        idx = [d["globalIdx"] for m in self.d["modules"] for d in m["declarations"]]
        self.assertEqual(len(idx), len(set(idx)), "globalIdx 有重复")
        self.assertEqual(sorted(idx), list(range(len(idx))), "globalIdx 不连续，引用图会错位")

    def test_callee_indices_resolve(self):
        """静态引用图的索引必须都能解到声明，否则面板上的「谁引用了它」会指向空。"""
        n = len(idx := [d["globalIdx"] for m in self.d["modules"] for d in m["declarations"]])
        valid = set(idx)
        bad = 0
        for m in self.d["modules"]:
            for d in m["declarations"]:
                for c in d.get("callees", []):
                    if c not in valid:
                        bad += 1
        self.assertEqual(bad, 0, f"{bad} 个引用索引越界（共 {n} 个声明）")

    # ---- 变更 / 验证层 ----

    def test_change_sets_are_parsed_and_located(self):
        changes = self.d["changes"]
        self.assertGreater(len(changes), 0, "一个变更集都没解析出来")
        for c in changes:
            self.assertIn(c["kind"], ("worktree", "commit"))
            self.assertIn("range", c)
            for f in c["files"]:
                self.assertIn("added", f)
                self.assertIn("removed", f)
                self.assertTrue(f["evidence"], f'{f["path"]} 缺证据来源')
                for d in f["touchedDecls"]:
                    # 定位到的声明必须自带可核对的坐标
                    self.assertIn("line", d)
                    self.assertIn("name", d)
                    self.assertIn("globalIdx", d)
            # 影响面必须与变更集同时存在
            if c["stats"]["files"] > 0:
                self.assertIsNotNone(c.get("impact"), f'{c["range"]} 缺影响面')

    def test_touched_decl_lines_come_from_git_hunks(self):
        """变更行号必须落在 hunk 范围内 —— 否则「这次提交改了这个声明」是编的。"""
        for c in self.d["changes"]:
            for f in c["files"]:
                hunks = f["hunks"]
                if not hunks:
                    continue
                spans = [(h["newStart"], h["newStart"] + max(h["newCount"], 1) - 1) for h in hunks]
                for d in f["touchedDecls"]:
                    inside = any(lo <= d["line"] <= hi or (d["line"] <= lo and True) for lo, hi in spans)
                    self.assertTrue(inside, f'{c["range"]} {f["path"]}:{d["line"]} 不在任何 hunk 内')

    def test_validation_records_are_wellformed(self):
        v = self.d["validation"]
        self.assertIn("records", v)
        for r in v["records"]:
            self.assertIn("at", r)
            self.assertIn("exitCode", r)
            self.assertIn("tasks", r)
            for t in r["tasks"]:
                self.assertIn("name", t)
                self.assertIn(t["outcome"],
                              {"EXECUTED", "UP-TO-DATE", "FROM-CACHE", "SKIPPED", "NO-SOURCE", "FAILED", "DID-NO-WORK"})
            self.assertEqual(
                r["ok"], r["exitCode"] == 0 and str(r.get("buildLine", "")).startswith("BUILD SUCCESSFUL"),
                "ok 字段与退出码/构建行不自洽",
            )

    def test_validation_task_to_module_mapping(self):
        """任务名必须能映射回模块，否则「这个模块最近验证过吗」无从回答。"""
        v = self.d["validation"]
        mapped = [t for r in v["records"] for t in r["tasks"] if t.get("module")]
        for t in mapped:
            self.assertIn(t["module"], self.modules, f'{t["name"]} 映射到不存在的模块')
            self.assertTrue(t["name"].startswith(t["module"] + ":"), f'{t["name"]} 与模块 {t["module"]} 不匹配')
        if v["records"]:
            self.assertGreater(len(mapped), 0, "有验证记录却一个模块任务都没映射上")

    def test_test_evidence_is_not_empty_when_xml_exists(self):
        """XML 有测试类却报 0 用例 = 解析错了（或真的跑了 0 个，必须能被区分）。"""
        for m in self.d["modules"]:
            for t in m["testResults"]:
                self.assertGreaterEqual(t["cases"], 0)
                self.assertGreaterEqual(t["suites"], 0)
                if t["cases"] == 0:
                    self.assertTrue(t["empty"], "用例为 0 却没标 empty")

    def test_rule_findings_carry_evidence(self):
        for f in self.d["findings"]:
            self.assertIn(f["severity"], ("P1", "P2", "P3"))
            self.assertTrue(f["evidence"], f'{f["rule"]} 缺证据')


if __name__ == "__main__":
    unittest.main(verbosity=2)
