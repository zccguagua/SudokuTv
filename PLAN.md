# 数独 TV（九宫题库版）实施计划

> 目标：以 `sudoku-assistant-tv` 的九宫做题模式为蓝本，在 `sudokuTv` 新建一个 Android TV 应用，使用
> `sudoku-exchange-puzzle-bank` 作为内置题库，并新增「关卡选择」「一键清除填写」「下一题」。

## 0. 一句话结论

可以做。九宫做题部分（棋盘、数字面板、预选数字、计时、自动校验、排行榜）几乎可以整体搬过来，
需要重写的是「题目来源」和「首页/关卡选择」；题库不能直接塞进 APK，需要先做一个离线预处理，
把它压成定长二进制记录。计划按 9 个阶段推进，每阶段都有可验收的产出。

## 0.1 实施进度

- [x] **阶段 0 工程骨架**：Gradle 脚手架、`com.zcc.sudokutv`、TV Manifest、主题/图标/横幅、占位界面；
      `./gradlew :app:assembleDebug` 已通过。
- [x] **阶段 1 题库预处理**：题库已一次性转换为 4 个 42 字节定长记录文件并随仓库提交。
      交付时保留全量（100000/352643/321592/119681），后续为控制安装包体积统一裁剪为**每难度前 20000 题**
      （合计 80000 题，四个 `.bin` 各 840,000 字节）；`diabolical#12070` 回读校验通过。
- [x] **阶段 2 模型与数据层**：`Puzzle`/`SudokuRules`/`GameProgress`/`PuzzleBank`/两个仓库；
      单测覆盖记录解码、规则冲突、存档编解码、成绩与按难度槽位。
- [x] **阶段 3 做题界面**：九宫棋盘、同宫高亮、错误标红、数字面板（默认 5、底部清除）、预选四角、计时、自动校验。
- [x] **阶段 4 首页与随机开局**：4 张难度卡片，有存档继续、无存档随机；「继续上局」兜底最近的难度槽位。
- [x] **阶段 5 关卡选择**：难度页签 + 自绘 3×4 键盘（含遥控器数字键、删除键）+ 范围校验。
- [x] **阶段 6 清除填写 + 下一题**：右侧按钮区，从棋盘最右列按右进入；清除不改题面与计时，下一题重置计时。
- [x] **阶段 7 排行榜与结算**：按难度保留最快 10 次；结算页含查看成绩/下一题/返回首页。
- [x] **手柄支持（补充）**：`input/GamepadInput` 把 A/B → 确定、X/Y → 菜单、十字键/左摇杆 → 方向键；
      含按键重复与双键码去重；新增 10 个单测。
- [~] **阶段 8 收尾与发布**：README/AGENTS/NOTICE 已补，`testDebugUnitTest + lintRelease + assembleRelease` 已通过；
      待电视真机验收（启动、DPAD、清除/下一题、关卡编号、崩溃日志）。

### 0.2 已验证结果

| 检查 | 结果 |
|---|---|
| 题库规模 | 每难度保留源题库前 20000 题（easy/medium/hard/diabolical 各 20000）；`diabolical#12070` 题面与评分 9.2 回读一致，`#20000` 为末条 |
| 单元测试 | 29 个用例全部通过（规则、记录解码、存档编解码、成绩/槽位、题库回读） |
| Release 构建 | `lintRelease` 与 `assembleRelease` 通过；APK 3.65 MB（`versionName 0.2.0` / `versionCode 2`） |
| 题库打包 | 4 个 `.bin` 共 3,360,000 字节，均以 stored（未压缩）方式打包，满足 `openFd` 随机读 |
| 界面冒烟 | 模拟器 1920×1080 下首页与做题页像素采样符合设计（卡片选中态、棋盘、完成度条） |

---

## 1. 可行性判断

### 1.1 可以直接复刻的部分

| 能力 | 源文件 | 处理方式 |
|---|---|---|
| 视图坐标等比缩放（1920×1080 基准） | `ViewportTransform.kt` | 原样复制（27 行） |
| 遥控器/返回键分发 | `MainActivity.kt` | 原样复制，仅改包名 |
| 九宫棋盘绘制 + 焦点 + 同宫高亮 | `SudokuGameView.drawGame` | 复制，去掉四宫/六宫分支 |
| 数字面板（默认 5、底部「清除」） | `SudokuGameView` 的 `picker*` | 复制，固定 3×3 |
| 预选数字（每格最多 4 个，四角绘制） | `SudokuGameView` 的 `candidateMasks` | 原样复制 |
| 计时（`elapsedRealtime` + 暂停/续时） | `SudokuGameView` | 原样复制 |
| 填满自动校验 + 错误标红 + 自动提交 | `SudokuGenerator.isValidSolution` / `conflictingCells` | 保留规则函数，去掉题面生成 |
| 成绩存储（每难度最快 10 次） | `data/ScoreRepository.kt` | 复制，key 只保留难度 |
| 未完成进度保存/恢复 | `data/ProgressRepository.kt` | 重写编码（存题号，不存答案） |

### 1.2 必须丢弃的部分

- `TwentyFour*` 全部（模型、渲染器、存档、测试）。
- `BoardSize` 四宫/六宫分支（`clueCounts`、`pickerLayout` 的 2 列/6 键分支、`blockRows/blockColumns` 变化分支）。
- `SudokuGenerator.generate` / `generateSolution`（不再生成题面）。
- 首页「选择宫格」与「24 点」入口。

### 1.3 题库可以用，但不能原样打包

已核对的源数据事实：

- 每个文件是定长 **101 字节/行**记录：`12 字节哈希 + 空格 + 81 位题面 + 空格 + 4 字节评分（右对齐，如 " 1.2"） + CRLF`。
- 行数：easy 100,000；medium 352,643；hard 321,592；diabolical 119,681（合计 893,939 题）。
- 原始文本合计约 **90 MB**，直接放进 `assets` 会让 APK 体积不可接受，且文本无法 O(1) 随机读取。
- 题库**不含答案**；但 README 说明每题由 QQWing 生成，保证唯一解，所以「用原始题面 + 行列宫规则校验完整盘面」就足够判对，不需要答案。

推荐做法：构建期把 4 个 txt 转成 4 个定长二进制文件（**42 字节/题**），保留原始行号，用
`AssetManager.openFd` 随机定位。全量打包约 **35.8 MiB**，最终实现时按难度裁剪为前 20000 题，
总计 3,360,000 字节（约 3.2 MiB）。

### 1.4 已确认的 4 个决定

1. **难度显示名**：`简单 / 中等 / 困难 / 专家`（对应 easy/medium/hard/diabolical）。
2. **清除填写不需要二次确认**：直接清空并显示「已清除所有填写」。
3. **存档按难度分槽**：4 个难度各保留一局未完成进度；难度卡片有存档时进入存档，否则随机开新题。
4. **不做显示答案能力**：保持「填满才校验」的原行为，不内置求解器。

另外：包名最终定为 **`com.zcc.sudokutv`**。

---

## 2. 目标产品形态

### 2.1 页面（5 个）

```
HOME ──────────────┬─> GAME（随机题）
  ├ 4 张难度卡片   └─> GAME（指定题号）
  ├ 关卡选择 ──> LEVEL_SELECT（难度页签 + 数字键盘）
  ├ 继续上局（仅有存档时可点）
  └ 排行榜 ──> SCORES
GAME ──填满并通过校验──> REWARD
REWARD ──> 下一题 / 排行榜 / 首页
```

- **HOME**：标题、4 张难度卡片（名称 + 评分区间 + 「进入随机一题」），底排按钮
  `[排行榜] [关卡选择] [继续上局]`。难度卡片按确定/点按 = 立即开一局该难度随机题。
- **LEVEL_SELECT**：顶部 4 个难度页签；中间显示已输入题号（大号）；下方自绘 3×4 数字键盘
  `1 2 3 / 4 5 6 / 7 8 9 / ← 0 确定`，另有「取消」「随机」按钮；提示 `可输入 1–{count}`。
- **GAME**：左侧 984×984 棋盘 + 右侧信息栏（难度与评分、计时、完成度、两个新按钮、操作提示、状态）。
- **REWARD**：完成、难度/题号/评分、用时、是否新纪录，按钮 `[查看成绩] [下一题] [返回首页]`。
- **SCORES**：4 个难度页签 + 该难度最快 10 次 + 返回。

### 2.2 遥控器映射（尽量与源项目一致，只新增 2 处）

| 场景 | 按键 | 行为 |
|---|---|---|
| 棋盘 | 方向键 | 移动 `selectedCell`（到边界停住） |
| 棋盘 | 确定 | 打开数字面板（题面已知格除外） |
| 棋盘 | 菜单 | 空格打开预选面板（已填格无反应） |
| 棋盘 | **右方向键（最右列）** | **进入右侧按钮区（新增）** |
| 面板 | 方向键 / 确定 / 菜单 / 返回 | 与源项目完全一致：填数、清除、切换预选、放弃 |
| 按钮区 | 上/下 / 确定 | 切换「清除填写」「下一题」并执行；左方向键回到棋盘最右列 |
| 任意 | 返回 | GAME → 首页（自动保存）；面板打开时先关闭面板（源项目行为） |

触摸：棋盘点按开数字面板、长按空格开预选、点按钮执行、点外部关闭面板，全部沿用源项目。

---

## 3. 题库数据方案（最关键的一步）

### 3.1 记录格式（42 字节定长）

| 偏移 | 内容 |
|---|---|
| 0–40 | 81 个格子，每格 4 bit。第 i 字节高 4 位 = 第 2i 格，低 4 位 = 第 2i+1 格；第 40 字节低 4 位补 0 |
| 41 | 评分 × 10 四舍五入（`8.3 → 83`） |

- 记录下标 `r` 与题号 `id` 的关系：`r = id - 1`，`id` 从 1 开始，对应源文件行号。
- 文件长度 = 题数 × 42，可反推题数，不必硬编码。
- 体积：本仓交付版本为**每难度前 20000 题**，每个文件 840,000 字节（约 820 KiB），四档合计约 3.2 MiB。

### 3.2 编号语义（务必在 UI 里写清楚）

题号是**单个难度文件内的行号**（1–20000）。`专家 · 第 12070 题` 指的是 `diabolical.txt` 第 12070 行，
题面 `000100000008090300170800000020000067061050930930000040000002056003040700000001000`，评分 9.2。
不同难度下同样的数字指向完全不同的题。

- 4 个难度在 UI 上显示的量级都是 `1–20000`（由文件长度反推，不硬编码）。
- 关卡选择只能校验「在范围内」，不能校验「这题好不好玩」。
- 只能取连续前缀，不能抽样：`openFd` 按 `id-1` 固定偏移定位。
- 裁剪后极少数稀有评分档缺失（专家区间前 20000 条覆盖 5.0–9.2，缺 6.1 / 8.1 / 9.3 三个稀有档），
  如需补齐可把高分记录追加到文件末尾。

### 3.3 一次性生成并固化（已完成）

题库转换曾用一个一次性 Gradle 任务完成，之后 4 个 `.bin` **随仓库提交**（合计约 3.2 MiB）。
因为题库是固定数据、后续不再新增或替换，项目**不保留生成流程**，构建也不依赖任何外部仓库。
裁剪同样是一次性本地操作：直接截断到前 20000 条（头部字节与源文件完全一致，可用 `git checkout` 回到全量）。

- 转换规则：源文件每行 101 字节 → 42 字节记录（见 §3.1），记录顺序 = 源文件行号顺序。
- `androidResources { noCompress.add("bin") }`，否则 `openFd` 拿不到未压缩资源，无法随机读。
- `verifyPuzzleBank` 在 `preBuild` 检查 4 个资产是否存在、长度是否为 42 的整数倍。

### 3.4 运行时读取

```kotlin
// data/PuzzleBank.kt
fun read(difficulty: Difficulty, id: Int): Puzzle? {
    val afd = assets.openFd("bank/${difficulty.fileName}.bin")
    afd.use {
        val offset = afd.startOffset + (id - 1) * RECORD_BYTES
        FileInputStream(afd.fileDescriptor).use { input ->
            input.channel.position(offset)
            val buffer = ByteBuffer.allocate(RECORD_BYTES)
            while (buffer.hasRemaining()) if (input.channel.read(buffer) < 0) return null
            return decode(buffer.array(), id, difficulty)
        }
    }
}
```

- 一次只读 42 字节，不要整体载入内存（低端电视内存有限）。
- 在 UI 线程同步读 42 字节足够快；若电视实测有卡顿，再包一层线程（本项目不引入协程依赖）。
- 兜底：若 `openFd` 抛 `FileNotFoundException`（资源被压缩），改为首次复制到 `filesDir/bank/` 再用
  `RandomAccessFile`。

### 3.5 解析与校验

```kotlin
data class Puzzle(
    val id: Int,
    val difficulty: Difficulty,
    val ratingTenths: Int,      // 83 表示 8.3
    val givens: IntArray,      // 81 格，0 = 空
) {
    fun isGiven(index: Int) = givens[index] != 0
    fun isValidCompletion(values: IntArray): Boolean =
        values.size == 81 &&
            values.indices.all { givens[it] == 0 || values[it] == givens[it] } &&
            SudokuRules.isValidSolution(values)
}
```

> 因为题库保证唯一解，`isValidCompletion` 判对即等于与标准答案一致；不存答案不会误判。

---

## 4. 目标工程结构

```
sudokuTv/
├─ settings.gradle.kts            rootProject.name = "SudokuTv"
├─ build.gradle.kts               AGP 8.7.3 + Kotlin 2.0.21
├─ gradle.properties
├─ gradlew / gradlew.bat          ┐ 从 sudoku-assistant-tv 复制
├─ gradle/wrapper/*               ┘
├─ .gitignore                     build/ 等
├─ PLAN.md / AGENTS.md / README.md / NOTICE.md
└─ app/
   ├─ build.gradle.kts            noCompress、verifyPuzzleBank、R8
   ├─ proguard-rules.pro
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml   leanback + landscape + banner
      │  ├─ assets/bank/*.bin     固定题库（每难度前 20000 题，随仓库提交，约 3.2 MiB）
      │  ├─ java/com/zcc/sudokutv/
      │  │  ├─ MainActivity.kt
      │  │  ├─ SudokuGameView.kt        页面状态机 + 绘制
      │  │  ├─ model/Puzzle.kt          Difficulty、Puzzle
      │  │  ├─ model/SudokuRules.kt     校验与冲突
      │  │  ├─ model/GameProgress.kt    存档编解码
      │  │  └─ data/{PuzzleCodec,PuzzleBank,ScoreRepository,ProgressRepository}.kt
      │  └─ res/{values,values-v31,drawable-nodpi}/
      └─ test/java/com/zcc/sudokutv/
```

---

## 5. 分阶段步骤（按顺序做，每阶段先跑通再进入下一阶段）

### 阶段 0：工程骨架（半天）

1. 在 `sudokuTv` 复制源项目的 Gradle 脚手架：`gradlew`、`gradlew.bat`、`gradle/`、`settings.gradle.kts`、
   根 `build.gradle.kts`、`gradle.properties`、`app/build.gradle.kts`、`proguard-rules.pro`。
2. 全局替换包名 `com.kanayama.sudokuassistant` → `com.zcc.sudokutv`，应用名 `数独`，
   `versionCode 1` / `versionName 0.1.0`，其余保持 `compileSdk 35 / minSdk 24 / targetSdk 35 / JDK 17`。
3. 复制资源：`values/{colors,themes}.xml`、`values-v31/themes.xml`、`drawable-nodpi/tv_brand_banner.png`、
   `drawable-nodpi/tv_brand_icon.png`；`AndroidManifest.xml` 保留 leanback 与 landscape。
4. 写一个只显示「数独」文字的占位 `SudokuGameView` + `MainActivity`，确认能编译、能装到电视。

**验收**：`./gradlew :app:assembleDebug` 通过；电视上启动不白屏、方向键有响应。

---

### 阶段 1：题库预处理（约半天）

5. 一次性完成题库转换，按 §3.1 写出 4 个 `.bin` 并随仓库提交（转换任务已完成并移除）。
6. 打印统计：各文件题数、字节数，并与源文件行数比对。
7. 抽样自检：随机取 20 条，`id` 反查源文件对应行，题面逐字符相等。
8. 配 `noCompress.add("bin")`。
9. 体积收尾：4 个文件统一截断到前 20000 条（840,000 字节），仅删除尾部字节。

**验收**：4 个文件题数均为 `20000`，字节数均为 840,000；`diabolical` 第 12070 条解码后等于 §3.2
里的题面，评分 92；第 20000 条也按 `BankAssetTest` 固定校验。

---

### 阶段 2：模型与数据层（1 天）

9. `model/Puzzle.kt`：
   - `enum class Difficulty(val label, val fileName, val ratingHint)`，4 个值。
   - `Puzzle(id, difficulty, ratingTenths, givens)`（无 `solution`）。
10. `model/SudokuRules.kt`：从 `SudokuGenerator` 摘出 `isValidSolution(values)`、
    `conflictingCells(values)`，固定 9×9、3×3 宫。**删除** `generate`、`generateSolution`、`clueCount`。
11. `data/PuzzleBank.kt`：`count(difficulty)`（由文件长度/42 推导，缓存）、`read(difficulty, id)`、
    `randomId(difficulty, exclude: Int?)`。
12. `data/ScoreRepository.kt`：key 改为 `difficulty.name`，保留 10 次。
13. `data/ProgressRepository.kt`：单槽 `current`，编码字段
    `version|difficulty|id|entries|candidates|selectedCell|elapsedMillis`；
    解码时重新 `PuzzleBank.read` 取题面校验，失效则丢弃。单局通关后清除。
14. 单元测试（见 §7）。

**验收**：JVM 测试全绿；`PuzzleBank` 能在 Robolectric 里读到资产（或先在真机日志验证）。

---

### 阶段 3：做题界面复刻（1～2 天）

15. 复制 `SudokuGameView` 与 `ViewportTransform`，按步骤删除：24 点全部字段/函数、四宫六宫分支、
    `Page.TWENTY_FOUR`、宫格选择相关代码。
16. 固定九宫：`side = 9`、`blockRows = blockColumns = 3`、`defaultPickerValue = 5`、
    数字面板 3 列、`pickerLayout` 只保留 9×9 版本（620 宽、键 128）。
17. `enterValue` 改为使用 `puzzle.isValidCompletion`，成功时
    `scores.record(difficulty, seconds)` → `Page.REWARD`；失败时标红并提示。
18. 保留：同宫高亮、错误红字、预选四角、完成度、`elapsedRealtime` 计时、暂停/续时、返回首页保存。
19. 游戏页右栏先显示：`难度 · 评分`、用时、完成度、操作提示、状态。

**验收**：与源项目九宫逐项对照清单通过（见 §7 的回归清单）；遥控器填数、菜单预选、底部清除全部正常。

---

### 阶段 4：首页与随机开局（半天）

20. 重排 HOME：标题 + 4 张难度卡片（名称、`评分 < 1.5` 等提示、
    副标题按存档状态显示「继续上局」或「进入随机一题」）+ 底排
    `[排行榜] [关卡选择] [继续上局]`，保留源项目的焦点结构（行内左右、行间上下）。
21. 难度卡片确定/点按：该难度有存档 → `startGame(savedPuzzle)` 恢复；无存档 →
    `startRandomGame(difficulty)`（`id = bank.randomId(difficulty, exclude = null)`），重置计时与填写，写该难度槽位。
22. 「继续上局」：读取最近一局所属难度的槽位，恢复题面、填写、预选、焦点、累计时间；
    四个槽位全空时按钮置灰且按键无反应。

**验收**：同一难度连续进入 5 次，题目不同；返回首页再继续，盘面与用时一致。

---

### 阶段 5：关卡选择界面（1 天）

23. 新增 `Page.LEVEL_SELECT` 与 `levelDifficulty`、`levelInput: StringBuilder`、`levelFocus`。
24. 绘制：顶部 4 个难度页签；中间题号显示（空时 `—`）；`1–{count}` 提示；
    3×4 键盘；`取消`、`随机` 按钮。
25. 输入规则：只接受数字，最多 `count.toString().length` 位，禁止前导 0；`←` 删除一位；
    `确定` 时校验 `1..count`，越界显示「请输入 1–{count}」。
26. 同时支持遥控器数字键/小键盘的 `KEYCODE_0..9`、`KEYCODE_NUMPAD_0..9`、`KEYCODE_DEL`。
27. 确定后 `PuzzleBank.read` → `startGame(puzzle)`；题面为空或读失败提示「题库读取失败」。

**验收**：输入 `119681` + 恶魔 → 进入 §3.2 那道题并显示「恶魔 · 8.3」；
输入 `0`、`999999999` 都被拦住；返回键回首页。

---

### 阶段 6：一键清除填写 + 下一题（半天）

28. 右栏新增两个按钮（建议 `清除填写` 1165–1490、`下一题` 1500–1824，y 约 400–500，
    具体位置按实际排版微调，不得压到棋盘）。绘制与触摸矩形共用同一函数。
29. 焦点拓扑：棋盘最右列再按右 → 进入按钮区；按钮区左 → 回到原行最右列；按钮区上/下切换；
    确定执行；触摸直接执行。
30. `clearAllEntries()`：`entries = givens.copyOf()`、`candidateMasks = 0`、`incorrectCells = {}`、
    关闭面板、`statusMessage = "已清除所有填写"`；**不动计时、不动焦点、不改题面**。
31. `nextPuzzle()`：`bank.randomId(difficulty, exclude = current.id)`，重置
    `elapsedMillis = 0`、`startedAt = now`、`elapsedSeconds = 0`、`newRecord = false`，进入 GAME 并只覆盖该难度槽位。
    未完成也允许切换。
32. 提示文案里补上新按钮说明；`ROUND` 页面「下一题」按钮复用 `nextPuzzle()`。

**验收**：清除后题面格仍在、计时继续；下一题题号变化、用时归零、旧局不再出现在「继续上局」。

---

### 阶段 7：排行榜与结算（半天）

33. `SCORES` 只保留 4 个难度页签（删除宫格页签与 `BoardSize` 相关焦点计算）。
34. `REWARD` 显示 `难度 · 第 N 题`、评分、用时、新纪录标记；三个按钮
    `[查看成绩] [下一题] [返回首页]`，默认焦点在「下一题」。

**验收**：同难度通关 11 次只保留最快 10 次；不同难度互不影响。

---

### 阶段 8：收尾与发布（半天）

35. 写 `README.md`（功能、题号语义、构建、安装）与 `AGENTS.md`（目录职责、题库格式、遥控器映射）。
36. 增加 `NOTICE`：题库来自 `sudoku-exchange-puzzle-bank`，公有领域（见其 `LICENSE.txt`）。
37. 全量验证：

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
adb connect <电视IP>:5555
adb -s <电视IP>:5555 install -r app/build/outputs/apk/release/app-release.apk
adb -s <电视IP>:5555 shell cmd package compile -m speed -f com.kanayama.sudokutv
```

**验收**：启动首帧即响应方向键；DPAD 全流程走通；截图归档；`adb logcat` 无崩溃。
（禁止卸载或清数据安装。）

---

## 6. 关键代码设计草案

### 6.1 页面与状态

```kotlin
private enum class Page { HOME, LEVEL_SELECT, GAME, REWARD, SCORES }
private enum class PickerMode { VALUE, CANDIDATES }

private var page = Page.HOME
private var difficulty = Difficulty.EASY
private var puzzle: Puzzle? = null
private var entries = IntArray(0)
private var candidateMasks = IntArray(0)
private var selectedCell = 0
private var sideFocus = SideFocus.NONE          // NONE / CLEAR_ALL / NEXT，新增
```

### 6.2 两个新操作的语义边界

| 操作 | 改题面 | 改填写 | 改预选 | 改计时 | 改焦点 | 写存档 |
|---|---|---|---|---|---|---|
| 清除填写 | 否 | 全部清空 | 全部清空 | 继续 | 保留当前格 | 是 |
| 下一题 | 换题 | 重置为题面 | 清空 | 归零重计 | 第一空格 | 是（覆盖） |
| 数字面板「清除」 | 否 | 仅当前格 | 仅当前格 | 继续 | 保留 | 是 |

### 6.3 题号解析

```kotlin
private fun confirmLevelInput() {
    val count = bank.count(levelDifficulty)
    val id = levelInput.toString().toIntOrNull()
    if (id == null || id !in 1..count) {
        levelMessage = "请输入 1–$count"
        return
    }
    val puzzle = bank.read(levelDifficulty, id) ?: run { levelMessage = "题库读取失败"; return }
    startGame(puzzle)
}
```

---

## 7. 测试与回归清单

### 7.1 JVM 单元测试

| 测试 | 内容 |
|---|---|
| `BankAssetTest` | 4 个难度均为 20000 题、840,000 字节；`diabolical#12070`（9.2）与 `#20000` 题面/评分固定；抽样 200 条题面合法 |
| `PuzzleCodecTest` | 解码 42 字节：81 格取值 0..9、评分还原（83→8.3） |
| `SudokuRulesTest` | 合法完整盘判对；题面被改动判错；行列宫重复能定位冲突格 |
| `GameProgressTest` | 编码/解码往返；难度或 id 失效时返回 null；`entries` 中题面格被篡改时拒绝 |
| `ScoreRepositoryTest` | 只留 10 条、升序、跨难度隔离 |

（Robolectric 用于需要 `Context`/`AssetManager` 的用例，沿用源项目依赖版本。）

### 7.2 电视/遥控器回归清单（每阶段都跑）

- [ ] 启动首帧后第一次按方向键即响应，无延迟。
- [ ] 棋盘方向键四向移动；最右列按右进入侧栏，侧栏按左回到原行。
- [ ] 确定打开数字面板，默认聚焦 5；向下可选「清除」；确定清空当前格。
- [ ] 菜单键在空格打开预选，最多 4 个，选中角标与保存后显示一致；在已填格按菜单无反应。
- [ ] 面板中返回键关闭面板；棋盘上返回键回首页并保存。
- [ ] 填满最后一格自动校验；错误时红字提示且可继续修改。
- [ ] 清除填写：只清玩家填写与预选，题面不变，计时不中断。
- [ ] 下一题：题号变化、计时归零、进度覆盖。
- [ ] 关卡选择输入合法/非法题号的表现。
- [ ] 触摸：点格、长按预选、点两个新按钮、面板外点按关闭。

---

## 8. 已知限制 / 做不到的地方（重要）

1. **题库没有答案数据**：所以不能做「提示下一步」「显示正确解」；也不做逐格即时判错。
   若以后需要，可加一个回溯求解器（题库唯一解，可秒解），但那会改变「填满才校验」的既有约定。
2. **原始题库共 90 MB 文本 / 35.8 MiB 定长记录，不能原样进 APK**：必须走 §3 的预处理。
   本仓已裁剪为每难度前 20000 题（合计 3.2 MiB，Release APK 3.65 MB）。题号仍是源文件行号，
   但上限变成 20000，因此 `diabolical#119681` 这类超大题号已不可用；裁剪只能取连续前缀。
3. **题号只在单一难度文件内有效**：不同难度同名题号指向不同题目；UI 必须明确展示难度页签。
4. **关卡选择无法校验题目内容**：只能校验范围，无法知道某题对用户是否「合适」。
5. **电视遥控器不一定有数字键**：因此关卡选择必须自绘键盘；也不能依赖系统输入法（会破坏原生焦点链路）。
6. **首页必须重排**：去掉四宫/六宫/24 点后，源项目首页布局（三张宫格卡 + 24 点按钮）不再适用，
   视觉上不可能与源项目 1:1 一致，但交互与视觉语言可以保持一致。
7. **存档与题库版本绑定**：只存 `id` 的话，题库重新生成/替换后同一题号可能是另一道题。
   计划里用「按 id 重新读题面」+ 可选「题面哈希」校验；不匹配就丢弃存档，不会出现错题续玩。
8. **「下一题」无法保证长期不重复**：只保证不重复当前这一题。
9. **原项目的四宫/六宫/24 点相关代码不能复用**：尺寸假设、渲染器、存档格式都不兼容，必须删除。
10. **排行榜维度变化**：原来按「宫格 × 难度」，现在只有难度；旧成绩不迁移（新应用无旧数据）。

---

## 9. 工作量预估

| 阶段 | 内容 | 预估 |
|---|---|---|
| 0 | 工程骨架 | 0.5 天 |
| 1 | 题库预处理 | 0.5 天 |
| 2 | 模型与数据层 | 1 天 |
| 3 | 做题界面复刻 | 1.5 天 |
| 4 | 首页与随机开局 | 0.5 天 |
| 5 | 关卡选择 | 1 天 |
| 6 | 清除填写 + 下一题 | 0.5 天 |
| 7 | 排行榜与结算 | 0.5 天 |
| 8 | 收尾与发布 | 0.5 天 |
| | **合计** | **约 6.5 天** |

---

## 10. 建议的实施顺序（如果只想先看效果）

先用「阶段 0 + 1 + 2 + 3 + 4」做出一个能玩随机题的版本装机验证（约 3 天），
确认遥控器手感和题库读取性能没问题，再做关卡选择与两个新按钮。
这样即使题库体积或性能不达标，也能最早暴露问题。
