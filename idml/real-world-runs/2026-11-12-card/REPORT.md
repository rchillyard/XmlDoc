# InDesign 真实导出测试记录

2026-11-12，Adobe InDesign 2026 21.5.1.73。16 组覆盖检查和合并结果检查全部通过。

## 文档与基线

使用 Adobe 官方双面名片示例，每页 88.9 × 50.8 mm。原始下载包及来源记录分别见 `source.zip` 与 `provenance.json`。源文件保留在 `source/`，测试基线为 `baseline.indd`。

基线经过明确准备，文本统一为本机 Arial，图片重连随包 AI 标志，背面添加色块、分组和固定短文本框以覆盖所需操作。基线界面显示 No errors，见 `baseline-ui.png` 与 `baseline-ui-state.txt`。这次验证的是经过准备的两页名片样本。

## 验证结果

| 场景 | 预期与实际 | 验收 |
|---|---|---|
| 01-single-add | 合并成功 | 两页像素一致，保存后重开成功 |
| 02-single-delete | 合并成功 | 两页像素一致，保存后重开成功 |
| 03-single-move | 合并成功 | 两页像素一致，保存后重开成功 |
| 04-both-add-diff-pos | 合并成功 | 两页像素一致，保存后重开成功 |
| 05-move-move-divergent | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 06-delete-vs-modify | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 06b-delete-thread-vs-content | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 07-same-story-diff-para | 合并成功 | 两页像素一致，保存后重开成功 |
| 08-same-para-overlap | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 08b-same-para-disjoint | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 09-zorder | 合并成功 | 两页像素一致，保存后重开成功 |
| 10-group-ungroup | 合并成功 | 两页像素一致，保存后重开成功 |
| 10b-divergent-parent | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 11-thread-insert | 合并成功 | 两页像素一致，保存后重开成功 |
| 12-next-divergent | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |
| 13-parentstory-vs-content | 报告冲突 | 冲突目标已核对，左侧输入逐字节保留 |

8 组成功结果共 16 页与独立参考渲染逐像素一致，输出无文本溢出。独立参考通过直接在 InDesign 中依次执行两侧操作产生。8 组冲突日志已核对到对应对象、文本或引用关系。详见 `final-results.json`。

## 文件说明

每组目录含 `base.idml`、`left.idml`、`right.idml`、两侧对象清单与 `merge.log`。成功组还含 `merged.idml`、`expected.idml`、重新保存的 `*-review.indd`、`roundtrip.idml`、`application.log` 和 `ui.png`。冲突组的 `merged.idml` 保留左侧内容。

InDesign 自动命名的 `merged-page2.png` 为第 1 页，`merged-page22.png` 为第 2 页，`expected-` 同理。`baseline-page2.png` 和 `baseline-page22.png` 分别为两页基线。

`batch.jsx` 为执行过的生成脚本，`review.jsx` 为独立参考制作及打开、保存、重开、渲染脚本。脚本运行目录固定为 `/tmp/codex-idml-card`，本目录是完整持久副本。`Links/` 包含随包图形资源。所有原始导出仍保留实际导出时的链接路径。

重新验证已有产物可执行下面的命令。

```bash
cd /Users/lady_penguin/Documents/3-way-merge
uv run --with pillow python idml/real-world-runs/2026-11-12-card/final_verify.py
```

## 范围

覆盖 01–13 及 06b、08b、10b 的 16 个核心场景。仓库另有四项发布验收 manifest 和扩展 atlas，本次结果没有修改它们的状态。图像相等在当前版本、字体、180 dpi 渲染条件下成立。原始 Adobe 示例的使用说明随下载包保留。

Magazine 的早期尝试及结果保存在相邻的 `2026-11-12-magazine` 目录。

提交整理时将日志和对象清单的换行统一为 LF，并去掉行尾空白，便于 Git 审阅。
