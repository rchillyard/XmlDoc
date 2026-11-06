# MR14 验收证据

日期：2026-11-06。分支：`V1_1_3-Kaining-2`，没有为 MR13/MR14 创建新分支。
先提交计划，再执行 MR13，最后执行 MR14。**自动化通过，发布验收未完成。**
用户确认暂无获授权的真实三方样本；4 组真实案例均缺输入和 InDesign 实机记录。

## 来源与结果

本地干净构建对应实现提交 `f2bca0a`；完整 SHA、命令、退出码、日志 SHA、语料文件
SHA/mtime 在 [机器可读记录](evidence/mr14-validation.json)。后续报告与证据提交不改变受测实现。

| 检查 | 命令/证据 | 实际结果 |
|---|---|---|
| 干净 Scala 构建 | `sbt 'core/clean' 'idml/clean' 'kml/clean' 'core/test' 'idml/test' 'kml/test'`；[日志 gzip](evidence/mr14-clean.log.gz) | 退出 0；core 432、idml 404、kml 110，共 946 通过；1 个既有 ignored：ExtractorsSpec.extractor3B；无 pending |
| MR14 公共入口专项 | `sbt 'idml/testOnly *IdmlMergeAcceptanceSpec'`；[日志 gzip](evidence/mr14-acceptance.log.gz) | 6 项通过；这是上面 404 项的子集，不重复计数 |
| Python 损坏输入/门禁回归 | `python3 -m unittest discover -s idml/tools -p 'test_*.py'`；[日志 gzip](evidence/mr14-python.log.gz) | 22 项通过，退出 0 |
| 语料只读验证 | `python3 idml/tools/generate_three_way_corpus.py --verify` | 16 组、schema 3、strict-text-node-v1；所有文件字节与 mtime 前后相同 |
| MR13 正式性能 | [性能报告](xml-merge-performance.md)、[原始测量](evidence/mr13-formal.json)、[汇总](evidence/mr13-formal-summary.json) | 25 场景 × 5 次，125 次正式测量；冻结门禁通过 |
| 真实样本严格门禁 | `python3 idml/tools/verify_real_world_manifest.py --report idml/docs/evidence/mr14-real-world-readiness.json` | **退出 1**；契约合法，但 supplied=0/4、releaseReady=false；[具体阻塞](evidence/mr14-real-world-readiness.json) |

日志仅将本机 workspace/home 绝对路径替换为占位符，gzip 固定时间戳；不修改测试结果。
MR13 只测已定义规模下的通用核心及最小包分段性能，不代表真实 IDML 完整入口或排版性能。

## 九部分计划追溯

| 计划部分 | 对应 MR / 实际证据 | 状态与边界 |
|---|---|---|
| 一：预期依据 | MR01/03/14；[冻结规格](xml-merge-test-spec.md)、[理论报告](xml-merge-theory-idml-report.md) | 区分论文规则、项目细化、领域约束、工程契约 |
| 二：测试样本 | MR03/14；CorpusContractSpec、独立 expected、真实样本 manifest | 16 组合成语料完成；4 组真实样本尚未提供 |
| 三：五层测试 | MR04—09/11—12；匹配、PCS、协调、重建、包入口 Specs | 自动化通过；具体缺陷与文件见理论报告 |
| 四：场景矩阵 | MR05—08；PcsEditDetector、PcsMerger、PcsTreeBuilder、TreeMatcher Specs | 操作组合、guards、深层 Delete/Edit、无 Self 与混排自动化覆盖 |
| 五：断言 | MR02/06—09；IdmlMergeAssertionsSpec、ThreeWayCorpusSpec | 完整 XML/冲突对象；不以仅检查某个属性代替完整 oracle |
| 六：性质 | MR09—10；ThreeWayMergerPropertiesSpec、PCS 性质测试 | 两种顺序、恒等、编辑保留与来源约束；仅对声明模型/生成范围成立 |
| 七：已有用例 | MR03/09/11—12；ThreeWayCorpusSpec、ThreeWayPackageCorpusSpec | 16 组按适用入口执行正常/左右交换顺序；10 成功、10b 冲突分别验证 |
| 八：端到端 | MR11—12/14；IdmlReferenceValidatorSpec、IdmlMergeDriverAtomicSpec、IdmlMergeAcceptanceSpec | 公共 driver、完整包、引用、二进制资源、失败不落盘已验证；InDesign 打开/保存/重开未验证 |
| 九：规模与顺序 | MR13—14；冻结 calibration/gates/formal 产物、CI 工作流 | 本地性能与回归完成；远程 CI 必须单独观察，不能由本地通过推断 |

五层分别为匹配（MR04）、PCS/检测（MR05）、协调（MR06—07）、重建（MR08）、
整包（MR11—12）；MR09/14 贯通入口。MR14 公共入口额外覆盖完整成功包、通用冲突、
领域冲突、最终替换 I/O 失败与损坏输入；冲突/失败均验证输入哈希和临时文件清理。
真实样本缺失只记录阻塞，不计作成功或忽略的 InDesign 验收测试。

## 真实验收如何补齐

按 [真实样本制作指南](idml-real-sample-guide.md) 制作独立 base/left/right、来源授权、
操作记录、独立 expected 或完整冲突预期，以及 InDesign 版本和打开/保存/关闭/重开证据。
[manifest](../src/test/resources/com/phasmidsoftware/xmldoc/idml/real-world-corpus/manifest.json)
预留四组固定案例。补齐后执行严格门禁；`--allow-pending` 仅校验计划，不能解锁发布。
成功案例的通用 XML 与包验证也不能替代人工确认排版结果。

## 远程 CI

工作流 [.github/workflows/idml-merge-tests.yml](../../.github/workflows/idml-merge-tests.yml)
将 `automated-regressions` 和 `release-evidence` 分为独立 job。
前者验证自动化回归；后者在真实记录缺失时必须失败并上传 readiness JSON。
因此在当前 0/4 状态下，整个工作流不能宣称绿色或发布完成。远程运行结果已核查：

- [GitHub run 42245687017](https://github.com/rchillyard/XmlDoc/actions/runs/42245687017)，
  受测提交 `84343cb8833a58dbb76ac783c9ed15d43d6b25f9`。
- `automated-regressions`：success（Scala、只读 corpus 与 Python 步骤均成功）。
- `release-evidence`：failure，严格门禁退出 1；contractValid=true、suppliedCases=0、
  plannedCases=4、releaseReady=false；readiness artifact 上传成功。
- 整体 conclusion=failure，仅因为上述真实证据阻塞；不是全部 CI 通过。
  [机器可读 job/step 结果](evidence/mr14-github-ci.json)。
- 本段及执行状态的后续提交仅补文档与证据，不改变受测源码。
