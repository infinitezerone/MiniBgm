# -*- coding: utf-8 -*-
import io
import os
import re

# ============ 1. 圆角野值归一（app + feature 主源集） ============
RADIUS_MAP = {'2': '4', '3': '4', '5': '4', '6': '8', '10': '8', '14': '12', '18': '16', '20': '16', '27': '24', '28': '24'}
call_re = re.compile(r'RoundedCornerShape\(([^()]*)\)')
num_re = re.compile(r'(\d+)\.dp')

changed = 0
roots = [r'app/src/main', r'feature']
for root in roots:
    for dirpath, dirnames, filenames in os.walk(root):
        if any(part in dirpath for part in ['/build', '\\build', '/node_modules']):
            continue
        dirnames[:] = [d for d in dirnames if d != 'build']
        for fn in filenames:
            if not fn.endswith('.kt'):
                continue
            fp = os.path.join(dirpath, fn)
            s = io.open(fp, encoding='utf-8').read()
            ns = call_re.sub(lambda m: 'RoundedCornerShape(' + num_re.sub(lambda n: RADIUS_MAP.get(n.group(1), n.group(1)) + '.dp', m.group(1)) + ')', s)
            if ns != s:
                io.open(fp, 'w', encoding='utf-8', newline='\n').write(ns)
                changed += 1
print('radius-normalized files:', changed)

# ============ 2. SemanticColors: 高亮与类型色板补暗色档 + 自适应助手 ============
p = r'core/designsystem/src/main/kotlin/com/infinitezerone/minibgm/core/designsystem/theme/SemanticColors.kt'
s = io.open(p, encoding='utf-8').read()

old = """// 搜索关键词高亮

val HighlightAmber = Color(0xFFD97706)
val HighlightContainer = Color(0xFFFFF3D0)
val OnHighlightContainer = Color(0xFFB25E00)

// 条目类型色板（container 为浅底，On 为其上的内容色）

val TypeBookContainer = Color(0xFFFFF3E0)
val OnTypeBook = Color(0xFFE65100)
val TypeAnimeContainer = Color(0xFFE3F2FD)
val OnTypeAnime = Color(0xFF1976D2)
val TypeMusicContainer = Color(0xFFF3E5F5)
val OnTypeMusic = Color(0xFF7B1FA2)
val TypeGameContainer = Color(0xFFE8F5E9)
val OnTypeGame = Color(0xFF2E7D32)
val TypeRealContainer = Color(0xFFFCE4EC)
val OnTypeReal = Color(0xFFC2185B)"""
new = """// 搜索关键词高亮（亮/暗双档：暗色档用暖褐深底 + 亮琥珀前景，避免浅黄底在纯黑上发糊）

val HighlightAmber = Color(0xFFD97706)
val HighlightContainerLight = Color(0xFFFFF3D0)
val OnHighlightContainerLight = Color(0xFFB25E00)
val HighlightContainerDark = Color(0xFF43330C)
val OnHighlightContainerDark = Color(0xFFFCD34D)
val HighlightContainer = HighlightContainerLight
val OnHighlightContainer = OnHighlightContainerLight

// 条目类型色板（container 为浅底，On 为其上的内容色；亮/暗双档）

val TypeBookContainer = Color(0xFFFFF3E0)
val OnTypeBook = Color(0xFFE65100)
val TypeBookContainerDark = Color(0xFF4E2606)
val OnTypeBookDark = Color(0xFFFFB68F)
val TypeAnimeContainer = Color(0xFFE3F2FD)
val OnTypeAnime = Color(0xFF1976D2)
val TypeAnimeContainerDark = Color(0xFF0D3B6F)
val OnTypeAnimeDark = Color(0xFF90CAF9)
val TypeMusicContainer = Color(0xFFF3E5F5)
val OnTypeMusic = Color(0xFF7B1FA2)
val TypeMusicContainerDark = Color(0xFF4A1B66)
val OnTypeMusicDark = Color(0xFFCE93D8)
val TypeGameContainer = Color(0xFFE8F5E9)
val OnTypeGame = Color(0xFF2E7D32)
val TypeGameContainerDark = Color(0xFF12402A)
val OnTypeGameDark = Color(0xFFA5D6A7)
val TypeRealContainer = Color(0xFFFCE4EC)
val OnTypeReal = Color(0xFFC2185B)
val TypeRealContainerDark = Color(0xFF59113D)
val OnTypeRealDark = Color(0xFFF48FB1)

// ==========
// 搜索关键词高亮自适应助手
// ==========

@Composable
@ReadOnlyComposable
fun highlightContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) HighlightContainerDark else HighlightContainerLight

@Composable
@ReadOnlyComposable
fun onHighlightContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnHighlightContainerDark else OnHighlightContainerLight

// ==========
// 条目类型色板自适应助手
// ==========

@Composable
@ReadOnlyComposable
fun typeBookContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeBookContainerDark else TypeBookContainer

@Composable
@ReadOnlyComposable
fun onTypeBookColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeBookDark else OnTypeBook

@Composable
@ReadOnlyComposable
fun typeAnimeContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeAnimeContainerDark else TypeAnimeContainer

@Composable
@ReadOnlyComposable
fun onTypeAnimeColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeAnimeDark else OnTypeAnime

@Composable
@ReadOnlyComposable
fun typeMusicContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeMusicContainerDark else TypeMusicContainer

@Composable
@ReadOnlyComposable
fun onTypeMusicColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeMusicDark else OnTypeMusic

@Composable
@ReadOnlyComposable
fun typeGameContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeGameContainerDark else TypeGameContainer

@Composable
@ReadOnlyComposable
fun onTypeGameColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeGameDark else OnTypeGame

@Composable
@ReadOnlyComposable
fun typeRealContainerColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TypeRealContainerDark else TypeRealContainer

@Composable
@ReadOnlyComposable
fun onTypeRealColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) OnTypeRealDark else OnTypeReal"""
assert old in s, 'semantic anchor'
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('semantic ok')

# ============ 3. SearchResultCards 切换到自适应助手 ============
p = r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SearchResultCards.kt'
s = io.open(p, encoding='utf-8').read()
for a, b in [
    ('import com.infinitezerone.minibgm.core.designsystem.theme.HighlightContainer', 'import com.infinitezerone.minibgm.core.designsystem.theme.highlightContainerColor'),
    ('import com.infinitezerone.minibgm.core.designsystem.theme.OnHighlightContainer', 'import com.infinitezerone.minibgm.core.designsystem.theme.onHighlightContainerColor'),
]:
    assert a in s, a
    s = s.replace(a, b)
s = s.replace('color = HighlightContainer,', 'color = highlightContainerColor(),')
s = s.replace('color = OnHighlightContainer,', 'color = onHighlightContainerColor(),')

# getSubjectTypeTheme 改走自适应助手
old = """fun getSubjectTypeTheme(subjectType: SubjectType): SubjectTypeColorTheme =
    when (subjectType) {
        SubjectType.BOOK ->
            SubjectTypeColorTheme(
                containerColor = TypeBookContainer,
                contentColor = OnTypeBook,
            )
        SubjectType.ANIME ->
            SubjectTypeColorTheme(
                containerColor = TypeAnimeContainer,
                contentColor = OnTypeAnime,
            )
        SubjectType.MUSIC ->
            SubjectTypeColorTheme(
                containerColor = TypeMusicContainer,
                contentColor = OnTypeMusic,
            )
        SubjectType.GAME ->
            SubjectTypeColorTheme(
                containerColor = TypeGameContainer,
                contentColor = OnTypeGame,
            )
        SubjectType.REAL ->
            SubjectTypeColorTheme(
                containerColor = TypeRealContainer,
                contentColor = OnTypeReal,
            )
    }"""
new = """@Composable
fun getSubjectTypeTheme(subjectType: SubjectType): SubjectTypeColorTheme =
    when (subjectType) {
        SubjectType.BOOK ->
            SubjectTypeColorTheme(
                containerColor = typeBookContainerColor(),
                contentColor = onTypeBookColor(),
            )
        SubjectType.ANIME ->
            SubjectTypeColorTheme(
                containerColor = typeAnimeContainerColor(),
                contentColor = onTypeAnimeColor(),
            )
        SubjectType.MUSIC ->
            SubjectTypeColorTheme(
                containerColor = typeMusicContainerColor(),
                contentColor = onTypeMusicColor(),
            )
        SubjectType.GAME ->
            SubjectTypeColorTheme(
                containerColor = typeGameContainerColor(),
                contentColor = onTypeGameColor(),
            )
        SubjectType.REAL ->
            SubjectTypeColorTheme(
                containerColor = typeRealContainerColor(),
                contentColor = onTypeRealColor(),
            )
    }"""
assert old in s, 'subjectTypeTheme anchor'
s = s.replace(old, new)
# 清理不再使用的静态导入，换成未用的会告警——先探测
for name in ['TypeBookContainer', 'OnTypeBook', 'TypeAnimeContainer', 'OnTypeAnime', 'TypeMusicContainer', 'OnTypeMusic', 'TypeGameContainer', 'OnTypeGame', 'TypeRealContainer', 'OnTypeReal']:
    s = s.replace('import com.infinitezerone.minibgm.core.designsystem.theme.' + name + '\n', '')
# 加入新助手导入
s = s.replace(
    'import com.infinitezerone.minibgm.core.designsystem.theme.highlightContainerColor',
    'import com.infinitezerone.minibgm.core.designsystem.theme.highlightContainerColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.onTypeAnimeColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.onTypeBookColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.onTypeGameColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.onTypeMusicColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.onTypeRealColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.typeAnimeContainerColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.typeBookContainerColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.typeGameContainerColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.typeMusicContainerColor\nimport com.infinitezerone.minibgm.core.designsystem.theme.typeRealContainerColor')
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('search cards ok')

# ============ 4. 海报/立绘比例令牌 ============
tok = r'core/designsystem/src/main/kotlin/com/infinitezerone/minibgm/core/designsystem/theme/Dimens.kt'
io.open(tok, 'w', encoding='utf-8', newline='\n').write('''package com.infinitezerone.minibgm.core.designsystem.theme

/**
 * 内容图片的宽高比令牌（width / height）。
 *
 * - [BgmPosterAspectRatio]：条目海报（新番网格、搜索结果、排期卡、AI 提案卡等）。
 *   当前全站统一为 0.7（约 5:7）；若设计上要对齐标准 2:3（0.667），改这一个常量即可全站生效。
 * - [BgmPortraitAspectRatio]：人物立绘（声优/角色头像），比海报更瘦高一点。
 *
 * 严禁在 feature 里手写比例字面量——比例属于令牌，改版只改这里。
 */
const val BgmPosterAspectRatio = 0.7f

/** 人物立绘宽高比（3:4 偏方，略瘦于海报） */
const val BgmPortraitAspectRatio = 0.72f
''')

ratio_map = {
    '0.7f': 'BgmPosterAspectRatio',
    '0.72f': 'BgmPortraitAspectRatio',
    '2f / 3f': 'BgmPosterAspectRatio',
}
ratio_files = {
    r'feature/assistant/src/main/kotlin/com/infinitezerone/minibgm/feature/assistant/components/PendingActionCard.kt': ['0.72f'],
    r'feature/schedule/src/main/kotlin/com/infinitezerone/minibgm/feature/schedule/components/ScheduleTimelineComponents.kt': ['0.7f'],
    r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SeasonalAnimeCard.kt': ['2f / 3f'],
    r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SearchResultCards.kt': ['0.7f'],
    r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/WaterfallSubjectCard.kt': ['0.72f'],
    r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SeasonalAnimeRow.kt': ['0.7f'],
    r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/CharacterDetailBottomSheet.kt': ['0.72f'],
    r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/RelatedWorksSection.kt': ['0.72f'],
    r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectHeaderSection.kt': ['0.7f'],
    r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectCastSection.kt': ['0.7f', '0.72f'],
}
for fp, ratios in ratio_files.items():
    s = io.open(fp, encoding='utf-8').read()
    for r in ratios:
        s = s.replace('aspectRatio = ' + r + ',', 'aspectRatio = ' + ratio_map[r] + ',')
    if 'import com.infinitezerone.minibgm.core.designsystem.theme.BgmPosterAspectRatio' not in s:
        anchor = 'import com.infinitezerone.minibgm.core.designsystem.'
        idx = s.find(anchor)
        assert idx >= 0, fp
        line_end = s.find('\n', idx)
        s = s[:line_end + 1] + 'import com.infinitezerone.minibgm.core.designsystem.theme.BgmPortraitAspectRatio\nimport com.infinitezerone.minibgm.core.designsystem.theme.BgmPosterAspectRatio\n' + s[line_end + 1:]
    # 删除未用的那个导入
    if 'BgmPortraitAspectRatio' not in s.replace('import com.infinitezerone.minibgm.core.designsystem.theme.BgmPortraitAspectRatio\n', ''):
        s = s.replace('import com.infinitezerone.minibgm.core.designsystem.theme.BgmPortraitAspectRatio\n', '')
    if 'BgmPosterAspectRatio' not in s.replace('import com.infinitezerone.minibgm.core.designsystem.theme.BgmPosterAspectRatio\n', ''):
        s = s.replace('import com.infinitezerone.minibgm.core.designsystem.theme.BgmPosterAspectRatio\n', '')
    io.open(fp, 'w', encoding='utf-8', newline='\n').write(s)
print('ratio tokens ok')

# CoverImage 默认值保持 0.7（与令牌同值），注释指向令牌
p = r'core/designsystem/src/main/kotlin/com/infinitezerone/minibgm/core/designsystem/component/CoverImage.kt'
s = io.open(p, encoding='utf-8').read()
if 'aspectRatio: Float = 0.7f' in s:
    s = s.replace(
        'aspectRatio: Float = 0.7f',
        'aspectRatio: Float = BgmPosterAspectRatio,')
    if 'import com.infinitezerone.minibgm.core.designsystem.theme.BgmPosterAspectRatio' not in s:
        idx = s.find('import com.infinitezerone.minibgm.core.designsystem.')
        line_end = s.find('\n', idx)
        s = s[:line_end + 1] + 'import com.infinitezerone.minibgm.core.designsystem.theme.BgmPosterAspectRatio\n' + s[line_end + 1:]
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('coverimage ok')
else:
    print('coverimage: pattern not found, check manually')
