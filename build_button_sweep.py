import io, re

FILES = [
 r'feature/assistant/src/main/kotlin/com/infinitezerone/minibgm/feature/assistant/AssistantScreenParts.kt',
 r'feature/assistant/src/main/kotlin/com/infinitezerone/minibgm/feature/assistant/components/AssistantConfigDialog.kt',
 r'feature/assistant/src/main/kotlin/com/infinitezerone/minibgm/feature/assistant/components/ModelPickerDialog.kt',
 r'feature/assistant/src/main/kotlin/com/infinitezerone/minibgm/feature/assistant/components/PendingActionCard.kt',
 r'feature/schedule/src/main/kotlin/com/infinitezerone/minibgm/feature/schedule/components/ScheduleStatusNotes.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/AddSeasonalTagBottomSheet.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/ExploreFilterPills.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/ExploreFilterBottomSheet.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/ExploreSubjectRow.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SearchHeaderAndTabs.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SearchIdleDiscovery.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SearchResultsContainer.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SeasonalAnimeCard.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SeasonalFilterOptionsBottomSheet.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/components/SeasonPickerBottomSheet.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/SearchStatusViews.kt',
 r'feature/search/src/main/kotlin/com/infinitezerone/minibgm/feature/search/SeasonalGuideScreen.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/PlaybackRuleDialogs.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/AiringTimingBottomSheet.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/AiSettingsDialog.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/DanmakuSettingsDialog.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/DeveloperOptionsBottomSheet.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/UserAccountSheet.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/UserAuthCard.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/components/VideoTaskDetailBottomSheet.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/DownloadScreen.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/InAppWebChrome.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/PlaybackRulesScreen.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/SettingsScreen.kt',
 r'feature/user/src/main/kotlin/com/infinitezerone/minibgm/feature/user/SubjectDownloadDetailScreen.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/EpisodeDetailScreen.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/SubjectDetailScreen.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/TopicDetailScreen.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/EpisodeCommentsSection.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/EpisodeQuickActionBottomSheet.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/RelatedWorksSection.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SourceStateViews.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectCastSection.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectCollectionCard.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectDetailContent.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectHeaderSection.kt',
 r'feature/subject/src/main/kotlin/com/infinitezerone/minibgm/feature/subject/components/SubjectSourcesBottomSheet.kt',
]

BACKSLASH = chr(92)
NL = chr(10)
VARIANT = {'Button': 'Primary', 'FilledTonalButton': 'Tonal', 'OutlinedButton': 'Outlined', 'TextButton': 'Secondary'}
CUSTOM_KEYS = {'colors', 'shape', 'contentPadding', 'border', 'elevation', 'interactionSource', 'colors='}

def line_of(s, idx):
    return s.count(NL, 0, idx) + 1

def split_top_level(s):
    parts, depth, cur, i, in_str = [], 0, [], 0, None
    while i < len(s):
        ch = s[i]
        if in_str:
            cur.append(ch)
            if ch == BACKSLASH and i + 1 < len(s):
                cur.append(s[i + 1])
                i += 2
                continue
            if ch == in_str:
                in_str = None
            i += 1
            continue
        if ch == '"':
            in_str = ch
            cur.append(ch)
            i += 1
            continue
        if ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(''.join(cur))
            cur = []
        else:
            cur.append(ch)
        i += 1
    if cur:
        parts.append(''.join(cur))
    return [p.strip() for p in parts if p.strip()]

def parse_args(s):
    d = {}
    for i, p in enumerate(split_top_level(s)):
        m = re.match(r'^([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(.*)$', p, re.S)
        if m:
            d[m.group(1)] = m.group(2).strip()
        else:
            d[str(i)] = p
    return d

def extract_single_text(body):
    body = body.strip()
    if body.startswith('//') or chr(10) not in body and not body.startswith('Text('):
        pass
    m = re.match(r'^Text\s*\(', body)
    if not m or not body.endswith(')'):
        return None
    depth, j = 1, m.end()
    while j < len(body) and depth > 0:
        if body[j] == '(':
            depth += 1
        elif body[j] == ')':
            depth -= 1
        j += 1
    if j != len(body):
        return None
    inner = body[m.end():j - 1]
    if 'Text(' in inner or 'Icon(' in inner:
        return None
    a = parse_args(inner)
    text = a.get('0') or a.get('text')
    if text is None or a.get('style') or a.get('color'):
        return None
    return text

total_ok = 0
total_skip = 0
skip_report = []
for path in FILES:
    s = io.open(path, encoding='utf-8').read()
    out = []
    i = 0
    n_ok = n_skip = 0
    pat = re.compile(r'(?<![A-Za-z0-9_])(Button|TextButton|OutlinedButton|ElevatedButton|FilledTonalButton)\s*\(')
    while True:
        m = pat.search(s, i)
        if not m:
            out.append(s[i:])
            break
        start = m.start()
        comp = m.group(1)
        line_start = s.rfind(NL, 0, start) + 1
        if '//' in s[line_start:start]:
            out.append(s[i:m.end()])
            i = m.end()
            continue
        depth, j = 1, m.end()
        while j < len(s) and depth > 0:
            if s[j] == '(':
                depth += 1
            elif s[j] == ')':
                depth -= 1
            j += 1
        args_src = s[m.end():j - 1]
        k = j
        while k < len(s) and s[k] in ' ' + NL + chr(9):
            k += 1
        end = j
        lambda_src = None
        if k < len(s) and s[k] == '{':
            depth, j2, in_str = 1, k + 1, None
            while j2 < len(s) and depth > 0:
                ch = s[j2]
                if in_str:
                    if ch == BACKSLASH:
                        j2 += 2
                        continue
                    if ch == in_str:
                        in_str = None
                elif ch == '"':
                    in_str = ch
                elif ch == '{':
                    depth += 1
                elif ch == '}':
                    depth -= 1
                j2 += 1
            lambda_src = s[k + 1:j2 - 1]
            end = j2
        bargs = parse_args(args_src)
        custom = any(k2 in bargs for k2 in ['colors', 'shape', 'contentPadding', 'border', 'elevation', 'interactionSource'])
        text = extract_single_text(lambda_src) if lambda_src is not None else None
        if text is None or custom:
            out.append(s[i:end])
            i = end
            n_skip += 1
            skip_report.append(path + ':' + str(line_of(s, start)) + ' (' + comp + (' custom-args' if custom else ' non-single-Text') + ')')
            continue
        onClick = bargs.get('onClick') or bargs.get('0')
        parts = ['onClick = ' + onClick, 'text = ' + text]
        if bargs.get('modifier'):
            parts.append('modifier = ' + bargs['modifier'])
        if bargs.get('enabled'):
            parts.append('enabled = ' + bargs['enabled'])
        variant = VARIANT[comp]
        call = 'BgmPillButton(' + ', '.join(parts)
        if variant != 'Primary':
            call += ', variant = BgmPillVariant.' + variant
        call += ')'
        out.append(s[i:start])
        out.append(call)
        i = end
        n_ok += 1
    s2 = ''.join(out)
    for comp in ['Button', 'TextButton', 'OutlinedButton', 'ElevatedButton', 'FilledTonalButton']:
        if not re.search(r'(?<![A-Za-z0-9_])' + comp + r'\s*\(', s2):
            s2 = s2.replace('import androidx.compose.material3.' + comp + NL, '', 1)
    if 'BgmPillButton(' in s2 and 'import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton' not in s2:
        m = re.search(r'^import com\.infinitezerone\.minibgm\.core\.designsystem\.', s2, re.M)
        s2 = s2[:m.start()] + 'import com.infinitezerone.minibgm.core.designsystem.component.BgmPillButton' + NL + s2[m.start():]
    if 'BgmPillVariant.' in s2 and 'import com.infinitezerone.minibgm.core.designsystem.component.BgmPillVariant' not in s2:
        m = re.search(r'^import com\.infinitezerone\.minibgm\.core\.designsystem\.component\.BgmPillButton' + NL, s2, re.M)
        s2 = s2[:m.end()] + 'import com.infinitezerone.minibgm.core.designsystem.component.BgmPillVariant' + NL + s2[m.end():]
    if s2 != s:
        io.open(path, 'w', encoding='utf-8', newline=NL).write(s2)
    total_ok += n_ok
    total_skip += n_skip
    if n_ok or n_skip:
        print(path.split('/')[-1] + ': migrated=' + str(n_ok) + ' skipped=' + str(n_skip))
print('TOTAL migrated:', total_ok, 'skipped:', total_skip)
for x in skip_report:
    print('SKIP', x)
