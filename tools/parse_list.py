#!/usr/bin/env python3
"""把「动漫记录表.txt」解析成 App 的初始数据 seed.json。

用法：python3 tools/parse_list.py tools/动漫记录表.txt app/src/main/assets/seed.json

每一行「标题——范围」会拆成「作品(series) + 条目(part)」：
  进击的巨人——第二季全部          -> 进击的巨人 / 第二季
  轻音少女——全两季全部            -> 轻音少女 / 第一季、第二季
  中二病也要谈恋爱——两季加剧场版全部 -> 第一季、第二季、剧场版
原文保存在每条的 source 字段里，App 详情页能看到。
"""
import json
import re
import sys

CN = "零一二三四五六七八九十"


def cn_num(n: int) -> str:
    if n <= 10:
        return CN[n]
    if n < 20:
        return "十" + CN[n - 10]
    return CN[n // 10] + "十" + (CN[n % 10] if n % 10 else "")


def to_int(s: str) -> int:
    if s.isdigit():
        return int(s)
    s = s.replace("两", "二")
    if s == "十":
        return 10
    if s.startswith("十"):
        return 10 + CN.index(s[1])
    if "十" in s:
        a, b = s.split("十")
        return CN.index(a) * 10 + (CN.index(b) if b else 0)
    return CN.index(s)


# 同一系列的不同叫法 -> 统一的作品名。按前缀匹配，长的优先。
SERIES = {
    "辉夜大小姐想让我告白": "辉夜大小姐想让我告白",
    "约会大作战": "约会大作战",
    "约战狂三外传": "约会大作战",
    "关于我转生变成史莱姆这档事": "关于我转生变成史莱姆这档事",
    "转生史莱姆日记": "关于我转生变成史莱姆这档事",
    "剧场版 鬼灭之刃": "鬼灭之刃",
    "鬼灭之刃": "鬼灭之刃",
    "新世纪福音战士": "新世纪福音战士",
    "化物语": "物语系列",
    "伪物语": "物语系列",
    "猫物语": "物语系列",
    "灰色的果实": "灰色系列",
    "灰色的迷宫": "灰色系列",
    "灰色的乐园": "灰色系列",
    "灰色：幻影扳机": "灰色：幻影扳机",
    "为美好的世界献上爆焰": "为美好的世界献上祝福",
    "为美好的世界献上祝福": "为美好的世界献上祝福",
    "SSSS.古立特": "SSSS系列",
    "SSSS.电光机王": "SSSS系列",
    "古利特宇宙": "SSSS系列",
    "侦探歌剧 少女福尔摩斯": "侦探歌剧 少女福尔摩斯",
    "机动战士高达 水星的魔女": "机动战士高达 水星的魔女",
    "Love Live!": "Love Live!",
    "Re:从零开始的异世界生活": "Re:从零开始的异世界生活",
    "潜行吧！奈亚子": "潜行吧！奈亚子",
    "电锯人": "电锯人",
    "进击的巨人": "进击的巨人",
    "刀剑神域": "刀剑神域",
    "甲铁城的卡巴内里": "甲铁城的卡巴内里",
    "FLCL": "FLCL",
    "东京喰种": "东京喰种",
    "黑岩射手": "黑岩射手",
    "告白实行委员会": "告白实行委员会",
}

# 整行特判：标题 -> (作品, 条目)
EXACT = {
    "化物语": ("物语系列", "化物语"),
    "伪物语": ("物语系列", "伪物语"),
    "猫物语 黑": ("物语系列", "猫物语 黑"),
    "灰色的果实": ("灰色系列", "灰色的果实"),
    "灰色的迷宫": ("灰色系列", "灰色的迷宫"),
    "灰色的乐园": ("灰色系列", "灰色的乐园"),
    "SSSS.古立特": ("SSSS系列", "SSSS.古立特"),
    "SSSS.电光机王": ("SSSS系列", "SSSS.电光机王"),
    "古利特宇宙": ("SSSS系列", "古利特宇宙（剧场版）"),
    "为美好的世界献上爆焰！": ("为美好的世界献上祝福", "爆焰！"),
    "转生史莱姆日记": ("关于我转生变成史莱姆这档事", "转生史莱姆日记"),
    "约战狂三外传": ("约会大作战", "约战狂三外传"),
    "约会大作战万由里裁决": ("约会大作战", "剧场版 万由里裁决"),
    "Love Live!School Idol Project": ("Love Live!", ""),
    "Love Live!学园偶像 电影": ("Love Live!", "剧场版"),
    "BanG Dream! It's MyGO!!!!!": ("BanG Dream! It's MyGO!!!!!", ""),
}

PART_RE = re.compile(
    r"^(?P<series>.+?)[\s·：:]*(?P<part>"
    r"第[一二三四五六七八九十两\d]+季.*|最终季.*|剧场版.*|OVA\d*|OAD|W|Extra Edition)$"
)


def norm_part(p: str) -> str:
    p = p.strip(" ·：:")
    m = re.match(r"^第([一二三四五六七八九十两\d]+)季(.*)$", p)
    if m:
        rest = m.group(2).strip()
        rest = re.sub(r"(?i)^part\s*(\d)", r"Part.\1", rest)
        p = f"第{cn_num(to_int(m.group(1)))}季" + (f" {rest}" if rest else "")
    p = re.sub(r"(?i)part\s*(\d)", r"Part.\1", p)
    p = re.sub(r"^(最终季)(?=\S)", r"\1 ", p)
    if p == "W":
        p = "第二季（W）"
    return p


def split_title(title: str):
    if title in EXACT:
        return EXACT[title]
    for key in sorted(SERIES, key=len, reverse=True):
        if title.startswith(key):
            rest = title[len(key):]
            return SERIES[key], norm_part(rest)
    m = PART_RE.match(title)
    if m and m.group("series"):
        return m.group("series").strip(), norm_part(m.group("part"))
    return title, ""


# 整行特判：原文 -> 条目列表（人工复核过的拆法）
RANGE_OVERRIDE = {
    # 「全二季」指第一季的两部分，「OVA」实际是第二季第1部分（v1.1 复核）
    "无职转生——全二季全部加OVA": ["第一季 第1部分", "第一季 第2部分", "第二季 第1部分"],
    "无职转生第二季——全部": ["第二季 第2部分"],
}


def expand_range(rng: str):
    """范围文本 -> [条目, ...]；None 表示沿用标题里的条目。"""
    rng = rng.strip()
    if rng == "全部":
        return None
    parts = []
    m = re.match(r"^全?([一二三两\d])季(?:全部)?", rng)
    if m:
        for i in range(1, to_int(m.group(1)) + 1):
            parts.append(f"第{cn_num(i)}季")
    m2 = re.match(r"^第([一二三四五六七八九十\d]+)季(.*?)全部", rng)
    if m2:
        rest = m2.group(2)
        parts.append(norm_part(f"第{m2.group(1)}季{rest}"))
    if rng.startswith("全部"):
        parts.append("")
    if "剧场版" in rng:
        parts.append("剧场版")
    if "OVA" in rng:
        parts.append("OVA")
    return parts


def main(src, dst):
    entries = []
    year = None
    order = 0
    for raw in open(src, encoding="utf-8"):
        line = raw.strip()
        if not line or line == "动漫记录表":
            continue
        m = re.match(r"^═+\s*(\d{4})年版\s*═+$", line)
        if m:
            year = int(m.group(1))
            continue
        title, _, rng = line.partition("——")
        title = title.strip()
        note = ""
        rewatch = 0
        m = re.search(r"[（(]([^）)]*)[）)]$", title)
        if m and m.group(1) in ("二刷", "AI做的"):
            note = m.group(1)
            if m.group(1) == "二刷":
                rewatch = 1
            title = title[: m.start()].strip()
        series, title_part = split_title(title)
        parts = RANGE_OVERRIDE.get(line) or expand_range(rng)
        if parts is None:
            parts = [title_part]
        elif title_part and parts == [""]:
            parts = [title_part]
        for p in parts:
            order += 1
            entries.append({
                "id": order,
                "series": series,
                "part": p,
                "status": "DONE",
                "year": year,
                "order": order,
                "rewatch": rewatch,
                "note": note,
                "source": line,
            })
    json.dump({"version": 2, "entries": entries}, open(dst, "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print(f"{len(entries)} 条目, {len({e['series'] for e in entries})} 部作品", file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
