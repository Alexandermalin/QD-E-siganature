# 实际源码技术地图

本批修复源码证据：`E:/02-Project/QiDu/QD-E-Siganature/uapbd/src/private/nc/bs/uapbd/qd/esign/persistence/QdEsignStore.java:120`从实际对象补日志身份，`:131`结果查询回退历史，`:140`恢复成功对象；同目录`CompletedRequestReader.java:14`只读终态恢复与对象证据校验；`E:/02-Project/QiDu/QD-E-Siganature/uapbd/src/private/nc/impl/uapbd/qd/esign/SignatureServiceImpl.java:105`业务执行入口仅锁在线请求，历史SUCCESS仅重放。29项新增离线回归及239项完整断言通过，未视为NC运行证据。

现场补充证据位于本目录`site-evidence/2026-11-07/`，每份JSON含实际SELECT及原始结果，详见SITE_VALIDATION。`native_alias_types.json`和`all_node_ui_params.json`共同确认投产单/委外加工入库的独立节点、类型和XML；`buttons_0/1.json`保存1385条按钮。证据仅证明注册/结构，不证明最终RPC或事务。

取证日期：2026-11-07。`NC_HOME = E:\01-Home\NC65home\QuDuTest\nchome`；表内以此缩写开头的路径为该绝对目录。JAR内路径用 `!/` 表示。可信度“高”仅表示本地源码或字节码支持结论，均不等于NC运行已验证。未找到源码时明确使用javap，不伪造源码行。

## 客户端与上下文

| 证据路径/位置 | 类、方法与调用 | 结论/可信度 |
|---|---|---|
| 开发目录 `.idea/misc.xml:3`、`.idea/NCHomeConfigSetting.xml:4` | ProjectRootManager / NCHomeConfig | ibm-1.7及真实Home路径；高 |
| NC_HOME/modules/so/client/classes/nc/ui/config/pubapp_ext_config.xml:47–55、59–68 | ActionInterceptorInfo，target=approveAction/DeleteAction | 现有无侵入插件配置示例；只能新增本项目绑定，不能覆盖既有文件；高 |
| NC_HOME/modules/so/client/classes/nc/ui/extend/pubapp/action/ApproveActionInterceptor.java:27、39、47、104 | implements ActionInterceptor，beforeDoAction / afterDoActionSuccessed / afterDoActionFailed | 三回调存在；示例失败返回false不能照搬；高 |
| NC_HOME/modules/riart/client/lib/uiriart_riaUIFactoryIILevel-1.jar | nc.ui.uif2.actions.ActionInterceptor；nc.ui.uif2.NCAction.actionPerformed，javap偏移37–143 | before=false跳过业务；afterFailed=false跳过原异常处理；新拦截必须保留原处理；高 |
| NC_HOME/modules/riacc/client/lib/uiriacc_riapubappfuncLevel-1.jar | ActionInterceptorInfo / ActionInterceptorPluginMediator.addInterceptor | 扫描Spring中的Info并组合已有拦截器；不需要覆盖原拦截器；高 |
| 同上/相关UI JAR，本地javap | CompositeActionInterceptor.beforeDoAction / afterDoActionSuccessed / afterDoActionFailed | before逆序短路，after正序；原拦截器可使后续回调不发生；不能只靠客户端清理；高 |
| NC_HOME/modules/uapbd/client/classes/nc/ui/bd/pub/actions/AnsyMessageDialog.java:37、95–111 | extends UIDialog，setModal，UIPanel/UILabel/UIButton/UITextArea | NC原生弹窗与组件模式；高 |
| NC_HOME/modules/ic/client/classes/nc/ui/ic/qdyy/lims/ReLimsDialog.java:103、251–285 | UIButton/ActionListener，closeOK/closeCancel | 确认取消交互模式；高 |
| NC_HOME/modules/ic/client/classes/nc/ui/ic/general/action/GeneralSaveAction.java:285–297 | fillUpContext，ICPFParameter→PFlowContext.setUserObj→super.fillUpContext | userObj已有产品用途，不得替换；高 |
| NC_HOME/modules/pubapp/client/lib/uipubapp_platformextLevel-1.jar，javap | AbstractScriptExcAction.doAction→clear；PFlowContext.clear；PFlowProxy.runAction/runActionBatch | eParam会先清空，再被复制给PF；仅before写eParam不能证明传播有效；高 |
| 同上，javap | ScriptPFlowAction.doAction→checkDataPermission | 无权限可显示错误后正常return；客户端成功回调不能证明已提交；高 |
| NC_HOME/external/lib/fwpub.jar，javap原始行331–348 | InvocationInfoProxy.setProperty/getProperty | invProperties为ThreadLocal，任意属性不保证序列化跨RPC；高 |

## 服务、认证、事务、事件

| 证据路径/位置 | 类、方法与调用 | 结论/可信度 |
|---|---|---|
| NC_HOME/external/lib/fwpub.jar，javap | NCLocator.getInstance().lookup(Class)；InvocationInfoProxy.getUserId/getUserCode/getGroupId/getUserDataSource | 真实服务发现与服务端身份API；高 |
| NC_HOME/modules/uapfw/lib/pubuapfw_jdbcframeworkLevel-1.jar，javap | BaseDAO，SQLParameter | 真实参数化数据库访问；表字段需白名单；高 |
| NC_HOME/modules/baseapp/lib/pubbaseapp_appbusieventLevel-1.jar，javap | IBusinessListener.doAction(IBusinessEvent)，EventDispatcher.fireEvent/fireEventSync | 通用事件机制存在；不证明每节点都会触发；高 |
| 同上，javap | IBusinessEvent.getSourceID/getEventType/getUserObject，IEventType | 新增/修改/删除/审批/弃审批/锁定事件常量存在；实际注册与覆盖待验证；高 |
| NC_HOME/modules/baseapp/lib/pubbaseapp_prealertbaseLevel-1.jar，javap | IBackgroundWorkPlugin.executeTask(BgWorkingContext):PreAlertObject throws BusinessException | 真实后台任务契约；高 |
| NC_HOME/modules/baseapp/META-INF/S_baseapp_applogin.upm | remote/singleton/tx注册 | CMT/NONE等真实服务注册示例；高 |
| NC_HOME/external/lib/fwpub.jar，javap | TxAttribute | 仅CMT/BMT/NONE，无证据支持REQUIRES_NEW属性；高 |
| NC_HOME/modules/uapfw/lib/pubuapfw_jdbcframeworkLevel-1.jar，javap | JdbcTransaction.startTransaction/commitTransaction/rollbackTransaction | 捕获DbException仅Logger，不能以正常return判独立日志可靠提交；高 |
| NC_HOME/modules/baseapp/lib/pubbaseapp_apploginLevel-1.jar，javap原始行13–21 | StaticPWDVerifier.verify(AuthenSubject,UserVO)→RbacUserPwdUtil.checkUserPassword | 密码校验返回1/3/2，但自身不处理计数锁定；高 |
| NC_HOME/modules/baseapp/lib/pubbaseapp_apprbacLevel-1.jar；META-INF/lib/baseapp_apploginLevel-1.jar | IIdentitiVerifyService.identityVerify；IdentityVerifyImpl | 真正认证服务存在，必须以服务端当前用户使用；具体锁定闭环待NC验证；高 |
| applogin相关JAR，javap原始行132–183、458–479 | LoginVerifyBean.identityVerify / identifyUserIsAutoLock | 受保护令牌/SSO短路及锁定逻辑存在；二次密码确认不能接受客户端附加令牌绕过；高 |
| applogin相关JAR，javap原始行94–164、363–400 | INCLoginService.verify→LoginEngine.verify；dealWithLoginFailure/getMaxFailure | verify构造NCSession，失败计数在login流程；不能重新登录来替代二次确认；高 |

## 业务实际调用证据

以下相邻源码仅作为调用证据，不属于本项目可覆盖目标。

| 绝对路径/位置 | 类、方法及关键关系 | 结论 |
|---|---|---|
| E:/02-Project/QiDu/QdyyNc2Lepu/so/src/private/nc/bs/so/m30/maintain/InsertSaleOrderBP.java:49–63、95 | AroundProcesser.before→BillInsert.insert→after；AddNewBillCodeRule | 新增正式编码取持久化结果，不在签名前假定已生成 |
| E:/02-Project/QiDu/QiDu-NC2DMS/so/src/private/nc/impl/so/m30/action/main/ApproveSaleOrderAction.java:35–69 | AroundProcesser→script.procActionFlow→script.getVos→状态更新→after | 审批要在真实业务处理处采集 |
| E:/02-Project/QiDu/QiDu-NC2DMS/pu/src/private/nc/impl/pu/m21/action/OrderApproveAction.java:22–52 | PfParameterUtil旧/完整VO→procFlowBacth→BillUpdate.update→after | 采购/销售调用形态不同，需适配 |
| E:/02-Project/QiDu/QdyyNc2Lepu/ic/src/private/nc/bs/ic/m4a/delete/DeleteBP.java:18–30 | DeleteBPTemplate(BPPlugInPoint.DeleteBP,this).delete(vos) | 删除前规则入口候选，具体注册/事务需验证 |
| E:/02-Project/QiDu/QdyyNc2Lepu/uapbd/src/private/nc/bs/uapbd/listener/lepu/material/SyncLepuMaterialListener.java:23–64 | IBusinessListener.doAction→BDCommonEvent.getNewObjs/getObjs | 档案事件获取对象模式；未证明全节点覆盖 |
| E:/02-Project/QiDu/QdyyNc2Lepu/META-INF/nc2lepuUpm.upm:4–6 | remote=true、tx=CMT接口/实现注册 | 二开服务注册参考，不复制原服务 |

## 正式操作清单对应的源码候选

| 对象 | NC_HOME下真实配置/类 |
|---|---|
| 客户、供应商、物料 | modules/uapbd/client/lib/uiuapbd_customer.jar!/nc/ui/bd/cust/config/Customer_Config_Base.xml；uiuapbd_supplier.jar!/nc/ui/bd/supplier/config/supplier_config_base.xml；uiuapbd_material.jar!/nc/ui/bd/material/config/Material_Config_Base.xml |
| 仓库、货位 | uiuapbd_stordoc.jar!/nc/ui/bd/stordoc/stordoc_config_org.xml；uiuapbd_rack.jar!/nc/ui/bd/rack/rack_config.xml |
| 需求申请、请购、采购订单/发票 | modules/pu/client/lib/uipu_storereq.jar!/nc/ui/pu/m422x/config/storereq_action.xml；uipu_buyingreq.jar!/nc/ui/pu/m20/config/praybill_action.xml；uipu_puorder.jar!/nc/ui/pu/m21/config/order_action.xml；uipu_puinvoice.jar!/nc/ui/pu/m25/config/poInvoice_action.xml |
| 供应商价目表 | modules/purp/client/lib/uipurp_supplierprice.jar!/nc/ui/pp/supplierprice/config/supplierprice_action.xml：BatchSupplierPriceSaveAction/BatchDelAction |
| 销售关闭管理 | modules/so/client/lib/uiso_salesorder.jar!/nc/ui/so/m30/closemanage/model/m30close_config.xml：十二种M30Close动作 |
| 盘点、转库、形态转换、状态调整 | modules/ic/client/lib/uiic_invcount.jar!/nc/ui/ic/m4r/Invcount_org.xml；uiic_whstrans.jar!/nc/ui/ic/m4k/WhsTrans_org.xml；uiic_invadjust.jar!/nc/ui/ic/m4n/transform_org.xml、nc/ui/ic/m4460/stateadjust_org.xml |
| 关联发票最终确认 | modules/pu/client/classes/nc/ui/pu/m25/dialog/RelevanceInvoiceDialog.java:169–176：ActionListener→closeOK/closeCancel，不是NCAction |

JAR配置中的bean id、billType和permissioncode不能自动当成实际NC功能/按钮编码。上述候选仅证明扩展形态，未完成平台注册、上下文、事务和实际运行验证前不得启用。

## 采购、库存与价目表的精确服务契约

下列均为本机JDK7的javap实际输出摘要，异常均为 `nc.vo.pub.BusinessException`。类型仅在本节为阅读简写，完整包由表首列限定；不能据接口存在认定绕过原PF动作是安全的。

| 类与JAR | 已核验签名/字段 |
|---|---|
| nc.itf.pu.m21.IOrderMaintain，modules/pu/lib/pubpu_puorder.jar | OrderVO[] insert/save/update(OrderVO[],OrderContext)；void delete/deleteByFullVOs(OrderVO[],OrderContext[])；freezeOrder(OrderVO[],String)和unfreezeOrder(OrderVO[])返回OrderVO[] |
| nc.itf.pu.m21.IOrderApprove，同上 | OrderVO[] approve/sendapprove/unapprove/unSendapprove(OrderVO[],nc.bs.pub.compiler.AbstractCompiler2)；compiler有真实用途，不能擅自传null替代原PF流程 |
| nc.itf.pu.m21.IOrderQuery / IOrderClose，同上 | queryOrderVOsByIds(String[],UFBoolean):OrderVO[]；close/open(OrderVO[],int,boolean):OrderVO[] |
| nc.vo.pu.m21.entity.OrderVO，同上 | getHVO():OrderHeaderVO、getBVO():OrderItemVO[]、getPaymentVO():OrderPaymentVO[]；付款计划不能遗漏 |
| OrderHeaderVO / OrderItemVO，同上 | 表头pk_order、vbillcode、forderstatus、pk_org/group/supplier、ts；表体pk_order_b、pk_order、pk_material、nnum、norigmny、ts。状态不是fbillstatus |
| nc.itf.ic.m45.self.IPurchaseInMaintain，modules/ic/lib/pubic_inbound.jar | PurchaseInVO[] insert/sign/cancelSign(PurchaseInVO[])；update(PurchaseInVO[]新值,PurchaseInVO[]旧值):PurchaseInVO[]；void delete(PurchaseInVO[]) |
| nc.itf.ic.m4c.self.ISaleOutMaintain，modules/ic/lib/pubic_outbound.jar | SaleOutVO[] insert/sign/cancelSign(SaleOutVO[])；update(SaleOutVO[]新值,SaleOutVO[]旧值):SaleOutVO[]；void delete(SaleOutVO[])；另sign(SaleOutVO[],boolean)重载 |
| nc.impl.ic.m45.PurchaseInMaintainImpl.update，META-INF/lib/ic_inbound.jar原始行166–173；nc.impl.ic.m4c.SaleOutMaintainImpl.update，META-INF/lib/ic_outbound.jar原始行82–86 | LocalVariableTable证实参数1为billVOs新值、参数2为originBills/originBillVOs旧值，转调各自UpdateAction.update |
| nc.vo.ic.general.define.ICBillVO，modules/ic/lib/pubic_pub.jar | extends AbstractBill；getHead/getParentVO():ICBillHeadVO，getBodys/getChildrenVO():ICBillBodyVO[]，getLocationVOs():ICLocationVO[]，isHasLoadedLocations() |
| ICBillHeadVO / ICBillBodyVO，同上 | 表头cgeneralhid、vbillcode、fbillflag、pk_org/group、cwarehouseid、ts；表体cgeneralbid、cgeneralhid、cmaterialoid/vid、nnum、nassistnum、cstateid、ts |
| nc.itf.ic.m45.self.IPurchaseInQuery / nc.itf.ic.m4c.self.ISaleOutQuery | 按String[]的query/querySaleOut返回String，不是VO[]；不能误转型 |
| nc.itf.pubapp.pub.smart.IBillQueryService，modules/pubapp/lib/pubpubapp_platformextLevel-1.jar | <E extends AbstractBill> E querySingleBillByPk(Class<E>,String)；AbstractBill[] queryAbstractBillsByPks(Class<?>,String[])。是否装载完整货位等明细仍须验证 |
| nc.itf.pp.supplierprice.ISupplierPriceService，modules/purp/lib/pubpurp_supplierprice.jar | extends ISmartService；BatchOperateVO batchSave(BatchOperateVO)；Object[] selectSupplierPriceByWhereSql(IQueryScheme)。没有独立delete方法 |
| nc.vo.bd.meta.BatchOperateVO，modules/baseapp/lib/pubbaseapp_appmetaLevel-1.jar | get/setAddObjs、UpdObjs、DelObjs(Object[])及各Indexs(int[])；删除通过batchSave中的delObjs，不虚构deleteAPI |
| nc.vo.pp.supplierprice.entity.SupplierPriceVO，pubpurp_supplierprice.jar | extends SuperVO；pk_supplierprice、pk_org/group、pk_supplier/material、norigprice、norigtaxprice、nnum、ts；未发现vbillcode，不假造单号 |

原服务注册分别为 `modules/pu/META-INF/SCM_PU_puorder_EJB60.upm:6–20`、`modules/ic/META-INF/SCM_IC_INBOUND_EJB60.upm:37–44`、`SCM_IC_OUTBOUND_EJB60.upm:42–49`、`modules/purp/META-INF/SCM_PP_supplierprice_EJB60.upm:5–8`，均明确 `tx=CMT`。真实实现类包括OrderMaintainImpl、OrderAppoveImpl（原名如此）、PurchaseInMaintainImpl、SaleOutMaintainImpl、SupplierPriceMaintainImpl。新包装调用是否正确参与原事务仍须故障回滚实测，不因UPM属性存在就宣称已验证。

## 权限语义补充：必须按同一授权主体计算

以下均为本机baseapp真实JAR的javap核验，未执行NC RBAC服务。

| 位置 | 真实方法及结论 |
|---|---|
| modules/baseapp/lib/pubbaseapp_apprbacLevel-1.jar：FuncSubInfo | getButtonPermissionOrgs(String)仅返回null，isNeedButtonPower()恒true，不能据方法存在构建按钮组织授权；isValidButton按原生语义允许NOREGISTERD或HASPERMISSION |
| 同JAR：FunctionPermProfileImpl.getFuncSubInfo | 真实new FuncSubInfo，非覆盖stub的子类；触发initNodePermInfoWhenFetchFuncSubInfo原生lazy detail加载 |
| 同JAR：FunctionPermProfileImpl.getSubjectPermInfos，原始行45 | 公开返回SubjectPermInfo[]；虽然IFunctionPermProfile接口未声明此getter，实现类确实公开，不能误判需要反射 |
| 同JAR：SubjectPermInfo.getPkorgs，原始行110–122 | 仅合并该subject的直接pkorgs与动态规则pkOrgsByRule，形成该subject的组织集合 |
| 同JAR：FunctionPermProfileImpl.updateFuncNodeDetailPermInfo，原始行216–228 | 按subjectid挂接该subject的FuncNodeDetailPermInfo，确认该subject确有对应FuncNodeInfo |
| modules/baseapp/META-INF/lib/baseapp_apprbacLevel-1.jar：AbstractFunctionPermProfoleGenerator.createProfile | new FunctionPermProfileImpl并设ownerUserID/ownerUserCode/pk_group/subjectPermInfo，支持服务端检查profile身份与集团 |
| 同JAR：FunctionPermissionPubServiceImpl.getFunctionPermProfile的两/三String重载 | 参数为cuserid/version/(ruleVersion)，不是按org筛选profile的API；不得把orgId当version传入 |
| 同JAR：FunctionPermissionPubServiceImpl.getUserPermissionPkOrgs | 仅功能级组织并集，不能与全局按钮状态拼成按钮+BU授权，否则跨角色串权 |

本项目EsignPermission先取当前身份/集团的真实完整profile，初始化节点明细，再逐subject新建隔离FuncSubInfo：同subject通过按钮检查后才取其getFuncPermissionPkorgs（保留原组织类型过滤），最后合并已完整授权的组织。管理/日志按钮严格HAS，原业务保留原生未注册按钮语义；未知profile实现failclosed。LogAccess复用同一helper，不直接使用null stub。

PermissionRulesTests离线16断言覆盖角色交叉授权、拒绝、规则组织、原业务无按钮管控及严格管理入口；其orgType为空的VO fixture不执行真实组织查询。实际profile服务、用户角色、orgType、动态业务活动组合均仍需NC环境验证。

## 价目表批处理结果专项复核

- `modules/baseapp/lib/pubbaseapp_appmetaLevel-1.jar` 的BatchOperateVO经javap -p -c -l完整核对：只有addObjs/updObjs/delObjs及addIndexs/updIndexs/delIndexs，无errors、逐对象失败标记或失败索引；getObjectId2IndexMap原始行58–82是identityHashCode到原UI行号的映射，不是成功/失败码。
- `modules/purp/META-INF/lib/purp_supplierprice.jar` 的SupplierPriceBP.batchSave原始行50–53：beforeProcess→SmartServiceImpl.batchSave→afterProcess；afterProcess原始行67–75为空操作，无逐对象异常捕获。
- `modules/pubapp/lib/pubpubapp_platformextLevel-1.jar` 的SmartServiceImpl.batchSave原始行29–33委托BatchSaveAction，Exception走ExceptionUtils.marsh；BatchSaveAction.batchSave原始行52–86无逐对象catch，删除保留原delObjs，更新和新增将VOUpdate/VOInsert返回对象写回对应数组。
- 同JAR的VODelete.delete原始行25–33、VOUpdate.update原始行156–184无逐对象成功结果或catch，委托表持久化；这些字节码未证明真实NC容器事务/底层DAO不存在独立提交，仍须NC故障回滚验证。
- 因无法只凭返回原数组证明批量每对象均成功，本轮SupplierPriceOperationAdapter在validate和describeResult均明确拒绝多对象新增/修改/删除；仅保留单对象原生batchSave桥接。混合操作同样拒绝。批量部分成功需后续专用结果/事务适配，不能将原数组成员全记为成功。
