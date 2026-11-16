# AGENTS.md — 数独（题库版）

> 面向 Android TV / 横屏设备的离线九宫数独。题目来自公有领域的 Sudoku Exchange Puzzle Bank。

## 1. 系统概述

- 技术栈：Kotlin、Android 原生 `View` + `Canvas`、`SharedPreferences`、Gradle（AGP 8.7.3 / Kotlin 2.0.21）。
- 刻意不使用 Compose：目标电视上原生 View 的首键响应更稳定。
- 只有一个 Activity（`MainActivity`），所有页面由 `SudokuGameView` 自绘并共用一套 1920×1080 设计坐标。
- 不联网、不登录、无广告、无音频；成绩与进度只存本地。

## 2. 目录导航

| 路径 | 职责 |
|---|---|
| `app/src/main/java/com/zcc/sudokutv/MainActivity.kt` | 承载视图、转发按键、暂停/恢复 |
| `app/src/main/java/com/zcc/sudokutv/SudokuGameView.kt` | 页面状态机（首页/关卡选择/做题/结算/排行榜）、绘制、触摸与遥控器 |
| `app/src/main/java/com/zcc/sudokutv/input/GamepadInput.kt` | 手柄按键/摇杆 → 现有键码（AB 确定、XY 菜单、方向键） |
| `app/src/main/java/com/zcc/sudokutv/model/Puzzle.kt` | `Difficulty`、`Puzzle`、棋盘常量（9×9，3×3 宫） |
| `app/src/main/java/com/zcc/sudokutv/model/SudokuRules.kt` | 完整盘面校验与冲突格定位 |
| `app/src/main/java/com/zcc/sudokutv/model/GameProgress.kt` | 单局存档编解码（v1） |
| `app/src/main/java/com/zcc/sudokutv/data/PuzzleCodec.kt` | 42 字节题库记录解码 |
| `app/src/main/java/com/zcc/sudokutv/data/PuzzleBank.kt` | `AssetManager.openFd` 随机读取题库 |
| `app/src/main/java/com/zcc/sudokutv/data/ScoreRepository.kt` | 每个难度最快 10 次成绩 |
| `app/src/main/java/com/zcc/sudokutv/data/ProgressRepository.kt` | 每个难度一局未完成存档 + `latest` 指针 |
| `app/src/main/assets/bank/*.bin` | 固定题库二进制，**随仓库提交**（每难度前 20000 题，各 840,000 字节，共约 3.2 MiB），不再提供生成流程 |
| `app/src/test/java/com/zcc/sudokutv/` | JVM 测试（规则、编解码、成绩/存档、题库回读） |
| `app/build.gradle.kts` | `verifyPuzzleBank`（preBuild 检查资产）、`noCompress`、R8 |
| `PLAN.md` | 阶段计划与实施进度 |

## 3. 核心业务场景

- **打开应用**：`MainActivity.onCreate` → `SudokuGameView.requestFocus` → `Page.HOME`。
- **首页选择难度**：`activateHome` → 该难度有存档则 `startSaved`，否则 `startRandom`。
  - `startRandom` → `PuzzleBank.randomId` → `PuzzleBank.read` → `beginGame`。
  - `startSaved` → `ProgressRepository.load` → 用存档中的题号重新读题面；题面/填写不一致则丢弃并随机开题。
- **关卡选择**：`Page.LEVEL_SELECT` → `levelInput` + 自绘 3×4 键盘 → `confirmLevelInput`
  → 范围 `1..count` 校验 → `beginGame`。
- **填数**：`handleGameKey`/`handleGameTap` → `openValuePicker` → `enterValue`。
  - 填满 81 格后 `Puzzle.isValidCompletion` 判定：通过则 `finishGame`，否则 `SudokuRules.conflictingCells` 标红。
- **预选数字**：遥控器菜单键（空格）或长按空格 → `openCandidatePicker` → 每格最多 4 个。
- **清除填写**：右侧按钮 → `clearAllEntries`（恢复题面、清空预选与错误，保留计时与焦点）。
- **下一题**：右侧按钮或结算页 → `nextPuzzle`（同难度、排除当前题、计时归零、覆盖该难度槽位）。
- **结算**：`finishGame` → 清除该难度存档 → `ScoreRepository.record` → `Page.REWARD`。
- **排行榜**：`Page.SCORES` → 四个难度页签 → 最快 10 次。
- **退出应用**：首页返回键 → 退出确认框 → `MainActivity.finishAffinity`。

## 4. 固定约束

- 棋盘固定 9×9、3×3 宫；不要重新引入四宫/六宫或 24 点。
- 布局以 1920×1080 设计坐标等比居中缩放（`onDraw` 与 `toDesignPoint` 必须使用同一公式）。
- 数字面板：3×3、默认聚焦 5、底部「清除」；预选面板复用同一网格。
- 手柄映射：`A/B → KEYCODE_ENTER`，`X/Y → KEYCODE_MENU`，十字键/左摇杆 → `KEYCODE_DPAD_*`；
  只在按下边沿触发，忽略 `repeatCount > 0`，同一动作 80 ms 内去重。
- 判断手柄来源必须用 `(source and SOURCE_GAMEPAD) == SOURCE_GAMEPAD`，不能用 `!= 0`
  （`SOURCE_DPAD` 与 `SOURCE_GAMEPAD` 共享类别位，会误判）。
- 遥控器拓扑：棋盘最右列按右进入右侧按钮区，按钮区按左回棋盘；其余按键保持与
  `sudoku-assistant-tv` 的九宫一致。
- 校验时机：只在最后一个空格填满后校验，不做逐格即时判错；题库保证唯一解，因此不需要保存答案。
- 计时：`SystemClock.elapsedRealtime()`，离开游戏时累计毫秒并暂停，恢复后接续。
- 存档：**每个难度一个槽位**，只存题号、填写、预选、焦点、累计毫秒；恢复时重新读题面并校验题面格一致。
- 题库格式：每记录 42 字节 = 81 格 × 4bit + 1 字节评分（评分 × 10）。记录下标 = 题号 − 1；
  题号是单个难度文件内的行号。
- 题库资产必须 `noCompress`，否则 `AssetManager.openFd` 无法随机定位；读取时一次只读 42 字节。
- 题库是**固定数据**，不新增、不替换；不要重新引入题库生成流程或外部题库依赖。
- 每个难度只保留源题库前 20000 题，题号范围固定 `1..20000`；只能按连续前缀裁剪（`openFd` 按 `id-1` 偏移定位，
  不能抽样）。若要补回高分题，只能追加到文件末尾。
- 题库资产必须随仓库提交，缺失时 `verifyPuzzleBank` 会在构建前报错。
- 发布使用 Release APK + 覆盖安装；不要卸载或清数据。
- 运行时不得新增网络、账号、广告、音频依赖。

## 5. 构建与验证

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

真机/模拟器建议检查：启动首键响应、棋盘四向移动、面板填数与清除、预选四角、
右侧两个按钮、关卡选择输入合法/非法题号、填满自动校验、返回首页后再继续。

## 6. 维护规则

- 修改模型、数据格式、遥控器映射或页面流程后，同步更新本文件与 `PLAN.md` 的进度。
- 题库记录格式若变化，必须同时更新 `PuzzleCodec`、`BankAssetTest`、`README.md` 与 `.bin` 资产。
- 不要提交 `local.properties`、`captures/` 下的截图。
