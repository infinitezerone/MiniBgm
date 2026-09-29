#!/usr/bin/env python3
"""探针：实测 Bangumi `POST /v0/search/subjects` 的真实行为，而不是照官方 schema 推断。

背景：官方 schema 自称「实验性 API，本 schema 和实际的 API 行为都可能随时发生改动」。
实测确实如此——文档承诺的能力未必落地，文档没写的限制却真实存在。
季度片单的取数策略建立在这些结论上，所以它们必须可复跑、可证伪。

跑法（只用标准库，无需装依赖）：
    python tools/bgm-api-probe/probe_search_api.py

退出码 0 = 既有结论仍成立；1 = 有结论已失效，需要复核取数策略。结论见同目录 README.md。
"""

import json
import sys
import time
import urllib.error
import urllib.request

BASE = "https://api.bgm.tv/v0/search/subjects"
# Bangumi 要求带可识别的 User-Agent；不设或设成默认值容易被限流。
UA = "MiniBgm-api-probe (+https://github.com/infinitezerone/MiniBgm)"

# 2026 夏（7月番）。按 App 的季界口径（每月 21 日）取窗口，总量实测 249。
SEASON = (">=2026-06-21", "<=2026-09-20")
# 超大结果集，用来探 total 的硬顶
HUGE = (">=2000-01-01", "<=2026-12-20")

THROTTLE_SECONDS = 1.3
_last_request_at = [0.0]

# 文档未声明、实测存在的单页硬上限
PAGE_CAP = 20
# meilisearch 的 maxTotalHits 默认值，实测体现为 total 的硬顶
TOTAL_CAP = 1000


def post(limit=1, offset=0, air=SEASON, meta_tags=None, tag=None, nsfw=False, sort="heat"):
    """发一次高级搜索。返回 (total, 实际返回条数)；出错时 total 为错误描述、条数为 None。"""
    gap = time.time() - _last_request_at[0]
    if gap < THROTTLE_SECONDS:
        time.sleep(THROTTLE_SECONDS - gap)
    _last_request_at[0] = time.time()

    filt = {"type": [2], "air_date": [air[0], air[1]], "nsfw": nsfw}
    if meta_tags is not None:
        filt["meta_tags"] = meta_tags
    if tag is not None:
        filt["tag"] = tag
    body = json.dumps({"sort": sort, "filter": filt}).encode()
    url = "%s?limit=%d&offset=%d" % (BASE, limit, offset)

    last = None
    # SSL EOF / 连接被对端掐断都偶发（实测十几次里会撞上一两次），退避重试几轮再认输，
    # 否则单次抖动会让下面的结论行缺一块、看不出对比
    for attempt in range(4):
        req = urllib.request.Request(
            url,
            data=body,
            method="POST",
            headers={
                "User-Agent": UA,
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                payload = json.loads(r.read().decode())
                return payload.get("total"), len(payload.get("data") or [])
        except urllib.error.HTTPError as e:
            return "HTTP %d" % e.code, None
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (attempt + 1))
    return "ERR %s" % str(last)[:60], None


def line(label, total, count):
    print("  %-44s total=%-8s returned=%s" % (label, total, count))


def main():
    failures = []

    print("1. limit 硬上限（官方 schema 未声明 maximum，实测有）")
    season_total, _ = post(limit=1)
    print("   季总量（2026 夏）= %s" % season_total)
    for limit in (20, 25, 30, 50, 100):
        _, count = post(limit=limit)
        print("   limit=%-4d -> 实际返回 %s 条" % (limit, count))
        if count != PAGE_CAP:
            failures.append("limit=%d 返回 %s 条，不再等于 %d —— 单页上限可能已放开" % (limit, count, PAGE_CAP))

    print()
    print("2. 深 offset（季量级应完全可用）")
    for offset in (0, 100, 200):
        total, count = post(limit=20, offset=offset)
        line("offset=%-4d limit=20" % offset, total, count)
        if count != 20:
            failures.append("offset=%d 未返回满页 —— 深分页可能已被限制" % offset)
    total, count = post(limit=20, offset=300)
    line("offset=300 limit=20（超出总量，应为 0）", total, count)
    if count != 0:
        failures.append("offset 超出总量仍返回 %s 条 —— 分页语义可能已变化" % count)

    print()
    print("3. total 硬顶（meilisearch maxTotalHits 默认 1000）")
    huge_total, _ = post(limit=1, air=HUGE)
    print("   air_date >= 2000 的 total = %s" % huge_total)
    if huge_total != TOTAL_CAP:
        failures.append("超大结果集 total=%s，不再等于 %d —— 硬顶可能已变化" % (huge_total, TOTAL_CAP))

    print()
    print("4. meta_tags 的 - 排除语法：官方文档承诺，实测未实现")
    plain, _ = post(limit=1)
    movie, _ = post(limit=1, meta_tags=["剧场版"])
    short, _ = post(limit=1, meta_tags=["短片"])
    excluded, _ = post(limit=1, meta_tags=["-剧场版"])
    print("   无标签 total=%s / [剧场版]=%s / [短片]=%s" % (plain, movie, short))
    line('["-剧场版"]', excluded, None)
    if isinstance(plain, int) and isinstance(movie, int):
        print("   若排除生效，这里应约等于 %d - %d = %d" % (plain, movie, plain - movie))
    if excluded == 0:
        print("   -> 确认：'-' 被当作字面标签名，匹配不到任何条目，排除语义没有落地。")
    elif isinstance(excluded, int) and isinstance(plain, int) and excluded < plain:
        failures.append('["-剧场版"] 返回 %s（< 总数 %s）—— 排除语义似乎已生效，取数策略可以简化' % (excluded, plain))

    print()
    print("5. meta_tags 多值 = AND（季度片单据此判定「或」关系无法下推）")
    both, _ = post(limit=1, meta_tags=["剧场版", "短片"])
    line('["剧场版","短片"]（AND，应无交集）', both, None)
    if both != 0:
        failures.append('["剧场版","短片"] 返回 %s —— AND 语义可能已变化' % both)

    print()
    if failures:
        print("!! 有 %d 条既有结论不再成立，季度片单的取数策略需要复核：" % len(failures))
        for item in failures:
            print("   - %s" % item)
        return 1
    print("全部结论与 README 一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
