package nc.ui.pu.m25.action;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import nc.bs.framework.common.NCLocator;
import nc.bs.pub.filesystem.IFileSystemService;
import nc.itf.qidu.busi.BarcodeBusiItf_NONE;
import nc.pu.m25.http.QueryInvoiceUtil;
import nc.pu.m25.itf.IUpdateInvoiceInfo;
import nc.pu.m25.logger.ItfLogger;
import nc.pu.m25.relevance.vo.RelevanceHVO;
import nc.pu.m25.util.QueryDefInfo;
import nc.ui.pu.m25.dialog.RelevanceDataDialog;
import nc.ui.pu.m25.dialog.RelevanceInvoiceDialog;
import nc.ui.pu.m25.model.InvoiceBillManageModel;
import nc.ui.pu.m25.view.InvoiceBillForm;
import nc.ui.pu.qd.esign.relation.InvoiceRelationBridge;
import nc.ui.pub.beans.UIDialog;
import nc.ui.pubapp.uif2app.query2.DefaultQueryConditionDLG;
import nc.ui.pubapp.uif2app.query2.IQueryConditionDLGInitializer;
import nc.ui.pubapp.uif2app.query2.QueryConditionDLGDelegator;
import nc.ui.pubapp.uif2app.query2.action.DefaultQueryAction;
import nc.ui.trade.business.HYPubBO_Client;
import nc.ui.uif2.NCAction;
import nc.ui.uif2.ShowStatusBarMsgUtil;
import nc.ui.uif2.editor.QueryTemplateContainer;
import nc.uif.pub.exception.UifException;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceItemVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.pub.lang.UFDouble;
import nc.vo.pub.query.ConditionVO;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.querytemplate.TemplateInfo;
import nc.vo.uif2.LoginContext;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * @author hanjwb 采购发票-关联发票按钮类
 * 
 */
public class RelevanceInvoiceAction extends NCAction {

	private static final String BUTTON_CODE = "RelevanceInvoice";

	public RelevanceInvoiceAction() {
		setBtnName("关联发票");
		setCode(BUTTON_CODE);
	}

	private InvoiceBillForm editor = null;

	private InvoiceBillManageModel model = null;

	private QueryTemplateContainer templateContainer;
	private IQueryConditionDLGInitializer qryCondDLGInitializer;
	private QueryConditionDLGDelegator qryCondDLGDelegator;
	

	// 实例化创建查询条件JSON类
	CreateQueryJson queryJson = new CreateQueryJson();

	// 实例化接口推送类
	QueryInvoiceUtil queryInUtil = new QueryInvoiceUtil();

	// 创建列表VO
	CreateQueryVO createVo = new CreateQueryVO();

	// 实例化创建JSON类
	CreateToSdJson createJson = new CreateToSdJson();
	
	// 定义日志输出接口
	BarcodeBusiItf_NONE jtekt_none = NCLocator.getInstance().lookup(BarcodeBusiItf_NONE.class);
	

	@Override
	public void doAction(ActionEvent e) throws Exception {
		// 获取选中数据
		InvoiceVO aggVo = (InvoiceVO) getModel().getSelectedData();
		// 表头VO
		InvoiceHeaderVO hvo = aggVo.getParentVO();
		// 价税合计
		UFDouble nTotalOrigMny = hvo.getNtotalorigmny();
		// 单据号
		String vbillCode = hvo.getVbillcode();
		// 表头主键
		String hpk = hvo.getPk_invoice();
		// 获取表体数据
		InvoiceItemVO[] bodys = aggVo.getChildrenVO();
		// 表体总税额
		UFDouble totalTax = UFDouble.ZERO_DBL;
		// 表体总无税金额
		UFDouble totalOrigMny = UFDouble.ZERO_DBL;
		// 供应商
		String supPk = hvo.getPk_supplier();
		// 是否关联
		String vdef2 = hvo.getVdef2();
		// 单据状态
		Integer billStatus = hvo.getFbillstatus();
		Map<String, String> defMap = QueryDefInfo.getInstance().getDefInfo();
		// 获取已关联PK
		if(billStatus == 0){
			String defPk = defMap.get("已关联");
			if(defPk.equals(vdef2)){
				ExceptionUtils.wrappBusinessException("已关联发票，不允许再次关联！");
		}
		}
		for (InvoiceItemVO body : bodys) {
			// 税额
			if(body.getNtax() != null){
				totalTax = totalTax.add(body.getNtax());
			}
			// 无税金额
			totalOrigMny = totalOrigMny.add(body.getNorigmny());
		}
		// 只有自由单数据才可以关联发票
		if (billStatus == 1||billStatus == 2) {
			ExceptionUtils.wrappBusinessException("当前单据已提交，无法关联发票！");
		}else if(billStatus == 3){
			ExceptionUtils.wrappBusinessException("当前单据已审批，无法关联发票！");
		}
		// 获取上下文
		LoginContext context = getModel().getContext();
		// 集团
		String pk_group = context.getPk_group();
		// 组织
		String pk_org = context.getPk_org();
		// 查询模板
		Container container = context.getEntranceUI();
		this.setQryCondDLGDelegator(null);
		QueryConditionDLGDelegator queryConditionDLG = getQryDLGDelegator();
		// 根据组织获取购方税号
		String taxpayercode = (String) HYPubBO_Client.findColValue(
				"org_financeorg", "taxpayercode", " pk_financeorg = '" + pk_org
						+ "' and enablestate = 2 ");
		queryConditionDLG.setDefaultValue("gfsh", taxpayercode);
		queryConditionDLG.setDefaultValue("xfmc", supPk);
		if (queryConditionDLG.showModal() == UIDialog.ID_OK) {
			// 获取查询界面的查询条件
			ConditionVO[] conditions = queryConditionDLG.getQueryConditionDLG()
					.getQryCondEditor().getGeneralCondtionVOs();
			// 创建查询JSON
			JSONObject conJson = queryJson.getConJson(conditions);
			systemOutDate("单据"+vbillCode+"查询数电发票开始，发送json如下："+JSON.toJSONString(conJson));
			// 查询接口日志记录
//			logStr.append("单据"+vbillCode+"查询数电发票开始，发送json如下："+JSON.toJSONString(conJson));
			String jsonMessage = JSON.toJSONString(conJson);
			Map<String, Object> mapTypes = JSON.parseObject(jsonMessage);
			// 查询数电发票信息
//			String result = queryInUtil.httpInvoice(mapTypes, "queryInvoice");
			String result = queryInUtil.doQuery("queryInvoice",jsonMessage);
			systemOutDate("单据"+vbillCode+"查询数电发票返回json如下："+result);
			// 查询接口返回数据
//			logStr.append("单据"+vbillCode+"查询数电发票返回json如下："+result);
			// 将查询发票日志写入数据库
			jtekt_none.saveNCLog("发送", "关联发票-查询发票", "单据"+vbillCode+"查询数电发票开始，发送json如下："+JSON.toJSONString(conJson), 
					"单据"+vbillCode+"查询数电发票返回json如下："+result, getMSTime());
			JSONObject resultJson = (JSONObject) JSON.parse(result);
			// 查询失败则抛错
			String code = (String) resultJson.get("code");
			String message = (String) resultJson.get("message");
			if (!"E0000".equals(code)&&!"E002".equals(code)) {
				systemOutDate("单据"+vbillCode+"查询数电发票失败"+message);
				ExceptionUtils.wrappBusinessException("查询数电发票失败：" + message);
			}
			List<RelevanceHVO> aggList = null;
			// code=E002表示没有符合的数据
			if("E002".equals(code)){
				// 打开列表界面
				RelevanceInvoiceDialog dialog = new RelevanceInvoiceDialog(
						container, pk_org, aggList);
				if (dialog.showModal() == UIDialog.ID_OK) {
					dialog.closeOK();
					return;
				}
			}else{
			// TestJson test = new TestJson();--测试json
			// JSONObject resultJson = test.getJson();
			// 获取界面显示VO
			aggList = createVo.createAggVo(resultJson);
			// 打开列表界面
			RelevanceInvoiceDialog dialog = new RelevanceInvoiceDialog(
					container, pk_org, aggList);
			if (dialog.showModal() == UIDialog.ID_OK) {
				// 获取选中数据
				RelevanceHVO[] hvos = dialog.getAggPendBillVO();
				if (InvoiceRelationBridge.isControlled(getModel(), aggVo, BUTTON_CODE)) {
					if (hvos == null || hvos.length == 0) {
						throw new BusinessException("请选择一条记录");
					}
					InvoiceRelationAmounts amounts = InvoiceRelationAmounts.compare(
							nTotalOrigMny, totalTax, totalOrigMny, hvos);
					RelevanceDataDialog dataDialog = new RelevanceDataDialog(container,
							amounts.differs ? "NO" : "YES", amounts.grossDifference, amounts.taxDifference);
					if (dataDialog.showModal() == UIDialog.ID_OK
							&& InvoiceRelationBridge.execute(getModel(), e, BUTTON_CODE, aggVo, hvos)) {
						InvoiceRelationRefresh.afterCommit(getModel(), aggVo, "关联");
					}
					return;
				}
				// 调取回传关联发票接口-成功接着后续操作，失败则返回所有操作
				// 获取回传关联JSON
				JSONObject backJson = createJson.createBackJson(hvos,
						vbillCode, nTotalOrigMny, "1");
				// 关联日志输出
//				logStr.append("单据"+vbillCode+"回传关联发票开始，发送json如下："+backJson);
				String backStr = JSON.toJSONString(backJson);
				// 回传关联日志输出
				systemOutDate("单据"+vbillCode+"回传关联发票开始，发送json如下："+backStr);
//				Map<String, Object> backMap = JSON.parseObject(backStr);
				// 回传关联发票
				String backResult = queryInUtil.doQuery("updateInvoiceCon", backStr);
				JSONObject backRes = JSON.parseObject(backResult);
				systemOutDate("回传关联返回数据："+JSON.toJSONString(backRes));
				// 回传关联日志输出
//				logStr.append("单据"+vbillCode+"回传关联发票返回数据："+backRes);
				// 将回传关联发票日志写入数据库
				jtekt_none.saveNCLog("发送", "关联发票-回传关联发票", "单据"+vbillCode+"回传关联发票开始，发送json如下："+backJson, 
						"单据"+vbillCode+"回传关联发票返回数据："+backRes, getMSTime());
				// 获取返回数组
				JSONArray backArr = (JSONArray) backRes.get("result");
//				JSONArray backArr = JSON.parseArray(backResult);
				// 是否回传关联成功
				UFBoolean isBack = UFBoolean.FALSE;
				StringBuffer backMeg = new StringBuffer();
				for (int i = 0; i < backArr.size(); i++) {
					JSONObject json = (JSONObject) backArr.get(i);
					// 返回编码
					String code1 = (String) json.get("code");
					// 如果返回失败则改变isback值为true
					if (!"E000".equals(code1)) {
						backMeg.append("发票号："+json.get("fphm")+""+json.get("message")+" ");
						isBack = UFBoolean.TRUE;
					}
				}
				// 回传关联数电发票一条失败则返回
				if (isBack.booleanValue()) {
					ExceptionUtils.wrappBusinessException("回传关联数电发票失败:"+backMeg);
				}
				// 回传数电关联发票成功则更新采购发票表头关联状态等数据
				NCLocator.getInstance().lookup(IUpdateInvoiceInfo.class).updateInvoiceHData(aggVo, hvos);
				// 挂载数电附件
				NCLocator.getInstance()
						.lookup(IUpdateInvoiceInfo.class)
						.mountInvoiceAttachment(aggVo, hvos);
				// 比对勾选发票价税合计、总税额、总无税金额是否相同-相同则同步发票号等信息，不同则弹框提示后续操作
				InvoiceRelationAmounts amounts = InvoiceRelationAmounts.compare(
						nTotalOrigMny, totalTax, totalOrigMny, hvos);
				UFDouble subTotalMny = amounts.grossDifference;
				UFDouble subTotalTax = amounts.taxDifference;
				UFDouble subTotalOriMny = amounts.netDifference;
				if (amounts.differs) {
					RelevanceDataDialog dataDialog = new RelevanceDataDialog(
							container,"NO",subTotalMny,subTotalTax);
					// 点击确认则更新表头价税合计及表体第一行税额、无税金额;否则调取取消关联接口
					if (dataDialog.showModal() == dataDialog.ID_OK) {
						// 更新采购发票数据
						NCLocator
								.getInstance()
								.lookup(IUpdateInvoiceInfo.class)
								.updateInvoiceData(aggVo, hvos, subTotalMny,
										subTotalTax, subTotalOriMny, "item");
//						// 挂载数电附件
//						NCLocator.getInstance()
//								.lookup(IUpdateInvoiceInfo.class)
//								.mountInvoiceAttachment(aggVo, hvos);
						// 获取变动后的aggvo
						InvoiceHeaderVO pvo = aggVo.getParentVO();
						// 将自定义项复制为已关联
						pvo.setVdef2(defMap.get("已关联"));
						// 将价税合计改为修改后数据
						pvo.setNtotalorigmny(pvo.getNtotalorigmny().sub(subTotalMny));
						// 拼装数电发票号
						StringBuffer invoiceNos = new StringBuffer();
						// 拼装数电发票id
						StringBuffer invoiceIds = new StringBuffer();
						for(RelevanceHVO hvo1:hvos){
							invoiceNos.append(hvo1.getFphm());
							invoiceNos.append(";");
							invoiceIds.append(hvo1.getId());
							invoiceIds.append(";");
						}
						invoiceNos.replace(invoiceNos.length()-1, invoiceNos.length(), "");
						invoiceIds.replace(invoiceIds.length()-1, invoiceIds.length(), "");
						pvo.setVdef1(invoiceNos.toString());
						pvo.setVdef3(invoiceIds.toString());
						String hts = (String) HYPubBO_Client.findColValue("po_invoice",
								"ts", " pk_invoice = '"+pvo.getPk_invoice()+"' ");
						pvo.setTs(new UFDateTime(hts));
						aggVo.setParentVO(pvo);
						// 获取表体数据
						InvoiceItemVO[] items = aggVo.getChildrenVO();
						for(InvoiceItemVO item:items){
							String rowNo = item.getCrowno();
							// 获取第一行数据
							if("10".equals(rowNo)){
								// 税额
								UFDouble tax = UFDouble.ZERO_DBL;
								if(item.getNtax()!=null){
									tax = item.getNtax();
								}
								// 含税金额
								UFDouble oriMny = item.getNorigtaxmny();
								item.setNtax(tax.sub(subTotalTax));
								item.setNorigtaxmny(oriMny.sub(subTotalMny));
								item.setNorigmny((oriMny.sub(subTotalMny)).sub((tax.sub(subTotalTax))));
								String bts = (String) HYPubBO_Client.findColValue("po_invoice_b",
										"ts", " pk_invoice_b = '"+item.getPk_invoice_b()+"' ");
								item.setTs(new UFDateTime(bts));
							}
						}
						aggVo.setChildrenVO(items);
					} else {
						// 弹差异弹框点击取消时需更新关联状态等数据-置空
						NCLocator.getInstance().lookup(IUpdateInvoiceInfo.class).unUpdateInvoiceHData(aggVo, hvos);
						// 定义删除附件List
						List<String> delList = new ArrayList<>();
						// 获取选中发票的发票号
						for(RelevanceHVO rhvo:hvos){
							String fphm = rhvo.getFphm();
							delList.add(hpk+"/"+fphm);
						}
						// 删除对应附件
						if(delList!=null&&delList.size()>0){
							NCLocator.getInstance().lookup(IFileSystemService.class).deleteNCFileNodes(delList.toArray(new String[delList.size()]));
						}
						// 调取数电取消关联接口
						// 获取取消关联JSON
						JSONObject unJson = createJson.createBackJson(hvos,
								vbillCode, nTotalOrigMny, "0");
						String unStr = JSON.toJSONString(unJson);
						systemOutDate("取消关联发票开始，发送json为："+unStr);
						// 取消关联日志输出
//						logStr.append("单据"+vbillCode+"取消关联发票开始，发送json为："+unStr);
						Map<String, Object> unMap = JSON.parseObject(unStr);
						// 回传关联发票
						String unResult = queryInUtil.doQuery("updateInvoiceCon", unStr);
						JSONObject unResultJson = (JSONObject) JSON.parse(unResult);
						JSONArray unArr = (JSONArray) unResultJson.get("result");
//						JSONArray unArr = JSON.parseArray(unResult);
						systemOutDate("取消关联发票返回json："+unResult);
						// 取消关联回传日志输出
//						logStr.append("单据"+vbillCode+"取消关联发票返回json："+unResult);
						// 将回传取消关联发票日志写入数据库
						jtekt_none.saveNCLog("发送", "关联发票-回传取消关联发票", "单据"+vbillCode+"取消关联发票开始，发送json为："+unStr, 
								"单据"+vbillCode+"取消关联发票返回json："+unResult, getMSTime());
						// 是否回传关联成功
						UFBoolean isUnBack = UFBoolean.FALSE;
						// 返回信息重组
						StringBuffer errMeg = new StringBuffer();
						for (int i = 0; i < unArr.size(); i++) {
							JSONObject json = (JSONObject) unArr.get(i);
							// 返回编码
							String code1 = (String) json.get("code");
							// 如果返回失败则改变isback值为true
							if (!"E000".equals(code1)) {
								isUnBack = UFBoolean.TRUE;
								errMeg.append("发票号："+json.get("fphm")+""+json.get("message")+" ");
							}
						}
						// 回传关联数电发票一条失败则返回
						if (isUnBack.booleanValue()) {
							ExceptionUtils
									.wrappBusinessException("取消关联数电发票失败:"+errMeg);
						}
						// 差异弹框点击取消提示取消成功
						ShowStatusBarMsgUtil.showStatusBarMsg("取消成功！", this.getModel().getContext());
					}
				} else {
					RelevanceDataDialog dataDialog = new RelevanceDataDialog(
							container,"YES",subTotalMny,subTotalTax);
					if (dataDialog.showModal() == dataDialog.ID_OK) {
					// 更新采购发票数据-数据相同则只更新关联状态及数电发票号
					NCLocator
							.getInstance()
							.lookup(IUpdateInvoiceInfo.class)
							.updateInvoiceData(aggVo, hvos, subTotalMny,
									subTotalTax, subTotalOriMny, null);
					// 获取变动后的aggvo
					InvoiceHeaderVO pvo = aggVo.getParentVO();
					// 将自定义项复制为已关联
					pvo.setVdef2(defMap.get("已关联"));
					// 拼装数电发票号
					StringBuffer invoiceNos = new StringBuffer();
					// 拼装数电发票id
					StringBuffer invoiceIds = new StringBuffer();
					for(RelevanceHVO hvo1:hvos){
						invoiceNos.append(hvo1.getFphm());
						invoiceNos.append(";");
						invoiceIds.append(hvo1.getId());
						invoiceIds.append(";");
					}
					invoiceNos.replace(invoiceNos.length()-1, invoiceNos.length(), "");
					invoiceIds.replace(invoiceIds.length()-1, invoiceIds.length(), "");
					pvo.setVdef1(invoiceNos.toString());
					pvo.setVdef3(invoiceIds.toString());
					String hts = (String) HYPubBO_Client.findColValue("po_invoice",
							"ts", " pk_invoice = '"+pvo.getPk_invoice()+"' ");
					pvo.setTs(new UFDateTime(hts));
					aggVo.setParentVO(pvo);
//					// 挂载数电附件
//					NCLocator.getInstance().lookup(IUpdateInvoiceInfo.class)
//							.mountInvoiceAttachment(aggVo, hvos);
					}else{
						// 弹差异弹框点击取消时需更新关联状态等数据-置空
						NCLocator.getInstance().lookup(IUpdateInvoiceInfo.class).unUpdateInvoiceHData(aggVo, hvos);
						// 定义删除附件List
						List<String> delList = new ArrayList<>();
						// 获取选中发票的发票号
						for(RelevanceHVO rhvo:hvos){
							String fphm = rhvo.getFphm();
							delList.add(hpk+"/"+fphm);
						}
						// 删除对应附件
						if(delList!=null&&delList.size()>0){
							NCLocator.getInstance().lookup(IFileSystemService.class).deleteNCFileNodes(delList.toArray(new String[delList.size()]));
						}
						// 调取数电取消关联接口
						// 获取取消关联JSON
						JSONObject unJson = createJson.createBackJson(hvos,
								vbillCode, nTotalOrigMny, "0");
						String unStr = JSON.toJSONString(unJson);
						systemOutDate("取消关联发票开始，发送json为："+unStr);
						// 取消关联日志输出
//						logStr.append("单据"+vbillCode+"取消关联发票开始，发送json为："+unStr);
						Map<String, Object> unMap = JSON.parseObject(unStr);
						// 回传关联发票
						String unResult = queryInUtil.doQuery("updateInvoiceCon", unStr);
						JSONObject unResultJson = (JSONObject) JSON.parse(unResult);
						JSONArray unArr = (JSONArray) unResultJson.get("result");
//						JSONArray unArr = JSON.parseArray(unResult);
						systemOutDate("取消关联发票返回json："+unResult);
						// 取消关联回传日志输出
//						logStr.append("单据"+vbillCode+"取消关联发票返回json："+unResult);
						// 将回传取消关联发票日志写入数据库
						jtekt_none.saveNCLog("发送", "关联发票-回传取消关联发票", "单据"+vbillCode+"取消关联发票开始，发送json为："+unStr, 
								"单据"+vbillCode+"取消关联发票返回json："+unResult, getMSTime());
						// 是否回传关联成功
						UFBoolean isUnBack = UFBoolean.FALSE;
						// 返回信息重组
						StringBuffer errMeg = new StringBuffer();
						for (int i = 0; i < unArr.size(); i++) {
							JSONObject json = (JSONObject) unArr.get(i);
							// 返回编码
							String code1 = (String) json.get("code");
							// 如果返回失败则改变isback值为true
							if (!"E000".equals(code1)) {
								isUnBack = UFBoolean.TRUE;
								errMeg.append("发票号："+json.get("fphm")+""+json.get("message")+" ");
							}
						}
						// 回传关联数电发票一条失败则返回
						if (isUnBack.booleanValue()) {
							ExceptionUtils
									.wrappBusinessException("取消关联数电发票失败:"+errMeg);
						}
						// 差异弹框点击取消提示取消成功
						ShowStatusBarMsgUtil.showStatusBarMsg("取消成功！", this.getModel().getContext());
					}
				}
			}
		}
		}
		// 更新界面数据
		this.getModel().directlyUpdate(aggVo);
	}
	
	public static String getMSTime() {
		return new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss.SSS").format(new Date(System.currentTimeMillis()));
	}
	
	

	private TemplateInfo getTemplateInfo() {
		String id = null;
		try {
			id = (String) HYPubBO_Client.findColValue("pub_query_templet",
					"id", " node_code='40041000' and model_name='关联数电发票查询模板' ");
		} catch (UifException e) {
			ExceptionUtils.wrappBusinessException("关联数电发票查询模板不存在，请检查！");
		}
		TemplateInfo tempinfo = new TemplateInfo();
		tempinfo.setTemplateId(id);
		tempinfo.setPk_Org(getModel().getContext().getPk_group());
		tempinfo.setFunNode("40041000");
		tempinfo.setUserid(getModel().getContext().getPk_loginUser());
		tempinfo.setNodekey("qt");
		tempinfo.setSealedDataShow(true);
		return tempinfo;
	}

	public QueryConditionDLGDelegator getQryDLGDelegator() {
		if (this.qryCondDLGDelegator == null) {
			TemplateInfo tempinfo = getTemplateInfo();

			this.qryCondDLGDelegator = createQryDLGDelegator(tempinfo);
			this.qryCondDLGDelegator.getQce().getQueryContext()
					.setReloadQuickAreaValue(false);
		}

		return this.qryCondDLGDelegator;
	}

	protected QueryConditionDLGDelegator createQryDLGDelegator(TemplateInfo arg0) {
		QueryConditionDLGDelegator dlgDelegator = new QueryConditionDLGDelegator(
				this.getModel().getContext().getEntranceUI(), arg0, null);
		initQueryConditionDLG(dlgDelegator);

		return dlgDelegator;
	}
	
	 protected void initQueryConditionDLG(QueryConditionDLGDelegator dlgDelegator)
	 {
	    if (getQryCondDLGInitializer() != null) {
	    getQryCondDLGInitializer().initQueryConditionDLG(dlgDelegator);
	   }
	  }
	 
	 public IQueryConditionDLGInitializer getQryCondDLGInitializer() {
		  return this.qryCondDLGInitializer;
	   }
	 
	  public void setQryCondDLGDelegator(QueryConditionDLGDelegator qryCondDLGDelegator)
	   {
	    this.qryCondDLGDelegator = qryCondDLGDelegator;
	  }

	// public QueryTemplateContainer getTemplateContainer() {
	// return templateContainer;
	// }
	//
	// public void setTemplateContainer(QueryTemplateContainer
	// templateContainer) {
	// this.templateContainer = templateContainer;
	// }

	public InvoiceBillForm getEditor() {
		return this.editor;
	}

	public InvoiceBillManageModel getModel() {
		return this.model;
	}

	public void setEditor(InvoiceBillForm editor) {
		this.editor = editor;
	}

	public void setModel(InvoiceBillManageModel model) {
		this.model = model;
		model.addAppEventListener(this);
	}
	
	/**
	 * 输出日志方法
	 * @param text
	 */
	private void systemOutDate(String text) {
		SimpleDateFormat dateFormat= new SimpleDateFormat("yyyy-MM-dd :hh:mm:ss");
		ItfLogger.error(text+dateFormat.format(new Date() ));
	}
}
