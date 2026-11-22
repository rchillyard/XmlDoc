# 如何制作真实三方 IDML 样本与 InDesign 验收记录

当前没有真实三方样本及应用记录。本指南是待执行步骤，不是已完成的证据。
四组计划冻结于 2026-11-06；至少完成三组成功、一组冲突，覆盖文字、结构、线程和资产。

## 先制作共同 base，再独立分叉

1. 用自己原创、可提交到仓库的内容制作 2 页小文档：两个 Story、至少两个互相串接的
   TextFrame、一个含两个矩形的 Group、一张自有 PNG。使用同一个 InDesign 完整版本，
   写下版本号、系统、字体、插件；本机检测到 2026/2027 安装目录，但尚未选定验收版本。
2. 解决预检中的缺字体、缺链接、溢出文字等问题；若故意保留某警告，事先记录原因和预期。
   保存 `base.indd`，导出 `base.idml`，关闭文档。只在副本上操作。
3. 从**同一份已关闭的 base.indd** 复制出 left.indd、right.indd。分别打开一个分支，
   只执行下表该侧操作，保存并导出 left.idml/right.idml，然后关闭。右侧必须从 base
   开始，不能从 left 或应用 Undo 后的状态开始；禁止两侧独立重建“看起来一样”的页面。
4. 对三个 IDML 解包，只读记录 part 路径、现存 Self 与父节点/兄弟关系。确认未改变节点
   身份稳定；新对象身份唯一。若导入/导出导致身份整体变化，重新制作，不靠改 Self 迎合测试。
   `base.indd` 与三份 IDML 都保存 SHA-256。macOS 可用 `shasum -a 256 文件名`。
5. 成功组从第三份全新 base.indd 副本**手工应用两侧兼容操作**，保存 expected.indd，
   导出 expected.idml 和 expected.pdf。含新插入对象的第 4 组改为从 left.indd 的全新副本
   手工应用右侧操作，保留左侧新对象的 Self；这仍独立于待测 merger。不能因手工重建的新
   对象获得不同 Self 就忽略身份断言。冲突组则先写
   expected-conflicts.json，记录全部 part/slot/base/left/right，禁止运行后抄实际输出作预期。

Adobe 官方说明：通过保存/导出对话框选择 InDesign Markup (IDML) 保存交换文件；具体
菜单名称以所用版本为准。所需链接资源可通过 File → Package 收集，并核对资源及字体
使用权限。[保存与 IDML](https://helpx.adobe.com/indesign/desktop/save-export-and-publish/save-and-export/save-documents.html)、
[打包文件](https://helpx.adobe.com/indesign/desktop/print/preflight/package-files-for-output.html)。
上述共同 base、分叉隔离、独立 oracle 和哈希流程是本项目的验收要求。

## 四组最小操作清单

| 目录 | 左侧操作 | 右侧操作 | 独立预期 |
|---|---|---|---|
| real-01-text-move | 修改 Story1 第一段正文 | 移动另一个已有矩形，保持身份 | 正文和位置更新都保留；完整包/页面与手工目标一致 |
| real-02-ungroup-style | 解组已有 Group，提升两个子对象 | 修改其中一个子对象 FillColor | 子对象各一次、父节点正确、颜色保留；手工核对页面位置，不推测坐标转换 |
| real-03-thread-delete-edit | 删除 Story1 线程中的第二个 frame，明确修复前后链 | 修改 Story1 的正文 | Story 级依赖冲突；源文件和 ours 哈希不变；不产出成功包 |
| real-04-thread-assets | 在 Story1 线程插入新 frame，更新双向链接 | 仅修改无关 Story2 正文 | 两项更新保留、Story1 双向链合法，自有图片 entry 字节不变 |

图层名称/标签可用于人工识别，但请把**实际 Self、Story 和 src** 写进 provenance。
不要把示例角色名当成真实 XML ID。解组样本必须另外确认视觉结果；XML 成功不足以证明位置正确。

## 提交材料与记录模板

每组目录：

```text
base.indd                 # 可选归档；确认可公开后再提交二进制大文件
base.idml / left.idml / right.idml
expected.idml             # 成功组，独立手工目标
expected-conflicts.json   # 冲突组，使用完整 PartConflict JSON 数组
provenance.json
application.json
evidence/expected.pdf
evidence/open.png
evidence/reopened.png
```

provenance.json 至少包括：作者/来源、明确授权依据、资产与字体来源、InDesign 完整版本、
base 创建/关闭记录、left/right 独立操作步骤、实际角色→Self/src 对照和 oracle 制作方法。
不要上传真实客户资料、未获许可字体或业务数据；优先使用自制最小文档。

application.json 填写 `version`、`reviewer`、`warningsReviewed`、`warnings`、`openPassed`、
`savePassed`、`closePassed`、`reopenPassed`、`visualPassed`、`threadPassed`、`assetsPassed`，
以及 `artifacts` 中的相对路径和 SHA-256。冲突组记录 `conflictReviewed=true`、
`oursUnchanged=true`、base/left/right 的输入哈希和实际冲突 JSON。
manifest 的每组 `artifacts` 同样使用 `{ "相对文件名": "sha256" }`。
将 provenance、application 和独立 oracle 都列进 artifacts；把 authorization.status
改为 `authorized` 必须同时填 `basis`，不能只改状态。模板字段缺失的严格门禁会失败。

## 实际运行与应用复验

1. 在临时目录复制 left.idml 为 merged.idml。可用
   `sbt 'idml/runMain com.phasmidsoftware.xmldoc.idml.IdmlMergeDriver base.idml merged.idml right.idml'`。
   命令中用真实完整路径；退出 0 为成功，1 为冲突，2 为输入/读写错误。
2. 对成功组比较完整目标 XML、part/entry 清单、引用、图片哈希和 designmap 的允许差异。
   ZIP 字节与压缩元数据无需与 expected.idml 相同；正文空白和子节点顺序不能忽略。
3. 在所选 InDesign 版本打开 merged.idml，逐页与**独立 expected.pdf** 对照：正文、
   字体、位置、叠放、解组对象数量、图片、溢出文字与 frame 串接。记录全部警告。
4. 保存成 merged.indd，关闭，再打开 merged.indd；重新逐项核对并截图。必要时再导出
   IDML 检查身份和引用。只记录亲自完成的步骤，不预填 true。
5. 交换 left/right，使用新的临时输出重复完整自动检查；成功结果按冻结语义相同。
   对冲突组比较所有冲突字段且确认 merged.idml 仍为调用前 left 副本，不能手工修复后声称自动成功。
6. `python3 idml/tools/verify_real_world_manifest.py` 严格校验；再运行
   `sbt 'idml/testOnly *IdmlMergeAcceptanceSpec'`。未完成任何组时发布门禁应明确失败。

该指南准备好了制作方法；当前四组均尚未制作，MR14 的真实应用验收仍被阻塞。
