# 第三方数据声明

## Sudoku Exchange Puzzle Bank

- 来源：<https://github.com/grantm/sudoku-exchange-puzzle-bank>
- 用途：本应用内置的全部数独题目
- 许可：公有领域（Public Domain），原文见该仓库的 `LICENSE.txt`
- 说明：题目由 QQWing 生成，并用 Sukaku Explainer 评分后分为 `easy`、`medium`、
  `hard`、`diabolical` 四档；每题保证唯一解。

应用不会联网获取题目。题库文本已一次性转换为定长二进制并随 APK 打包，每个难度保留源题库前
20000 题（共 80000 题）。
