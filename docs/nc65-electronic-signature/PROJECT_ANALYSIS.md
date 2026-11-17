# 工程分析

分析日期：2026-11-07。

## 根目录、版本控制与现状

- 开发根目录：`E:\02-Project\QiDu\QD-E-Siganature`；平台依赖目录：`E:\01-Home\NC65home\QuDuTest\nchome`，后者只读，不把产品目录当开发输出目录。
- 首次检查时开发目录没有 Git 仓库，只有 uapbd 骨架和 `Application5975` 示例。用户随后建立 Git `main` 分支及六模块目录，当前尚无提交；现有文件均应保留，不以“未跟踪”判断可以覆盖。
- `.iml` 现有 baseapp、uapbd、so、pu、purp、ic 六模块的 client/private/public 共 18 个源码根；各 `component.xml` 为插件骨架。
- 当前项目及已检查的祖先目录未发现 `AGENTS.md`。后续若出现新规则文件，编辑前重新检查适用范围。
- NC Home 内有独立 `.git`，其 HEAD 指向 `main`；初检曾遇 `Permission denied`，主任务后续已只读取得状态：main尚无提交、现有文件全部未跟踪。该项不再阻塞；Home仍保持只读，未跟踪不表示可覆盖。

## 实际技术栈

| 项目 | 证据 | 结论 |
|---|---|---|
| 工程 JDK | `.idea/misc.xml:3` | JDK 1.7 / ibm-1.7 |
| NC Home | `.idea/NCHomeConfigSetting.xml:4` | 指向 QuDuTest/nchome |
| 真实可用编译器 | `NC_HOME/ufjdk/bin/java.exe -version`、`javac.exe -version` | IBM J9 Java 1.7.0，可使用，不升级核心依赖 |
| PATH Java | `java -version` | Oracle Java 1.8.0_131；构建必须显式选 NC 的 JDK7 |
| 构建 | 开发根目录初始无 Maven/Gradle/Ant 配置 | 需新增隔离构建脚本；NC 自带 buildnc.xml 是代理/产品构建，不能直接当本项目安全构建入口 |
| 基线验证 | 用 IBM javac 编译 Application5975 到临时目录，并运行 | 实际输出 `Hello world!`；仅基础示例通过 |

## 多模块职责

| 模块 | 本项目职责 |
|---|---|
| uapbd | 公共 DTO/接口、策略、请求、签名/审计核心、数据层、公共窗口/查询 |
| baseapp | 真实平台认证与锁定适配、安全/会话事件 |
| so | 销售单据 PF 与销售关闭管理适配 |
| pu | 采购单据 PF、数电发票特殊适配 |
| purp | 供应商价目表批量操作适配 |
| ic | 普通出入库、特殊库存、复合出入库动作适配 |

这些职责是开发分工；目录存在不代表功能已适配。客户既有相邻工程与 Home 的覆盖类仅作为调用链证据，不直接拷贝其产品类覆盖方式。

## 已发现边界

- 产品按钮可使用真实 `ActionInterceptorInfo` 注入拦截器，不需要替换产品动作类；具体节点仍需分别绑定。
- NCAction 成功回调不能证明业务提交成功；权限失败、弹窗取消也可能正常 return。审计必须在服务端真实业务事务与结果处处理。
- 任意 `InvocationInfoProxy.setProperty` 是线程属性，不保证跨 RPC；PF 的 `userObj` 已被产品占用，不能覆盖。
- 物料、采购、销售和库存的保存/批量/删除机制不同，必须有适配层，不能以单个拦截类宣称统一覆盖。
- Home 存在 `qd-esign-v1.properties`、`qd-esign-probe.properties` 和专有密钥文件痕迹；只确认文件存在，不代表功能可用。不读取/复用/覆盖密钥。
- QDNC后续恢复连接并按用户确认完成24表安装与243条关闭目录，取得节点/按钮、原插件/监听器等证据；真实NC事务/业务尚未验证。既有V1节点与10张旧表保持原样，见SITE_VALIDATION和实际执行记录。

详细类、方法、源码/字节码证据与可信度见 `SOURCE_MAP.md`。
