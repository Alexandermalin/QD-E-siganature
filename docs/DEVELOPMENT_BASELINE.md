# NC65 电子签名开发基线

2026-11-17 的测试运行基线取自本机 QiDu-Test NC Home。两份部署 JAR 的哈希与请购提交及物料批改合并包一致。完整的电子签名构件清单位于 `config/runtime-baseline.json`，对应原始构件备份位于原始开发仓库同级目录的 `qd-esign-runtime-baseline-20261117.zip`。

主工程源码取自通过 IBM JDK 7 全量离线验证的物料批改构建。其中的 800 个 Java 文件逐项与该次构建记录核对。补充路径 `overlays/procurement` 保存采购试点各阶段源码，并带入已部署的请购提交修复。路径 `overlays/inventory` 保存现行的库存审批适配源码，重新编译后，其方法签名和字节码指令与已部署类一致。请购提交的适配类也通过了同样的核对。

现场还包含其他历次增量构件。当前仓库适用于以冻结运行构件为底包的增量开发。全量替换某个部署 JAR 前，需要补齐该 JAR 中的现行源码、配置及入口，并校验构建产物的完整类清单。

## 开工

每个 Codex 会话选择本仓库作为项目。创建会话时选择 **Worktree**，以 `qd-test-20261117` 对应的提交或已发布的后续基线为起点。在工作树里创建该需求的分支，例如在工作树里运行 `git switch -c codex/material-batch`。

校验各工作树引用的同一份 NC Home。在开发时用它提供 NC65 JDK 和依赖，构建输出位于各工作树自己的 `build` 目录。

```powershell
./tools/Test-RuntimeBaseline.ps1 -NcHome 'E:/01-Home/NC65home/QiDu-Test/nchome'
.	oolsuild.ps1 -NcHome 'E:\01-Home\NC65home\QiDu-Test\nchome' -Test
```

改动小且已有成功的构建记录时，可以先运行 `tools/test-targeted.ps1`。交付会话在 [发布登记](RELEASE_LEDGER.md) 记录基线标签、提交 SHA、涉及的模块或类以及测试结果。

## 集成与发布

集成会话在 `release/nc65-test` 汇总本轮提交。遇到同一类或配置的改动，先合并源码，再统一编译并跑两边的回归。增量包以对应部署基线的 JAR 为底包，核对改动类及全部其余条目的摘要，写明接受的安装前版本并附回退文件。

将测试和部署后的新基线登记在发布记录里，下一轮会话统一选用该基线。安装和真实业务联调由集成会话协调。
