# IDML XML Merge Test Specification

状态：MR01 规格已记录；后续实现与验收尚未完成。记录日期：2026-11-06。

本规格是后续 MR 的预期来源，不是当前实现通过证明。执行顺序以 [MR 拆分计划](xml-merge-test-mr-breakdown.md) 为基础；下文冻结决策替代计划中的相应待决策条目。MR01 只修改文档，不改算法、样本 XML、expected.json 或 driver。

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



## 冻结决策

以下选择自 2026-11-06 生效，后续修改须同步规格、样本 profile 和对应测试，不能只改 expected 使当前实现通过。

| 决策项 | 结论 | 日期 | 原因与适用入口 | 责任 MR |
|---|---|---|---|---|
| 预期来源 | 分为论文规则、项目细化、IDML 领域、工程契约；每条用例分别标注 | 2026-11-06 | 通用树模型不能证明领域语义 | 03、09、14 |
| 文本模式 | 本轮唯一验收 profile 为 `strict-text-node-v1`；文本节点内容原子比较，不开发 token merge | 2026-11-06 | 论文 §2.3、§6.3；当前入口没有 token 协调能力 | 03、08、09 |
| 08b | 同一文本节点的不同双边修改必须冲突，即便单词范围互不重叠 | 2026-11-06 | 以文本节点为原子单位，不能同时接受冲突或成功 | 03、08、09 |
| Delete/Edit | IDML 公共入口严格检查完整被删子树；后代内容或结构编辑不可静默丢失 | 2026-11-06 | §6.3 optional 检查由项目明确启用；底层关系森林不等于最终可达树 | 07、10 |
| 结构与属性 | 不启用推测合并；同一节点不同属性的独立修改仍可合并 | 2026-11-06 | PCS 按关系一致性判断；属性细化沿用 ThreeWayMerger 契约 | 06、07 |
| 领域冲突 | 使用下文 story 级保守依赖规则；06b、13 为要求支持的冲突 | 2026-11-06 | 不依赖不可得的字符排版流映射 | 11、12 |
| 身份与输入 | 各 part 内重复 Self、包级 frame/Story 索引重复身份显式失败；不同身份类别不盲目建立全包通用唯一性约束 | 2026-11-06 | TreeMatcher.matchBySelf 当前假定唯一，不应静默覆盖 | 04、11 |
| 忽略字段 | 仅 LinkImportTime、StyleUniqueId；具体比较与保留规则见下文 | 2026-11-06 | 与 EditDetector.defaultIgnoredAttributes 一致 | 02、03、08、10 |
| 写入 | 冲突和函数返回 I/O 失败均保持 ours 原字节；独立目标可并行，同目标并发不支持 | 2026-11-06 | 当前顺序写包不足以保证失败原子性；目标待 MR12 实现 | 12 |
| 外部接口 | 保留现有 merge 方法签名和 CLI 退出码 | 2026-11-06 | 不改变 Git driver 接入方式 | 11、12 |

### 领域依赖规则

1. 从 base 的 TextFrame 身份、ParentStory、NextTextFrame、PreviousTextFrame 建立 frame→Story 与线程关系；允许的空引用哨兵须由 MR11 根据真实样本列举，不把空值当悬挂引用。
2. 一侧删除 base frame，或修改其 ParentStory/线程拓扑，构成 Story 布局依赖变化。受影响集合包含变化 frame 的 base Story、修改后 Story，以及变化边两端 frame 在 base/该分支所属的 Story；空值不计入。
3. 另一侧相对 base 对该集合内 Story 的正文树做真实编辑，报告领域冲突。正文树指 Story 的文本、段落/字符样式范围及其内容结构；仅忽略字段变化不算真实编辑。正文比较的完整范围与路径由 MR11 固化为共享测试样本，不以排版后具体字符落点判断。
4. 左右对称执行。06b 为删除 tf2 与修改 story1；13 为 tf2 从 story1 切到 story2 并脱链，与修改 story1。两者均须冲突。
5. 仅单侧变化、仅无关 Story 编辑、仅忽略字段变化不触发本规则；仍须通过其他结构及引用合法性检查。
6. 本规则只对“布局依赖变化 + 另一侧相关正文编辑”保守报冲突，不意味着任意两个 frame 操作都冲突。合法插入 tfX 且另一侧不变可成功；12 的同属性分歧独立报告。
7. 候选包静态校验与并发意图冲突是两类检查。缺目标、非互反链接、非法环不能通过；即使引用合法也可能命中上述领域冲突。

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
| 七：已有用例 | 03、09、11—12 | 全部 15 组发现与适用层级执行 |
| 八：端到端 | 11—12、14 | 引用、原子写入、InDesign 验收 |
| 九：规模与顺序 | 13—14 | 分段性能、输入规模与最终证据 |

五层分别归属：匹配 MR04；PCS/检测 MR05；协调 MR06—07；重建 MR08；整包 MR11—12；MR09/14 贯通入口。

| 原有案例 | 唯一目标预期 | 来源 | 交付 MR |
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
| 10-group-ungroup | conflict；根据实际关系证明 | §5 唯一 parent/前驱/后继 | 03、07、09 |
| 11-thread-insert | accept；双向引用一致 | 规则 5 + IDML | 03、11、12 |
| 12-next-divergent | conflict | 同属性分歧 + IDML | 03、11、12 |
| 13-parentstory-vs-content | conflict | story 级领域依赖规则 | 03、11、12 |

## 数量、预算与后续冻结项

| 项目 | 数值或待决策项 | 单位与计算 | 依据 | 冻结时间点 |
|---|---|---|---|---|
| 原有 corpus | 15 | 独立场景目录 | 当前清单 | 2026-11-06 |
| 分支排列 | 2 | 正常与交换各一次/组 | §6.4 | 2026-11-06 |
| 生成规模和种子数量 | 待决策，不填猜测值 | 实际节点、检测编辑、独立种子；与生成操作数分列 | MR10 前测量 CI 时间和样本分布 | MR10 实施前，由 MR10 写入本表 |
| 基准规模、预热和重复数 | 待决策 | 节点、编辑、次数 | 固定环境试运行与资源预算 | MR13 正式测量前，由 MR13 写入 |
| 性能阈值 | 待决策 | ms、MiB、相对基线倍数；分段统计 | 先采基线，再冻结门禁，不按失败结果抬高 | MR13 正式测量前 |
| 真实验收数量与样本清单 | 待决策 | 独立 base/left/right 组 | 授权样本可用性；必须覆盖文字、结构、引用、资产 | MR14 实施前，由 MR14 写入 |

以上是后续测量型决策，不阻塞文档 MR01；逾期未冻结则阻塞对应实施或可交付声明。未定义新的生产输入数量上限，不将测试生成规模误当生产限制。

## 当前缺口与验收规则

- 08b 的现有 schemaVersion 1 expected.json 是历史 token-aware 预期；MR03 必须迁移为 strict-text-node-v1 的 conflict 并记录版本及原因。MR01 不改 JSON，也不把它当当前 profile 可执行结果。
- 06b、13 的冲突目标保持不变，但跨 part 意图检查尚待 MR11/12 验证和实现。
- 无 Self 匹配进入 ThreeWayMerger 的正确性、元数据处理、严格深层 Delete/Edit 均是待验证假设。
- driver 当前直接逐项写 ours，失败原子性是 MR12 目标，尚未得到保证。
- 后续每 MR 合并后须可构建、相关测试通过；修复与回归同 MR 交付，不允许为了主干绿灯静默跳过要求支持的用例。

## Launch blockers

- 到期策略、性能或样本决策未冻结。
- 任一要求支持的场景缺证据、只执行一侧顺序、误把生成器校验当合并验收。
- 无 Self、guards、深层 Delete/Edit、正文空白或完整结果比较失败。
- 06b/13 未按领域规则执行，引用、part/designmap 或资产保留失败。
- 冲突或 I/O 失败改变 ours，或修改 base/theirs/原样本。
- core/test、idml/test、适用 KML 回归、只读 corpus 校验、冻结性能门禁或真实 InDesign 打开/保存/重开验收未通过。
- 验收证据不是最终提交结果。MR01 文档验证不替代 MR14 交付验证。

## Explicitly deferred

- token-aware 文本合并、推测结构合并、n-way、copy/glue 特殊语义。
- 完整 infoset 与命名空间标准化重写、完整 designmap 合并、排版引擎。
- 同一目标并发写入、断电持久性与无关 KML/基础框架重构。
- 部署、数据库迁移、历史回填；本轮没有这些步骤。
