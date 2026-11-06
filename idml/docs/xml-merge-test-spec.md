# IDML XML Merge Test Specification

状态：MR03、MR11 已合并为 MR0311；MR12 包完整性与原子写入已实现，16 组 corpus 双向包入口/driver 验证通过。其他 MR 与 InDesign 应用验收仍按原计划执行。记录日期：2026-11-06。

本规格是后续 MR 的预期来源，不是当前实现通过证明。执行顺序以 [MR 拆分计划](xml-merge-test-mr-breakdown.md) 为基础；下文冻结决策替代计划中的相应待决策条目。MR01 只修改文档；本轮 MR03 修订样本契约、生成器、读取器和测试，不修改生产合并算法或 driver。

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
- 项目显式启用论文 §6.3 的可选严格 Delete/Edit 检查；最终不可达的被删区域中不得含编辑。移动后仍在最终可达树中的后代，不能仅因旧 Group 消失被视为丢失。
- 按论文 §6 保留编辑并移除与编辑矛盾的旧 base 关系；两个编辑互相矛盾才构成核心冲突。不启用局部坐标强制冲突或自动坐标转换。
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



## 冻结决策

以下选择自 2026-11-06 生效，后续修改须同步规格、样本 profile 和对应测试，不能只改 expected 使当前实现通过。

| 决策项 | 结论 | 日期 | 原因与适用入口 | 责任 MR |
|---|---|---|---|---|
| 预期来源 | 分为论文规则、项目细化、IDML 领域、工程契约；每条用例分别标注 | 2026-11-06 | 通用树模型不能证明领域语义 | 03、09、14 |
| 文本模式 | 本轮唯一验收 profile 为 `strict-text-node-v1`；文本节点内容原子比较，不开发 token merge | 2026-11-06 | 论文 §2.3、§6.3；当前入口没有 token 协调能力 | 03、08、09 |
| 08b | 同一文本节点的不同双边修改必须冲突，即便单词范围互不重叠 | 2026-11-06 | 以文本节点为原子单位，不能同时接受冲突或成功 | 03、08、09 |
| Delete/Edit | IDML 公共入口严格检查最终被删区域；移动后仍可达节点的编辑不得误报为丢失 | 2026-11-06 | §6.3 optional 检查由项目明确启用；底层关系森林不等于最终可达树 | 07、10 |
| case 10 | accept；保留原有输入，合并解组与 r3.ItemTransform 更新；只声明通用 XML 成功 | 2026-11-06 | §2.1、§3.1 规则 4/7、§6；旧 conflict 预期是测试设计错误 | 03、06、09 |
| case 10b | conflict；r3 的父节点 base=g1、left=ud1、right=g2，两个新编辑要求矛盾 | 2026-11-06 | §5 唯一 parent、§6.3 Position/Position | 03、07、09 |
| 结构与属性 | 不启用推测合并；同一节点不同属性的独立修改仍可合并 | 2026-11-06 | PCS 按关系一致性判断；属性细化沿用 ThreeWayMerger 契约 | 06、07 |
| 领域冲突 | 使用下文 story 级保守依赖规则；06b、13 为要求支持的冲突 | 2026-11-06 | 不依赖不可得的字符排版流映射 | 11、12 |
| 身份与输入 | 各 part 内重复 Self、包级 frame/Story 索引重复身份显式失败；不同身份类别不盲目建立全包通用唯一性约束 | 2026-11-06 | TreeMatcher.matchBySelf 当前假定唯一，不应静默覆盖 | 04、11 |
| 忽略字段 | 仅 LinkImportTime、StyleUniqueId；具体比较与保留规则见下文 | 2026-11-06 | 与 EditDetector.defaultIgnoredAttributes 一致 | 02、03、08、10 |
| 写入 | 冲突和函数返回 I/O 失败均保持 ours 原字节；独立目标可并行，同目标并发不支持 | 2026-11-06 | MR12 同目录临时副本校验后原子替换；不支持原子替换时失败 | 12 |
| 外部接口 | 保留现有 merge 方法签名和 CLI 退出码 | 2026-11-06 | 不改变 Git driver 接入方式 | 11、12 |

### 领域依赖规则

1. 从 base 的 TextFrame 身份、ParentStory、NextTextFrame、PreviousTextFrame 建立 frame→Story 与线程关系；允许的空引用哨兵须由 MR11 根据真实样本列举，不把空值当悬挂引用。
2. 一侧删除 base frame，或修改其 ParentStory/线程拓扑，构成 Story 布局依赖变化。受影响集合包含变化 frame 的 base Story、修改后 Story，以及变化边两端 frame 在 base/该分支所属的 Story；空值不计入。
3. 另一侧相对 base 对该集合内 Story 的正文树做真实编辑，报告领域冲突。正文树指 Story 的文本、段落/字符样式范围及其内容结构；仅忽略字段变化不算真实编辑。正文比较的完整范围与路径由 MR11 固化为共享测试样本，不以排版后具体字符落点判断。
4. 左右对称执行。06b 为删除 tf2 与修改 story1；13 为 tf2 从 story1 切到 story2 并脱链，与修改 story1。两者均须冲突。
5. 仅单侧变化、仅无关 Story 编辑、仅忽略字段变化不触发本规则；仍须通过其他结构及引用合法性检查。
6. 本规则只对“布局依赖变化 + 另一侧相关正文编辑”保守报冲突，不意味着任意两个 frame 操作都冲突。合法插入 tfX 且另一侧不变可成功；12 的同属性分歧独立报告。
7. 候选包静态校验与并发意图冲突是两类检查。缺目标、非互反链接、非法环不能通过；即使引用合法也可能命中上述领域冲突。

### MR11 引用范围与证据（2026-11-06）

- 校验视图为 designmap 声明的所有 XML part，先加载并检查输入身份，再合并各 part；以 left 的逻辑视图应用全部 PartContent/PartRemoved，校验候选结果后才允许 driver 写入。已存在通用或领域冲突时不构造可写候选。缺失声明 part、XML 解析失败、重复 src、身份碰撞返回 Failure；候选引用错误和并发意图冲突通过 PartConflict/StructuralConflict 返回，定位 src、Self、属性、目标。
- 身份范围：每个 part 的所有显式 Self 必须唯一；TextFrame 与 Story 各自另建包级唯一索引，覆盖嵌套在 Spread、MasterSpread、Story 等 part 内的节点。两类别之间允许同值，不将不同资源类别的 Self 建成全包通用索引。TextFrame/Story 必须有非空 Self。
- 引用白名单仅为 TextFrame.ParentStory、NextTextFrame、PreviousTextFrame。ParentStory 必须指向已声明 part 中的 Story，不能是空值。Next/Previous 的缺省属性、空字符串和 `n` 表示端点，不是悬挂引用；真实 HelloWorld2 的 uf8/u122/u13a 及 corpus tf1/tf2 使用 `n`。空字符串与缺省是显式兼容约定，有单独测试；没有额外猜测 None/null 等哨兵。
- 所有非空线程边必须存在、双向互反、两端 ParentStory 相同；Next 链不能成环，因而有限合法链具有起终点。允许同 Story 有多个独立链（corpus story2 的 tfA、tfB），不推测实际排版流。TextPath 等其他对象的线程模型不在本次冻结范围。
- Link 的 URI、AppliedObjectStyle、ItemLayer、资源路径等不在本引用白名单；外部资源字符串不作为 frame/Story Self 查找，不承诺本次校验这些资源的可达性。designmap 其他内容仍依现有 driver 保留策略处理。
- 正文比较范围冻结为 `part/Story[Self]/*` 的完整有序子节点树，包含文本/CDATA 类型、精确空白、ParagraphStyleRange、CharacterStyleRange、Content、嵌套表格/内联结构及其属性。属性顺序无关，递归忽略默认两字段；Story 根属性视为元数据，不单独触发正文编辑。共享构造样本在 IdmlReferenceValidatorSpec 中覆盖此范围，无排版后字符落点推断。
- IdmlReferenceValidatorSpec 覆盖合法插链、缺目标、单向链、跨 Story 边、互反环、身份碰撞、空哨兵、布局/正文正反向及单侧/无关 Story/忽略字段对照、旧/新 Story 与边端点依赖。
- IdmlPackageMergerSpec 使用原始只读 corpus 验证 06b、13 的合法布局与双向领域冲突及单侧对照；11 的完整候选与双向链；12 的 NextTextFrame 属性分歧与独立静态校验（left、right 均合法，属性分歧仍冲突）。另构造三个均合法的输入，使合并候选成为悬挂引用，证明 driver 返回冲突且 ours 字节不变；重复身份、缺 part 和领域冲突同样不写包。
- 旧 IdmlMergeDriverSpec 的成功删除 Story 测试同时删除引用 frame，避免将悬挂 ParentStory 当作成功；不修改受版本控制的 IDML 样本。此处仅保证验证失败不进入写包，写入中 I/O 失败原子性仍归 MR12。
- 验证：`core/test` 212 项通过，既有 `ExtractorsSpec.extractor3B` 1 项 ignored 未改动；`idml/test` 77 项全部通过，零 ignored/pending。补强完整冲突对象断言后，`idml/testOnly *IdmlReferenceValidatorSpec` 7 项再次通过。`git diff --check` 通过，corpus 字节未修改。本 MR 不替代 MR12/MR14 的完整 corpus 与 InDesign 验收。

### 忽略字段与 XML 比较

- 默认忽略字段精确为 `LinkImportTime`、`StyleUniqueId`。`Self`、`ParentStory`、`NextTextFrame`、`PreviousTextFrame` 及正文不得忽略。
- 忽略仅用于编辑检测与语义相等，不删除实际属性。已匹配且 base 存在的节点保留 base 的忽略字段值；新插入节点保留其来源分支值。双侧插入同身份且仅忽略字段不同，选择按原始 UTF-8 字节字典序较小的完整属性集合，作为对称且确定的保留规则；该组合与格式应在 MR08 实现前增加专门用例，当前支持状态未验证。
- 完整包中未触及 entry 保留 ours 原字节；此工程保留规则不受树级 base 取值规则覆盖。包级对称比较可排除上述两字段，不要求 ZIP 元数据字节对称。
- 属性按键值集合比较；子节点顺序严格比较；GenericText 与 GenericCData 的内容及节点类型分别保留。不得全局 trim 或合并空白。
- GenericElement 保存限定名称字符串与显式 xmlns 属性，不具备完整 expanded-name/URI 标准化模型。限定名称及保存的 xmlns 声明精确比较；仅前缀改变但 URI 相同不保证被视为相等。带前缀属性或不受支持的 infoset 信息不得声称已经无损往返，新增支持不属于 MR01。

### 失败契约

| 情况 | API 结果 | CLI | 写入要求 |
|---|---|---|---|
| 成功 | Success(空冲突列表) | 0 | 完成候选包校验后替换 ours |
| 内容、结构或领域冲突 | Success(非空 PartConflict) | 1 | ours 不变 |
| 输入损坏、重复身份、必需 part 缺失、XML 解析失败、I/O 失败 | Failure，含可定位错误 | 2 | ours 不变 |
| 参数数量错误 | 不调用 merge | 2 | 不写文件 |

不得将输入错误包装成正常的无冲突成功。发现部分校验只能在后续 MR 实现时，在证据表标记缺口，不把目标契约写成现状。不同目标可并行；同一目标并发写入、断电持久性与进程被强杀不在本轮保证范围。

## 计划追溯

| 计划部分 | 交付 MR | 验收内容 |
|---|---|---|
| 一：预期依据 | 01、03、14 | 四类来源、profile、证据闭环 |
| 二：测试样本 | 03、14 | 独立 expected、输入身份、真实导出来源 |
| 三：五层测试 | 04—09、11—12 | 匹配、关系、协调、重建、包入口 |
| 四：场景矩阵 | 05—08 | 操作组合、guards、深层删除、无 Self |
| 五：断言 | 02、06—09 | 完整结果与完整冲突对象 |
| 六：性质 | 09—10 | 两种顺序、恒等关系、编辑保留、来源约束 |
| 七：已有用例 | 03、09、11—12 | 全部 16 组（原有 15 + 10b）发现与适用层级执行 |
| 八：端到端 | 11—12、14 | 引用、原子写入、InDesign 验收 |
| 九：规模与顺序 | 13—14 | 分段性能、输入规模与最终证据 |

五层分别归属：匹配 MR04；PCS/检测 MR05；协调 MR06—07；重建 MR08；整包 MR11—12；MR09/14 贯通入口。

| 目标案例 | 唯一目标预期 | 来源 | 交付 MR |
|---|---|---|---|
| 01-single-add | accept | §3.1 规则 5 | 03、09 |
| 02-single-delete | accept；引用仍合法 | 规则 6 + IDML | 03、09、12 |
| 03-single-move | accept | 规则 7 | 03、09 |
| 04-both-add-diff-pos | accept | 规则 5、§5 | 03、09 |
| 05-move-move-divergent | conflict | §6.3 Position/Position | 03、07、09 |
| 06-delete-vs-modify | conflict | 项目启用 §6.3 Delete/Edit | 03、07、09 |
| 06b-delete-thread-vs-content | conflict | story 级领域依赖规则 | 03、11、12 |
| 07-same-story-diff-para | accept；两项正文编辑保留 | 规则 4、§4 | 03、08、09 |
| 08-same-para-overlap | conflict | 文本节点原子策略、§6.3 | 03、08、09 |
| 08b-same-para-disjoint | conflict | strict-text-node-v1 | 03、08、09 |
| 09-zorder | accept；关系兼容 | 规则 7、§5 | 03、06、09 |
| 10-group-ungroup | accept；解组与属性更新均保留 | §2.1、§3.1 规则 4/7、§6 | 03、06、09 |
| 10b-divergent-parent | conflict；两个不同的新父节点 | §5 唯一 parent、§6.3 Position/Position | 03、07、09 |
| 11-thread-insert | accept；双向引用一致 | 规则 5 + IDML | 03、11、12 |
| 12-next-divergent | conflict | 同属性分歧 + IDML | 03、11、12 |
| 13-parentstory-vs-content | conflict | story 级领域依赖规则 | 03、11、12 |

## 数量、预算与后续冻结项

| 项目 | 数值或待决策项 | 单位与计算 | 依据 | 冻结时间点 |
|---|---|---|---|---|
| corpus | 16 | 原有 15 组 + 10b | 固定清单，8 accept/8 conflict | 2026-11-06 |
| 输入 IDML | 48 | 每组 base/left/right | 原有 45 个字节不变，10b 新增 3 个 | 2026-11-06 |
| 分支排列 | 2 | 正常与交换各一次/组 | §6.4 | 2026-11-06 |
| 生成规模和种子数量 | 8 个种子（0—7）；8/32/96 节点；每组合 7 个兼容 recipe | 根及 3 个 Group 计入节点；生成树无正文节点；每模块 168 个成功样本，另有 24 组有意冲突 | 2026-11-06 试运行 core 2.3s、IDML 3.8s；见下方冻结记录 | MR10 正式验证前冻结，2026-11-06 |
| 基准规模、预热和重复数 | 32/128/512 节点；种子 13/37/73；操作预算 0/4/16；另有 64-KiB 长文本；3 次预热、5 次重复 | 25 场景、125 次正式测量；操作与 E 分列 | 校准与正式原始 JSON 见性能文档 | 2026-11-06 正式测量前冻结 |
| 性能阈值 | total≤250 ms、merge≤100 ms、heap≤960 MiB、中位数回归≤4倍（基线下限1 ms） | 固定本机环境，125 次正式测量全部通过 | mr13-gates.json 在正式测量前提交冻结 | 2026-11-06 |
| 真实验收数量与样本清单 | 冻结 4 组计划：3 accept、1 conflict；目前 0 组已提供 | real-01..04 见制作指南与 manifest | 用户确认无现成样本；必须另行制作并提供授权及 InDesign 记录 | 2026-11-06，MR14 实施前冻结；缺证据阻塞发布 |

以上是后续测量型决策，不阻塞文档 MR01；逾期未冻结则阻塞对应实施或可交付声明。未定义新的生产输入数量上限，不将测试生成规模误当生产限制。

## 当前缺口与验收规则

- MR03 已将原有 15 组 expected.json 从 schema 2 显式迁移到 schema 3，并新增 10b，共 16 组、8 accept/8 conflict。case 10 成功，10b 冲突；08b 保持 strict-text-node-v1 冲突。schema 1/2 均被显式拒绝。此状态只证明样本契约，不证明实际 merger 全 corpus 通过。
- 06b、13 的跨 part 意图检查已由 MR11 在包入口验证；MR12 已贯通 corpus 的领域与 driver 层，最终应用验收仍归 MR14。
- 无 Self 匹配进入 ThreeWayMerger 的正确性、元数据处理、严格深层 Delete/Edit 均是待验证假设。
- driver 已由 MR12 改为同目录临时副本内写入与校验，再原子替换；返回冲突及已覆盖 I/O/校验失败时保留 ours。进程被强杀、断电及同目标并发不在保证范围。
- 后续每 MR 合并后须可构建、相关测试通过；修复与回归同 MR 交付，不允许为了主干绿灯静默跳过要求支持的用例。

## Launch blockers

- 到期策略、性能或样本决策未冻结。
- 任一要求支持的场景缺证据、只执行一侧顺序、误把生成器校验当合并验收。
- case 10 仍强制冲突，或 10b 未表达两个真实新父节点；16 组清单与状态、版本不一致。
- 无 Self、guards、深层 Delete/Edit、正文空白或完整结果比较失败。
- 06b/13 未按领域规则执行，引用、part/designmap 或资产保留失败。
- 冲突或 I/O 失败改变 ours，或修改 base/theirs/原样本。
- core/test、idml/test、适用 KML 回归、只读 corpus 校验、冻结性能门禁或真实 InDesign 打开/保存/重开验收未通过。
- 验收证据不是最终提交结果。MR01 文档验证不替代 MR14 交付验证。

## Explicitly deferred

- token-aware 文本合并、推测结构合并、n-way、copy/glue 特殊语义。
- ItemTransform 坐标转换、局部坐标强制冲突或视觉位置意图判定。真实 InDesign 打开/保存验收仍需执行，但 XML 成功不证明视觉位置符合意图。
- 完整 infoset 与命名空间标准化重写、完整 designmap 合并、排版引擎。
- 同一目标并发写入、断电持久性与无关 KML/基础框架重构。
- 部署、数据库迁移、历史回填；本轮没有这些步骤。


## MR03 schema 3 契约与证据修正（2026-11-06）

- 实施文件除原计划清单外，增加 `idml/tools/test_three_way_corpus.py`；Scala 版本化读取器随 `CorpusContractSpec.scala` 交付，可由 MR09 复用 `CorpusContract`，不引入生产 JSON API。唯一新增依赖为 IDML Test 范围的 `com.lihaoyi:ujson_2.13:4.4.3`，使用已缓存的 Scala 2.13 二进制兼容库。
- `--verify`、`--verify-only` 与无参数均只读；`--generate` 显式重建，并可用 `--output` 指向隔离目录。禁止同时指定生成与只读标志。校验和重建均不调用待测 merger。
- 成功预期的 `oracle.entries` 包含完整 ZIP entry 清单。变化 XML 的 `source=snapshot` 与 `path=expected/<entry>` 定位完整快照；其余 `source=base` 必须匹配 base 字节。哈希用于完整性；后续 XML 合并结果使用 MR02 语义比较，不要求输出序列化字节一致。二进制资产用字节校验。
- 冲突预期的 `oracle.assertions` 保留 `level/part/target/field/base/left/right/type/relatedParts`。`generic` 由 MR09 映射实现 slot，`domain` 的跨 part 规则由 MR11/12 实现。case 10 无冲突对象，10b 使用 generic 的 r3/parent 三方值；不强制整个案例只有一条底层关系报告。业务冲突对象不是底层关系报告条数或其字符串格式的保证。
- 证据修正：10 的右侧只把 `r3.ItemTransform` 从 `1 0 0 1 340 100` 改为 `1 0 0 1 380 140`，没有新的 parent 编辑。旧 schema 2 为维持 conflict 目标补入的 `parent-context-local-transform` 规则已撤销，不得在生产算法中实现。现按论文 §2.1、§3.1 规则 4/7、§6 要求 accept：g1 消失，r3/r4 提升到 ud1，r3 使用右侧属性值且只出现一次，其余内容保留；新增完整成功快照。
- 10b 的共同 base 在 g1 后、tf1 前插入 g2，g2 含不变子节点 r6。左解组后 r3/r4 位于 Spread，右侧 r4 留在 g1，r3 移到 g2 的 r6 后。g2/r6 在三份输入中身份稳定；r3 的 g1/ud1/g2 三方父节点构成真正的双边编辑矛盾，来源为论文 §5、§6.3。结构关系的完整冲突报告与左右交换行为仍由 MR07/MR09 验证，本轮未调用 merger 生成预期。
- schema 3 的 migration 对原有场景记录 fromSchemaVersion=2；新 10b 没有旧文档，记录 null。schema 1/2 均提示显式迁移，不接受旧 case 10 冲突与新成功两种结果。
- 06-delete-vs-modify 的 base `FillColor` 实际缺失，用 JSON null 表示，不能假定 Color/Black。
- 原有 15 组输入共 45 个 IDML 文件均保持原字节；10b 仅新增 3 个，共 48 个。全部来源仍为 XML 构造，不是 InDesign 实际导出；生成确定性和契约读取通过不代替 MR09/12 的合并结果测试。

### 原 MR03 schema 2 验证记录（历史，不能作为本轮 schema 3 证据）

- `sbt "core/test" "idml/test"`：core 212、IDML 69 项通过；其中新增 CorpusContractSpec 4 项，无跳过。
- `python3 -m unittest discover -s idml/tools -p 'test_three_way_corpus.py'`：12 项通过；包含只读校验、损坏输入、重新封装哈希后的篡改检测和全量重建字节确定性。
- `python3 idml/tools/generate_three_way_corpus.py --verify`：15 组通过；校验前后内容哈希与 mtime 不变。45 个 IDML 输入已逐个对照 MR02 提交字节，全部一致。
- 以上为本地验证，不代表远程 CI、实际 merger 全 corpus 执行或 InDesign 验收完成。

### 新版 MR03 schema 3 验证记录

- `sbt "core/test" "idml/test"`：core 212、IDML 71 项全部通过，无失败、跳过或 pending。CorpusContractSpec 包含 6 项，新增 case 10 成功目标与 10b 双边新父节点契约检查。
- `python3 -m unittest discover -s idml/tools -p 'test_three_way_corpus.py'`：17 项全部通过，包含 schema 1/2 拒绝、旧 case 10 冲突拒绝、16 组固定状态、10b 缺失拒绝及篡改检测。
- `python3 idml/tools/generate_three_way_corpus.py --verify`：16 组、schema 3、strict-text-node-v1 校验通过；前后全部文件内容哈希与 mtime 一致。
- 临时目录独立重建与仓库完整文件清单及字节一致；原有 45 个输入逐个对照旧 MR03 提交未变，仅新增 10b 的 3 个输入包。case 10 快照独立构造，生产合并算法未修改。
- 本记录是本地样本契约与既有回归验证，不代表 MR06/MR07/MR09 的实际 merger 场景验证、远程 CI 或 InDesign 应用验收已完成。

## MR12 package integrity evidence (2026-11-06)

- Branch ancestry: MR03 `d047951` + MR11 `ce5ae98` → MR0311 `bd1a657` → MR12. Each new branch was published before its implementation was written. The MR0311 documentation conflict preserves both schema 3 and the MR11 rules; its IDML regression passed 83 tests.
- The public merge signature and CLI remain unchanged. The driver creates a unique `.idml-merge-*.idml` copy beside ours, applies all writes and manifest patches there, reopens the ZIP, parses every declared XML part, checks the complete candidate inventory/XML and references, then uses `ATOMIC_MOVE` with `REPLACE_EXISTING`. Pre-replacement failures delete staging and preserve ours; unsupported atomic moves fail without a destructive fallback. No fallible I/O follows successful replacement.
- A per-call, package-visible Writes boundary injects failures only at part writing, manifest writing and replacement; there are no shared mutable hooks. Tests inject failures after partial writes, after manifest writing, during staged XML validation, during replacement and for unsupported atomic moves. Missing inputs, corrupt ZIP/XML and a real manifest patch failure also preserve ours and clean staging. Four independent targets merge concurrently without sharing temporary files; same-target concurrency remains unsupported.
- Untouched XML entries retain ours' original bytes, including PIs, attribute order and ignored metadata. Untouched binary entries retain their bytes. Without part additions/removals, designmap is not written. With additions/removals, only the relevant idPkg elements change; aid PI and other content survive. Removing a reference supports single quotes and adjacent references on one line without deleting that whole line.
- IdmlPackageIntegritySpec exercises additions, one/both-side deletions, divergent additions at the same src, delete/modify and independent multi-part updates in both branch orders. Successful packages are reopened and compared for complete XML/entry inventory, assets and non-reference manifest content; conflicts preserve ours. Base and theirs remain unchanged.
- ThreeWayCorpusSpec strictly loads the MR03 schema-3 contract: 16 cases × 2 orders = 32 tests, each exercising both package merge and the real driver. Accepted results compare the complete independent oracle inventory, every XML entry, binary bytes, logical candidate and static references. Conflict results preserve ours; 06b/13 additionally compare complete domain reports derived from oracle target/relatedParts. Detailed generic business-conflict mapping still belongs to MR09; status assertions here do not claim that layer's acceptance.
- User-approved scope extension: the MR03+MR11 baseline falsely conflicted on case 04 because anonymous descendants used raw paths and indentation text used shifting ordinals. The IDML entry now matches anonymous nodes beneath corresponding Self anchors, scopes inserted anonymous descendants to their nearest Self ancestor, and anchors character nodes to the following element/end plus consecutive character ordinal. Node kind, exact text and order remain preserved. The anonymous-parent matching helper follows MR08 `a3b7bc6`; no MR04–MR08 branch is merged.
- Core changes only expose the existing RelationSet edit calculation and reconciliation for prematched identities; default XML entry behavior and conflict policy are unchanged. Focused regressions compare complete independent insertions with whitespace, require same-gap insertions to conflict, combine a moved anonymous descendant with an independent property edit, and preserve consecutive text/CDATA during insertion plus text editing. The real Insert/Insert regression now checks the exact whole-node slot rather than counting Self substrings inside valid descendant diagnostics.
- `sbt 'core/test' 'idml/test' 'kml/test'`: core 212 passed (the existing ExtractorsSpec.extractor3B remains ignored); IDML 142 and KML 109 passed with zero ignored/pending. Real Java subprocesses verify CLI 0 for success, 1 for conflict and 2 for argument/read/XML/write failures, including byte preservation.
- `python3 idml/tools/generate_three_way_corpus.py --verify`: 16 cases/schema 3 passed read-only validation. `python3 -m unittest discover -s idml/tools -p test_three_way_corpus.py`: 17 passed. All corpus file SHA-256 hashes and mtimes were unchanged, and all 48 IDML inputs remain byte-identical to MR0311. Oracles were not changed to accommodate the implementation.
- `git diff --check` passed. These are local automated results, not remote CI or completion of other MRs or InDesign open/save/reopen acceptance.


## MR10 生成性质测试冻结记录（2026-11-06）

- 默认固定种子 `0..7`，节点数 `8,32,96`；种子和节点数共同初始化 PRNG。
  共 24 棵合法 base 树，每棵生成 7 个兼容 recipe：独立内容更新、不同位置插入、
  删除与无关更新、移动与同节点更新、双方相同新父、重排与无关更新、双方相同更新。
  每组合另生成原子内容分歧和两个不同新父的冲突。不会假定任意随机编辑都兼容。
- 节点数精确包括根、3 个 Group 和 `n-4` 个带 Self 的叶节点；值长度有界。
  XML 派生 corpus 的参考 base Spread 有 185 个元素及 302 段 text/tail，Story 有
  16 个元素及 29 段 text/tail。这里选择更小且可缩减的树作为性质测试，真实形状、
  无 Self、正文和严格深层 Delete/Edit 另用固定回归覆盖；不是规模性能验收。
- 首轮候选规模试运行使用本机 Java 21、Scala 3.8.4：core 生成 suite 约 2.3s，
  IDML 约 3.8s（ScalaTest suite 时间，不含 sbt 启动/编译）。正式采用上述规模；
  预期预热后新增 suite 各低于 30s，不设脆弱的墙钟断言。这是单元测试预算，
  不替代、提前冻结或放宽 MR13 的性能门禁。
- 每模块每个节点规模的 56 个成功 recipe 共记录 96 项生成操作（双方同编辑按
  ledger 中的一项有效意图计，不把两个分支副本重复计数）。core 单独统计两侧
  `E` 的并集：首轮 8/32/96 节点分别为 1—8、1—12、1—33 条关系编辑。
  一次 move/reorder 可以形成多条 PCS 编辑，操作数与 E 数不等同。
- 四项恒等式为 `M(B,B,B)=B`、`M(B,X,B)=X`、`M(B,B,X)=X`、`M(B,X,X)=X`。
  对每个成功 recipe 的左右输入均执行，并检查成功结果对称性、完整 expected 树、
  可达身份唯一性及操作 ledger 中的删除、插入、父节点、属性和顺序意图。
- 原子 PCS 的 `E⊆Δ⊆T*` 用相同身份下的状态关系集合计算；`T*` 为三份输入关系的并集。
  该原始集合断言使用无忽略字段的生成树。忽略字段按冻结策略单独测试输出保留；
  IDML 属性细化逐属性检查值来源及完整目标树，允许合法的两侧属性组合。
- 已有节点保留 base 的忽略字段；单侧新节点保留来源值；双侧同身份且仅忽略字段
  不同，比较按属性名排序、XML 转义后的完整属性集合的 UTF-8 无符号字节顺序，
  选较小的整组属性。两层均有左右交换对照，不逐字段拼接忽略值。
- 不共享跨模块 Test classpath。IDML 文件内保存同一 recipe 规范的独立薄生成器；
  两者默认规模、种子和操作分布一致，不增加生成框架依赖。
- 重放：`sbt -Dmerge.seed=3 -Dmerge.size=32 'core/testOnly *MergePropertiesSpec'`
  或 `sbt -Dmerge.seed=3 -Dmerge.size=32 'idml/testOnly *ThreeWayMergerPropertiesSpec'`。
  失败报告 recipe、种子、原始规模和三方 XML；自动删除不涉及编辑目标的叶节点，
  保留同一检查失败，生成在该删除规则下局部最小的三方输入。不是通用 XML 最小化器。
- oracle 自检注入丢编辑、偏向左输入、凭空关系/属性的反例，必须被断言拒绝。
  06b/13 的领域门禁、真实 InDesign 和大规模性能证据仍属于 MR11—MR14。


## V1_1_3-Kaining-2 整合记录（2026-11-06）

- 上述 MR12、MR10 证据保留各自原分支历史；本轮从 `V1_1_3-Robin-Kaining-Merged`
  创建整合主干，顺序为 MR08、MR12、MR10，使用保留历史的普通 merge。
- MR12 先 rebase 到含 MR08 的主干；MR10 再 rebase 到含 MR12 的主干。
  MR02/MR03 的等价提交被识别并跳过，不重复应用已合入改动。
- 保留 MR08 的重建校验及混排内容、MR12 的原子写入及匿名后代身份、MR07 的严格
  不可达区域检查。严格删除检查复用与候选合并相同的预匹配关系，防止身份模型不一致。
- `ThreeWayCorpusSpec` 核对全部通用/领域冲突；`ThreeWayPackageCorpusSpec` 保留全部
  包/driver 用例。06b/13 的 oracle 中 tf1.NextTextFrame 改变与 tf2 删除/重连分别
  产生两端的领域报告，适配层逐条核对完整集合，不过滤额外冲突。领域阻塞说明由
  本整合状态取代；InDesign、MR13 性能和 MR14 最终验收仍待完成。
- 整合后的干净构建验证：`sbt 'core/clean' 'idml/clean' 'kml/clean' 'core/test' 'idml/test' 'kml/test'`
  通过 core 432、IDML 398、KML 110 项，共 940 项；core 原有 1 项忽略，无新增忽略或 pending。
  corpus 只读校验 16 组通过；Python 校验测试 17 项通过。


## MR13/MR14 当前执行计划（2026-11-06）

直接在 `V1_1_3-Kaining-2` 执行，先 MR13、后 MR14。性能候选矩阵与校准方法见
[性能计划](xml-merge-performance.md)；正式门禁须在校准后、正式测量前冻结。
真实验收候选清单在核对本地资料后冻结；缺少授权、独立 expected 或 InDesign 记录
必须在 [证据表](xml-merge-test-evidence.md) 明确阻塞，自动测试通过不替代应用验收。
