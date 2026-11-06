# IDML XML Merge Tests — Merge Request Breakdown

结论：建议拆为 7 个 Batch、14 个 Merge Request。每个 MR 只处理一个可独立理解、审阅和验证的职责。

本文件是实施计划，不代表测试已执行或能力已实现。文件路径以仓库根目录为基准；新增文件均标注“新增”。现有代码已核对，新增名称为本计划指定的目标文件。分支命名遵循用户提供的 mr-breakdown skill 模板。每个 MR 随变更交付测试；发现违反已冻结契约的行为，在负责该职责的 MR 中交付最小修复，禁止通过削弱 expected、忽略失败或把当前错误行为固化为正确预期来获得绿灯。

## Batch 1: Freeze merge policies and acceptance contracts

MR count: 1

### MR 01 — Record merge testing decisions
branch: feature/IDML-XML-Merge-Tests-MR01

Files:
- idml/docs/xml-merge-test-spec.md（新增）
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md

What to change:
- 把测试计划九个部分映射到下列 MR，冻结预期来源、适用入口、成功与失败契约。论文页码使用 PDF 页内页码。

Recommended dependencies

```text
MR01
├── MR02 ── MR03 ────────────────────────────────┐
├── MR04 ── MR05 ── MR06 ── MR07 ───────────────┤
│                 └──────────── MR08 ───────────┤
│   MR03 + MR07 + MR08 ── MR09 ── MR10 ─────────┤
├── MR11（同时依赖 MR02）───────────────────────┤
│   MR03 + MR11 ── MR12 ────────────────────────┤
└── MR13（同时依赖 MR04、MR05、MR07、MR08）──────┤
                                      全部 ── MR14
```

- MR02 与 MR04 可以并行；MR03 与 MR05 可以并行；MR06、MR08、MR11 在各自前置完成后可以并行。MR09 必须等待 MR03、MR07、MR08；MR10 必须等待 MR09；MR12 必须等待 MR03、MR11；MR13 在前置完成后可与 MR09—MR12 并行。Batch 是审阅分组，实际可启动条件以 MR 依赖为准。共享文件上的改动在后续 MR 中基于已合并版本整合。

Production launch:

```text
MR01—MR14 全部完成
+ core/test、idml/test 和 corpus 只读校验通过
+ 适用的 kml/test 回归通过
+ 所有要求支持的用例有自动化证据
+ 真实 InDesign 样本打开、保存、重开证据齐全
+ 性能记录满足 MR01 冻结的门禁
```

- 此处“上线”指声明本轮 IDML 合并能力可交付；不创建部署流程。无数据库 migration 或历史回填任务。

Locked scope
- 覆盖匹配、PCS、编辑检测与协调、重建、IDML 包五层，以及对称性、恒等关系、编辑保留、无凭空关系、真实文档与规模验证。
- 复用 `ThreeWayMerger.merge`、`IdmlPackageMerger.merge`、`IdmlMergeDriver.merge` 现有外部签名；新增能力只服务本测试计划。
- 默认以严格 Delete/Edit 验收；删除祖先与任意深度后代编辑不得静默丢失。
- 不实现推测性结构合并；同一间隙不同插入在严格模式下按关系矛盾判定。
- XML 比较保留子节点顺序与正文空白；属性作为集合比较。命名空间按解析模型实际保留的信息比较，不伪称具备模型未保留的能力。
- 有 Self 的节点保持身份，无 Self 节点单独验证匹配。仅用路径相等不能证明节点相同。
- `designmap.xml` 仅更新 part 引用；其他内容沿用 driver 当前保留策略，不扩展为完整 designmap 三方合并。
- 所有测试在临时副本上写入；base、theirs 和受版本控制的样本不得被 driver 修改。

主要参考：
- 输入计划：`/Users/lady_penguin/Downloads/三方 XML 合并测试计划图解.html`，第 1—9 部分与图 1—13。
- 格式规范：`/Users/lady_penguin/Downloads/mr-breakdown.skill` 内 `mr-breakdown/SKILL.md`、`assets/template.md`、`references/checklist.md`。
- 论文：Tancred Lindholm，A Three-way Merge for XML Documents，§2—§7、§9；`/Users/lady_penguin/Downloads/3 way merge paper/A Three-way Merge for XML Documents.pdf`。
- 现有实现：`core/src/main/scala/com/phasmidsoftware/xmldoc/merge/{TreeMatcher,ContentMatcher,Pcs,PcsEditDetector,PcsMerger,PcsTreeBuilder}.scala`。
- 现有 IDML 入口：`idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/{ThreeWayMerger,IdmlPackageMerger,IdmlMergeDriver}.scala`。
- 现有样本：`idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md`。

决策记录：

| 决策项 | 结论或待决策项 | 日期 | 原因 | 影响的 MR |
|---|---|---|---|---|
| 预期分层 | 区分论文、项目细化、IDML、工程契约 | 2026-11-06 | 避免错误引用论文 | 全部 |
| Delete/Edit | 严格检查整个被删子树 | 2026-11-06 | §6.3 optional；避免编辑丢失 | 07、09、10 |
| 结构推测合并 | 本轮不支持 | 2026-11-06 | §6.3 属于可选推测功能 | 06、09 |
| 属性协调 | 保留同一节点不同属性可合并的现有策略 | 2026-11-06 | ThreeWayMerger 的既有职责 | 06、10 |
| 文本原子单位 | 待决策：以当前文本节点原子能力为基线，或要求 token-aware；依据产品范围与实际入口实验冻结 | 待冻结时填写 | 08b 当前 expected 为 token-aware，不能伪报支持 | 03、08、09 |
| 06b 与 13 | 待决策：明确保守冲突条件或接受条件、受影响 Story 的识别；不能推断具体字符流入哪个 frame | 待冻结时填写 | 通用树模型不提供排版流结果 | 03、11、12 |
| 非法输入 | 重复身份、缺失 part、损坏 XML/ZIP 必须显式失败，不得成功丢数据；具体错误映射写入规格 | MR01 完成时填写 | 现有 matcher 假定 Self 唯一 | 04、11、12 |
| 写入失败与并发 | I/O 失败保留 ours；相同目标同时写入不属于支持范围；独立目标可并行 | 2026-11-06 | 现有顺序写包尚不能证明失败原子性 | 12 |
| 忽略字段与保留字段 | 待核对 `EditDetector.defaultIgnoredAttributes` 后逐字段冻结比较和输出规则 | MR01 完成时填写 | 避免不同层解释不一致 | 02、03、08、10 |

数值上限：

| 项目 | 数值 | 单位 | 计算方式 | 取值依据 | 执行位置 |
|---|---|---|---|---|---|
| 基础 corpus 覆盖 | 15 | 组 | 原有目录数，含 06b、08b | 当前 corpus | MR03、09 |
| 左右排列 | 2 | 次/组 | 正常与交换顺序 | §6.4 对称性 | MR09 |
| 生成测试规模及种子数 | 待决策 | 节点、编辑、种子 | 实际树节点；检测编辑数与生成操作数分开记 | 现有 CI 资源和样本分布 | MR10、13 |
| 性能门禁 | 待决策 | ms、MiB、倍数 | 分段计时、峰值内存、相对基线 | 固定环境预热测量与多次重复 | MR13、14 |
| 真实验收样本 | 待决策 | 组 | 按独立 base/left/right 组计数 | 覆盖文字、结构、引用、资产场景与授权样本可用性 | MR14 |

- 冻结时间点：文本与跨 part 规则在 MR03 实施前冻结；生成规模在 MR10 实施前冻结；性能参数在 MR13 实施前冻结；真实样本清单在 MR14 实施前冻结。MR01 必须记录决策方式与责任 MR，所有待决策项在对应截止点前写入结论、日期、原因；逾期阻塞依赖 MR。
- 当前待验证假设：无 Self 匹配能在 IDML 入口正确接线；全部 corpus 可被真实入口执行；输入元数据不会掩盖预期冲突；写包失败可通过同目录临时副本原子替换修复。后续 MR 用测试证明，失败即修复或记为发布阻塞。

Keep as-is:
- 不把本计划改成通用 XML 框架重写，不新增审批、签字或负责人确认流程。
- 保留 KML 模块及现有公开 API。

Done when:
- 五层测试、九部分计划和原有 15 组样本均分配到明确 MR。
- 每项已定决策有结论、日期和原因；待决策项有取值依据、冻结时间点与阻塞范围。
- 明确论文与领域策略边界；08b 不再同时存在互斥且未标模式的预期。
- 依赖图无环，后续引用只指向更早 MR。

## Batch 2: Establish test oracles and fixture contracts

MR count: 2

### MR 02 — Add semantic XML assertions
branch: feature/IDML-XML-Merge-Tests-MR02

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/MergeAssertions.scala（新增）
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/MergeAssertionsSpec.scala（新增）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlMergeAssertions.scala（新增）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlMergeAssertionsSpec.scala（新增）

What to change:
- 依赖 MR01。建立仅供测试的完整 XML 语义比较和冲突归一化辅助函数；IDML 辅助函数负责 part 路径与包断言。
- 因 `idml.dependsOn(core)` 不默认共享 core 测试类，两个模块保持薄辅助函数，各自测试；不为了共享少量断言修改生产依赖或发布测试包。
- 属性按键值集合比较，子节点严格按顺序比较，文本保留首尾空白、换行和混排位置。命名空间处理以 MR01 冻结的模型范围为准。
- 冲突按 part、slot、base/left/right 归一化排序；交换分支时只互换左右值，不删除 slot、base 或冲突条数。
- 失败消息提供首个差异路径和实际/预期值。仅显式声明的忽略字段可归一化。
- 依据：计划第五部分；论文 §2.3、§5、§6。

Keep as-is:
- 不修改 `GenericElement` 的生产相等语义，不排序原始 XML 子节点，不全局 trim 文本。

Done when:
- 仅属性排列不同通过；子节点换位、额外节点、缺失节点、文本多一个空格分别失败。
- 忽略字段与非忽略字段的负向测试通过。
- 冲突缺失、额外冲突、三方值错误均被断言发现。
- core/test、idml/test 可独立编译，不依赖另一模块的 Test classpath。

### MR 03 — Define executable corpus expectations
branch: feature/IDML-XML-Merge-Tests-MR03

Files:
- idml/tools/generate_three_way_corpus.py
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/01-single-add/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/01-single-add/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/02-single-delete/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/02-single-delete/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/03-single-move/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/03-single-move/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/04-both-add-diff-pos/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/04-both-add-diff-pos/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/05-move-move-divergent/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/05-move-move-divergent/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/06-delete-vs-modify/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/06-delete-vs-modify/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/06b-delete-thread-vs-content/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/06b-delete-thread-vs-content/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/07-same-story-diff-para/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/07-same-story-diff-para/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/08-same-para-overlap/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/08-same-para-overlap/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/08b-same-para-disjoint/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/08b-same-para-disjoint/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/09-zorder/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/09-zorder/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/10-group-ungroup/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/10-group-ungroup/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/11-thread-insert/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/11-thread-insert/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/12-next-divergent/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/12-next-divergent/expected.json
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/13-parentstory-vs-content/README.md
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/13-parentstory-vs-content/expected.json
- 该目录下成功案例的 expected/ 内预期 XML（新增；具体 part 文件在各 README 列出）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/CorpusContractSpec.scala（新增）

What to change:
- 依赖 MR01、MR02。为每组样本补充来源层级、论文章节、policy profile、预期节点身份、目标 part、成功结果或精确冲突对象，以及构造来源。
- 为 expected.json 定义版本化结构；保留旧字段或显式迁移，生成器与 Scala 读取方同时支持冻结后的版本。解析库选型沿用已有依赖；确需新增时只新增 Test 依赖。
- 08b 按 MR01 冻结模式给出唯一生效预期；若保存多个 profile，执行器必须显式选择，禁止“成功或冲突都算通过”。06b、13 只能使用已冻结领域规则。
- expected 由手工推导并核对的静态内容或独立的目标编辑构造产生，禁止调用待测 merger；生成输入与验证输出逻辑分离。
- 增加独立只读校验入口；核对现有 --verify 是否改写文件，若会改写则明确拆出只读命令。校验 schema、hash、共同 base、稳定 Self 和 required 文件。
- 依据：计划第二、七部分；论文 §3、§4、§7。

Keep as-is:
- 不把 XML 构造样本标成 InDesign GUI 导出，不重新分配未变化节点的 Self。
- 不用修改业务预期的方式适配当前实现。

Done when:
- 15 组全部有可执行、无歧义的 expected 和来源说明。
- 缺失 base、未知版本、损坏 JSON、错误 hash、未冻结 profile 均导致明确校验失败。
- 只读校验前后仓库样本字节不变；生成重跑结果符合声明的确定性规则。
- 未新增或修改真实样本原件；生成器校验不被描述为 merger 验证。

## Batch 3: Verify matching and relation encoding

MR count: 2

### MR 04 — Verify stable node correspondence
branch: feature/IDML-XML-Merge-Tests-MR04

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/TreeMatcherSpec.scala
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/ContentMatcherSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/TreeMatcherIdmlSpec.scala
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/TreeMatcher.scala（仅契约缺陷的最小修复）
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/ContentMatcher.scala（仅契约缺陷的最小修复）

What to change:
- 依赖 MR01。断言移动和修改后，同一 Self 仍对应同一 base 节点；新增相似节点不覆盖旧节点的匹配。
- 对每棵树检查匹配一对一、matched 与 unmatched 不重叠。区分同一身份跨版本出现和同一版本重复身份。
- 为无 Self 段落 A/B/C 构造左删 A、右改 C 的匹配预期；匹配应保持 B、C 身份。增加相同文本不同位置的歧义用例，冻结保守失败或歧义表示，不强行宣称能恢复未知编辑历史。
- 重复 Self 在输入验证或 matcher 边界显式失败，不允许 `.toMap` 静默覆盖后返回成功；在本 MR 明确执行边界并补兼容回归。
- 依据：论文 §3.1 规则 1、§4；计划图 6c。

Keep as-is:
- 不把 TreeMatcher.matchBySelf 改成隐式内容猜测入口；内容匹配继续由 ContentMatcher 明确承担。

Done when:
- 移动、插入、删除、内容更新、重复身份和歧义样本各有断言。
- A/B/C 用例的预期匹配被逐对检查，错误的绝对路径映射不能通过。
- 现有合法 Self 匹配结果保持兼容；相关 core 与 IDML 测试通过。

### MR 05 — Verify PCS encoding and edit detection
branch: feature/IDML-XML-Merge-Tests-MR05

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/PcsSpec.scala
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/PcsEditDetectorSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/PcsIdmlSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/PcsEditDetectorIdmlSpec.scala
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/Pcs.scala（仅必要修复）
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/PcsEditDetector.scala（仅必要修复）

What to change:
- 依赖 MR01、MR04。对空子列表、单子节点、首尾插入、中间删除、同父重排、跨父移动逐条写出完整 PCS 与 content 集合。
- 比较完整集合，覆盖 ListStart/ListEnd、每个叶子空子列表以及项目实际采用的 root 表示；与论文人工父节点表示不同处写明等价边界。
- 按同一匹配计算 E = T* − T*0；检查 unchanged 不产生编辑，移动的源上下文与目标上下文均出现，忽略字段不产生错误编辑。
- 增加有文本节点的最小关系样本，确保文本在有序子列表中有正确位置。
- 依据：论文 §2.3、§5、§6.1；计划第三部分。

Keep as-is:
- 不以操作历史代替状态差异；不要求一个 move 只能生成一条关系编辑。

Done when:
- 实际与预期完整关系集合相等，额外或遗漏关系使测试失败。
- 头、中、尾及空列表边界通过；单侧删除和跨父移动的旧位置闭合关系正确。
- 相同 Self 在不同匹配输入下的结果有明确测试，路径错配不能被 PCS 层掩盖。

## Batch 4: Verify reconciliation and reconstruction

MR count: 3

### MR 06 — Preserve compatible structural edits
branch: feature/IDML-XML-Merge-Tests-MR06

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/PcsMergerSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMergerSpec.scala
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/PcsMerger.scala（仅必要修复）
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMerger.scala（仅必要修复）

What to change:
- 依赖 MR01、MR02、MR05。补左移 b 到 a/c 后、右改 b.FillColor 的完整结果，断言顺序 a/c/b、颜色 Red、b 仅一次、其他内容不变。
- 补父节点移动与内部子节点重排，以及不同兼容位置插入；同时验证源位置闭合与目标位置准确。
- 补相同更新与相同结构调整不冲突；不同属性协调按 ThreeWayMerger 的项目策略验收，PcsMerger 的整体 content 原子语义单独验收。
- 复用 MR02 的完整比较，不只断言 conflicts 为空或 contain 一条关系。
- 依据：论文 §2.1、§3.1 规则 4—7、图 3；计划图 2。

Keep as-is:
- 不为同一间隙插入引入推测排序，不把业务属性细化规则追溯为论文的原子 content 定义。

Done when:
- 每个成功用例有完整 expected 树或完整关系集合。
- 移动与修改的任一结果丢失、节点重复、其他节点误改均使测试失败。
- 相同结果不产生假冲突；不同属性修改在最终入口合并成功。

### MR 07 — Detect contextual and deleted-subtree conflicts
branch: feature/IDML-XML-Merge-Tests-MR07

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/PcsMergerSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMergerSpec.scala
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/PcsMerger.scala（仅必要修复）
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/Merger.scala（仅必要修复）
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMerger.scala（仅必要修复）

What to change:
- 依赖 MR01、MR02、MR05、MR06。分别覆盖唯一 content、parent、predecessor、successor 被两个真实编辑破坏的情况；不把“同一业务动作”限定为一条底层冲突。
- 对 abcd/bacd/abdc 精确断言 b 的后继与 c 的前驱冲突；以 abcde/bacde/abced 合并成 baced 作为隔离对照。
- 覆盖 move/move、同间隙 insert/insert，以及 group/ungroup 实际关系矛盾；不能仅按操作名称判定。
- 严格 Delete/Edit 检查涵盖删除节点本身、删除祖先与任意深度后代更新或插入；检查最终不可达区域中的编辑，而非仅查已删除节点自己的属性。
- 在公共 IDML 入口执行严格策略；底层纯关系算法是否保留 optional 行为遵循 MR01，不无声改变默认 API 语义。
- 依据：论文 §3.1 规则 3、7c、7d；§5、§6.3、§6.4；计划图 6a、6b。

Keep as-is:
- 不通过保留 base 作为静默胜者来消除两项冲突编辑；不自动丢弃右侧编辑。

Done when:
- guards 用例返回完整预期关系冲突集合，隔离对照成功且完整结果为 baced。
- 祖先删除/深层编辑在严格入口报告冲突，未编辑子树的单侧删除仍成功。
- 各冲突的 base/left/right 正确，无关节点不被报告；左右交换后的对应断言通过。

### MR 08 — Preserve mixed content during tree reconstruction
branch: feature/IDML-XML-Merge-Tests-MR08

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/PcsTreeBuilderSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/PcsTreeBuilderIdmlSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMergerSpec.scala
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/PcsMerger.scala（仅清理被最终链绕过的旧 base 关系；保留新编辑关系及冲突报告）
- core/src/main/scala/com/phasmidsoftware/xmldoc/merge/PcsTreeBuilder.scala（仅必要修复）
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMerger.scala（必要的匹配接线修复）

What to change:
- 依赖 MR01、MR02、MR04、MR05。验证文本/元素/CDATA 混排、前后空白、空元素、连续文本及样式包装结构变化；以实际支持的节点模型声明输出约定。
- 将无 Self 的 A/B/C 案例贯通至 ThreeWayMerger；若入口仍按路径错配，接入已验证匹配，禁止仅在 mergeByContent 单元测试中证明能力后声称 IDML 已支持。
- 按冻结 profile 测试同一文本节点双边修改、相同修改、重叠和不重叠修改；原子模式的 08b 应冲突，token 模式必须真正合成两项修改并覆盖边界。若冻结 token 模式，新增专用实现文件及测试须在本 MR 开工前列入 Files。
- 关系冲突未解决时不得重建为成功树；缺失 content、重复父关系、循环或断裂链必须失败，不得死循环或静默省略。
- 依据：论文 §2.3、§4、§6、§7；计划第四至六部分。

Keep as-is:
- 不实现论文简化模型之外的全 XML infoset 重写；不改变忽略字段策略，不清理正文空白。

Done when:
- 混排内容完整往返，元素与文本的相对顺序和空白一致。
- 公共 IDML 入口得到 B、C′，不是 B、C；含歧义的输入按冻结契约处理。
- 08b 在唯一选定模式下通过；不得用 pending、ignore 或“二选一均可”替代。
- 非法关系重建明确失败，未被标记为成功。

## Batch 5: Execute corpus regressions and merge properties

MR count: 2

### MR 09 — Run every corpus case in both branch orders
branch: feature/IDML-XML-Merge-Tests-MR09

Files:
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayCorpusSpec.scala（新增）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/CorpusExpectations.scala（新增）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlMergeAssertions.scala
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/three-way-corpus/README.md
- build.sbt（仅确需 Test JSON 解析依赖时）

What to change:
- 依赖 MR01、MR02、MR03、MR07、MR08。发现全部场景目录，严格加载 MR03 契约，以案例名建立可单独运行的 ScalaTest 测试。
- 正常/交换排列分别使用独立临时副本。成功案例比较所有声明的 part 完整结果和结构身份；冲突案例比较冻结层级的完整冲突对象集合。
- 交换分支时按 MR02 互换冲突左右值；最终包只比较策略定义的语义结果，不要求 ZIP 字节或 ours 专有元数据相同。
- 对领域规则尚由 MR11 接入的 06b、13，在此仅交付通用入口执行及领域预期加载契约；不得虚报领域测试已通过。MR11 接入后由 MR12 执行完整领域预期，MR14 要求全部层级通过。
- 固定场景发现清单，防止缺目录、丢 expected 或空测试集合导致假成功。新增场景必须加入追溯表。
- 依据：论文 §7 的手工结果对照与对称性验证；计划第六、七部分。

Keep as-is:
- 不把生成器 --verify 作为合并测试，不原地调用 driver 修改 fixtures。

Done when:
- 原有 15 组都被发现，两种排列都实际执行；分层报告明确通用结果与领域验收状态。
- 删除一个样本目录、破坏 expected 或制造额外冲突会使测试失败。
- 无未声明的跳过；领域缺口显式列为 MR11/MR12 的发布阻塞。
- 并行运行不同场景不共享可写文件，重复运行结果一致。

### MR 10 — Verify merge invariants with generated trees
branch: feature/IDML-XML-Merge-Tests-MR10

Files:
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/MergePropertiesSpec.scala（新增）
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/MergeTreeGenerator.scala（新增）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayMergerPropertiesSpec.scala（新增）

What to change:
- 依赖 MR01、MR02、MR09。使用冻结规模和确定种子生成合法树；优先用现有 ScalaTest 循环，不为生成测试引入通用框架。
- 验证四项恒等关系、成功结果对称性、冲突交换一致性；忽略字段与输出保留规则按规格比较。
- 独立记录期望编辑及身份，检查最终可达树中的编辑保留。严格模式不能仅验证 E 在 Δ 中。
- 在原子 PCS 层验证 E⊆Δ⊆T*；属性细化层逐属性验证来源，不错误禁止来自两侧的合法属性组合。
- 对兼容操作生成成功样本，对有意矛盾操作检查冲突；不假定所有随机操作都应成功。
- 失败输出种子、三方最小 XML 和失败性质，支持同一种子精确重跑。
- 依据：论文 §6、§6.4；计划第六部分。

Keep as-is:
- 不用 merger 输出生成 expected，不给未定义行为添加性质断言。

Done when:
- 所有冻结种子通过；失败可单例重放。
- 注入丢编辑、偏向左分支或凭空关系的反例能被断言捕获。
- 无 Self 删除位移与严格 Delete/Edit 均进入生成或固定回归组合。

## Batch 6: Validate IDML package semantics and performance

MR count: 3

### MR 11 — Validate cross-part IDML references
branch: feature/IDML-XML-Merge-Tests-MR11

Files:
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/IdmlReferenceValidator.scala（新增）
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/IdmlPackageMerger.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlReferenceValidatorSpec.scala（新增）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlPackageMergerSpec.scala（新增）
- idml/docs/xml-merge-test-spec.md

What to change:
- 依赖 MR01、MR02。对候选合并包的逻辑视图验证引用，再交给 driver 写入；不能只验证输入包。
- 明确索引的身份范围、允许的空引用哨兵、外部资源引用例外及 Next/Previous/ParentStory 检查范围，不把所有字符串属性当作 Self 引用。
- 检查要求存在的 Story 与 frame、前后链接互反、非法线程环、冻结范围内的身份碰撞；错误定位到 src、Self、属性和目标。
- 06b、13 按 MR01 冻结的 base/left/right 依赖条件判断，分别证明“引用合法”与“并发编辑意图冲突”；不能把静态引用合法性等同于领域冲突检测。
- 复用 `PartConflict` 与 `StructuralConflict` 的现有输出接口；不凭空报告排版后某段文字落在哪个 TextFrame。
- 依据：计划第七、八部分；论文 §9 仅提供领域专门化方向，不作为这些 IDML 规则的直接证明。

Keep as-is:
- 不修改通用 PcsMerger 的 XML 语义，不新增排版引擎，不擅自修复引用后声称合并无冲突。

Done when:
- tfX 插入后的双向链、ParentStory、起终点通过；缺目标、单向链、非法环分别失败。
- 12-next-divergent 的属性冲突与线程合法性分别有证据。
- 06b、13 的正向、负向、仅一侧变化对照通过；无关 Story 内容编辑不被保守规则误伤。
- 验证失败不进入写包阶段；所有错误有可定位上下文。

### MR 12 — Preserve package integrity across merge outcomes
branch: feature/IDML-XML-Merge-Tests-MR12

Files:
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlMergeDriverSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlPackageSpec.scala
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlPackageMergerSpec.scala
- idml/src/main/scala/com/phasmidsoftware/xmldoc/idml/IdmlMergeDriver.scala
- core/src/main/scala/com/phasmidsoftware/xmldoc/xml/Zip.scala（仅必要写入修复）
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/ThreeWayCorpusSpec.scala

What to change:
- 依赖 MR01、MR02、MR03、MR11。覆盖 part 新增、删除、同 src 双侧不同新增、删除/修改、多个 part 独立更新；完成 corpus 的领域层与 driver 层验证。
- 成功后重新打开 ZIP、解析所有相关 XML，检查 part 清单与 designmap 互相一致；比较完整目标 XML及未编辑二进制 entry 的字节。
- 无 part 增删时 designmap 字节保持不变；增删时只允许目标 idPkg 引用差异，保留 aid PI 和其他内容。
- 冲突前后比较 ours 哈希。读错、XML 错、写 part 失败、patch designmap 失败及最终替换失败均测试失败契约。
- 当前 driver 逐项写 ours；改为在同目录临时副本完成全部修改与校验，再原子替换 ours。替换前失败清理临时文件并保留原件；不支持原子替换时明确失败，不降级为破坏性写入。
- 测试故障注入限于窄写入边界；不为此建立通用存储框架。不同输出目标可并行；不承诺相同目标并发写入。
- 通过子进程核对 main 的退出码：成功 0、冲突 1、参数或读写失败 2。
- 依据：计划第八部分和现有 IdmlMergeDriver 接口契约；原子写入属于工程要求。

Keep as-is:
- 保留 merge(base, ours, theirs) 成功修改 ours 的 Git driver 契约及外部签名。
- 不完整三方合并 designmap，不比较无意义的 ZIP 容器字节稳定性。

Done when:
- 成功包能重新打开，新增/删除 part 引用准确，未编辑资产字节一致。
- 冲突及各注入失败阶段 ours 与调用前字节一致；base、theirs 始终不变。
- 重复测试使用全新输入副本，临时文件无泄漏；独立目标并发不串写。
- 全部 corpus 在适用的包、领域、driver 层满足唯一预期；退出码断言通过。

### MR 13 — Measure merge scalability by pipeline stage
branch: feature/IDML-XML-Merge-Tests-MR13

Files:
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/MergeBenchmark.scala（新增）
- idml/docs/xml-merge-performance.md（新增）
- core/src/test/scala/com/phasmidsoftware/xmldoc/merge/MergeTreeGenerator.scala（若需复用，改为本模块独立生成器；具体路径在实施前列明）
- build.sbt（仅基准独立运行配置需要时）

What to change:
- 依赖 MR01、MR04、MR05、MR07、MR08。基准自带本模块可编译的生成输入，不隐式依赖 core Test classpath；若 MR10 已合并，可复用其生成规范而非直接引用不可见测试类。
- 冻结节点数、检测编辑数、操作比例、种子、预热次数、重复次数、JDK/JVM 参数与机器信息后测量。生成操作数与 E 大小分列，避免把一次 move 当一条 PCS 编辑。
- 分别测 XML 解析、匹配、协调/重建、写包及总耗时，记录峰值内存与正确性校验结果；纯合并计时输入预先解析和匹配。
- 生成无冲突树，insert/delete/move/update 按冻结的等概率抽样方案执行；记录因合法性约束被拒绝重采样的次数及实际分布。节点内容大小有界，另列长文本实验。
- 给出重复测量分布和 t=c0+c1n+c2e 拟合记录；不把单次耗时、R² 或端到端耗时当作 O(n log n) 的证明。
- 依据：论文 §6.5、§7、图 6；计划第九部分。

Keep as-is:
- 默认 unit test 不执行长时间基准；不在基准 MR 顺手优化算法。

Done when:
- 明确的独立命令能重跑，输出包含环境、种子、规模、分段时间与内存。
- 空编辑、小规模、冻结最大规模、长文本分别有记录，所有生成输出先通过正确性验证。
- 门禁阈值在正式测量前冻结；超阈值记录为阻塞，不能事后抬高阈值掩盖回归。
- 比较论文范围时排除解析与匹配，端到端指标另列。

## Batch 7: Verify end-to-end evidence and release readiness

MR count: 1

### MR 14 — Verify complete merge acceptance evidence
branch: feature/IDML-XML-Merge-Tests-MR14

Files:
- idml/src/test/scala/com/phasmidsoftware/xmldoc/idml/IdmlMergeAcceptanceSpec.scala（新增）
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/real-world-corpus/manifest.json（新增）
- idml/src/test/resources/com/phasmidsoftware/xmldoc/idml/real-world-corpus/README.md（新增）
- 该目录中 MR01 冻结清单指定的 base/left/right 与 expected 文件（新增；不得纳入无授权文档）
- idml/docs/xml-merge-test-evidence.md（新增）
- idml/docs/xml-merge-test-spec.md
- .github/workflows/idml-merge-tests.yml（新增）

What to change:
- 依赖 MR01—MR13。建立“计划条目→依据→profile→用例→测试层→执行命令→证据→状态”追溯表，所有要求支持的条目必须有结果；pending 不得计为通过。
- CI 执行 `sbt "core/test" "idml/test"` 和 MR03 的只读 corpus 校验；修改 core 生产代码时运行 `sbt "kml/test"`。基准通过独立作业或明确人工命令执行并保留证据，不混入普通单元测试。
- 新增少量跨层验收，仅验证真实文件→入口→候选领域检查→原子写出→重新读取的整链路，不复制前述全部单元断言。

发布检查清单：
1. 检查文本模式、领域规则、忽略字段和数值门禁已按截止点冻结。
2. 在干净副本执行单元、集成、corpus 与适用 KML 回归，记录 commit、命令、退出状态和日志。
3. 执行损坏输入、冲突及注入写失败验收，核对 ours 原件哈希。
4. 核对真实样本授权、来源、InDesign 版本、操作步骤与输入 hash。
5. 执行人工验收与性能测量，汇总失败与处理结果；没有数据库迁移或回填步骤。

人工抽查：
- 按 MR01 冻结数量和清单，覆盖文字编辑、对象移动与子树调整、线程/Story 引用、资源或二进制资产保留。
- 每个成功样本在 InDesign 打开、保存、关闭后重开；记录警告、文本、对象顺序、线程关系和可见排版结果。
- 每个冲突样本核对冲突对象及 ours 未改变。真实导出样本与 XML 构造样本分别标注。
- 缺少 InDesign 环境或授权样本时，记录明确未完成证据并阻塞本轮可交付声明，不能以 XML 能解析替代应用验收。

性能与完整性检查：
- 附 MR13 环境与原始测量结果、失败用例复现输入及修复提交。
- 语义比较只忽略冻结字段；记录资产 entry hash、part 清单、引用检查和 designmap 保留结果。

Keep as-is:
- 不重定义前述 MR 已冻结的规则，不以人工抽查替代自动测试，不在验收 MR 中夹带新功能。

Done when:
- 自动测试与只读样本校验通过，CI 不跳过本轮要求支持的场景。
- 计划九部分、五层测试与 15 组原有用例均有对应证据；新增 guards、深层 Delete/Edit、无 Self、混排内容已纳入。
- 每组 corpus 两个分支顺序均执行；真实样本打开、保存、重开完成并记录。
- 写包失败原子性与性能门禁通过；所有发布阻塞已消除。
- 证据基于最终待交付 commit；后续影响行为的修改触发相关验证重跑。

## Launch blockers

以下任一项未完成时不能发布：
- 文本原子单位、08b profile、06b/13 领域规则、忽略字段或门禁阈值到期未冻结。
- expected 来自待测 merger，或只按当前错误结果修改 expected。
- 使用局部 contain 或 conflicts.isEmpty 代替成功结果的完整断言，导致多余关系或丢编辑无法发现。
- 15 组中任一目录未发现、未执行、仅运行一个分支顺序，或领域层被跳过却标记通过。
- A/B/C 无 Self 案例只在 core 特殊入口通过，实际 IDML 入口仍丢失 C′。
- 严格 Delete/Edit 未检查完整被删子树；guards 或唯一关系矛盾被静默选边。
- 不同测试层对同一 profile 使用互相矛盾的预期。
- designmap 与 parts 不一致、目标引用不存在、线程互反性错误、非法环或资产丢失。
- 冲突或 I/O 失败后 ours 被部分写入；base、theirs 或样本原件被修改。
- 真实 InDesign 验收未完成，或只凭 XML 可解析声明可用。
- 性能证据缺少环境、输入规模或分段范围，或冻结阈值未满足。
- core/test、idml/test、适用的 kml/test 或只读样本校验失败；关键证据不是最终提交产生。

## Explicitly deferred

以下内容不进入上述 14 个 MR：
- 未被 MR01 明确选择的 token-aware 文本合并能力；选择该能力时须先补齐 MR08 的具体 Files 与实现范围，不能以本条规避已选范围。
- 推测性同间隙插入排序、自动忽略某侧移动、任意 n-way merge。
- copy/glue 的特殊节点语义；按论文 §4 的边界保留 insert/delete 表达。
- 完整 XML infoset 扩展、通用 XML canonicalization、无关序列化器重写。
- 完整 designmap 三方合并、自动修复所有 IDML 引用、InDesign 排版引擎或字符流重建。
- 无关 KML 功能、通用测试框架、生产 benchmark 平台及性能优化专项。
- 同一 ours 路径的并发写入支持、崩溃与断电后的跨平台持久化保证；本轮覆盖函数失败返回与原子替换语义。
- 无关权限系统、数据库迁移、历史回填、网站部署或 GitLab/GitHub 远端 MR 创建。
