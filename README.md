# rikka-files

杂物文件箱：临时文件、转交材料、一次性的包都放这里。

- 文件全部走 **Releases** 附件，仓库本体不存大文件
- 每个 Release 是一批交付，标题写清日期和内容
- 下载后请核对文件大小，大小对不上就是没传完

六花日语的 APK 不在这里，在 SukiSU-rikka/rikka-apk。

---

## 六花追番（临时放在这里）

记录和统计看过的动漫的安卓 App。第一次打开会自动导入「动漫记录表」里 2024–2026 年的 422 条记录（329 部作品），已经预先匹配好 Bangumi 的封面、集数、类型和制作公司。

**功能**
- 片单：按年份 / 按作品 / 不分组，按状态筛选（在看、想看、看完、搁置、弃坑），搜索
- 每条可记：评分（1–10）、开始/看完日期、观看进度（列表里直接 +1）、重看次数、短评
- 统计：作品数和条目数、总集数、总时长、每年看了多少、今年每月、评分分布、类型、常看标签、制作公司、最长系列
- Bangumi：添加新番时直接搜 bgm.tv；自动匹配不确定的在「更多 → 检查匹配结果」里确认
- 备份：导出 / 恢复 JSON，导出 CSV（Excel 能直接打开）

**安装**：推送后 GitHub Actions 自动编译，在 Actions 页面下载 `六花追番` artifact。签名固定用 `app/animelog.jks`，以后更新直接覆盖安装。

**本地构建**：AGP 8.13.2 + Kotlin 2.2.21 + Gradle 8.13，compileSdk 36，JDK 17。`gradle assembleRelease`。

**数据**
- 记录全部存在手机的 `files/library.json` 里，没有服务器
- 原始记录：`tools/动漫记录表.txt`；`tools/parse_list.py` 把它拆成「作品 + 季/篇」，生成 `app/src/main/assets/seed.json`
- 重新生成后，用 `gradle :app:testDebugUnitTest --tests '*MatchTest.matchSeed' -PseedMatch=1` 联网补上 Bangumi 信息，
  再用 `--tests '*MatchTest.applySeedFixes' -PseedMatch=1` 套上 `tools/seed_fixes.json` 里人工复核过的修正
- 已装旧版的手机：种子只在第一次打开时导入，所以种子的修正由 `SeedMigrations.kt` 在升级后第一次启动时补上
  （只改还停在旧错配上的条目，手动改过的不动；升级前的数据另存为 `files/library.v1.json`）

**更新记录**
- 1.1：修正 7 条 Bangumi 错配（无职转生第二季两部分、砂糖的幸福生活、四个人各自有着自己的秘密、最后的大魔王、夜露死驱少女、古利特宇宙）；无职转生四条改名为第一季/第二季的第1、第2部分
- 1.0：首版
- `UiShotTest` 用 Robolectric 在电脑上渲染界面截图（输出到 `app/build/shots/`）
