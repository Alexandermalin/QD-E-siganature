package nc.ui.pu.m25.action;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.pub.filesystem.IFileSystemService;
import nc.itf.qidu.busi.BarcodeBusiItf_NONE;
import nc.pu.m25.http.QueryInvoiceUtil;
import nc.pu.m25.itf.IUnUpdateInvoice;
import nc.pu.m25.logger.ItfLogger;
import nc.pu.m25.util.QueryDefInfo;
import nc.ui.pu.m25.model.InvoiceBillManageModel;
import nc.ui.pu.qd.esign.relation.InvoiceRelationBridge;
import nc.ui.pub.beans.MessageDialog;
import nc.ui.pub.beans.UIDialog;
import nc.ui.uif2.NCAction;
import nc.vo.pu.m25.entity.InvoiceHeaderVO;
import nc.vo.pu.m25.entity.InvoiceItemVO;
import nc.vo.pu.m25.entity.InvoiceVO;
import nc.vo.pub.lang.UFDouble;
import nc.vo.pubapp.pattern.exception.ExceptionUtils;
import nc.vo.pubapp.pattern.pub.MathTool;
import nc.vo.uif2.LoginContext;

public class UnRelevanceInvoiceAction extends NCAction {

	private static final String BUTTON_CODE = "UnRelevanceInvoice";

	public UnRelevanceInvoiceAction() {
		setBtnName("取消关联发票");
		setCode(BUTTON_CODE);
	}

	private InvoiceBillManageModel model = null;

	@Override
	public void doAction(ActionEvent e) {
		// 获取选中数据
		InvoiceVO aggVo = (InvoiceVO) getModel().getSelectedData();
		// 表头VO
		InvoiceHeaderVO hvo = aggVo.getParentVO();
		
		// 单据状态
		Integer billStatus = hvo.getFbillstatus();

		// 表头主键
		String hpk = hvo.getPk_invoice();
		// 只有自由单数据才可以取消关联发票 0=自由，1=提交，2=正在审批，3=审批通过，4=审批未通过，
		if (billStatus == 1 ) {
			ExceptionUtils.wrappBusinessException("当前单据已提交，无法取消关联发票！！");
		}
		if (billStatus == 2) {
			ExceptionUtils.wrappBusinessException("当前单据审批中，无法取消关联发票！");
		}
		if (billStatus == 3) {
			ExceptionUtils.wrappBusinessException("当前单据已审批通过，无法取消关联发票！");
		}
		if (billStatus == 4) {
			ExceptionUtils.wrappBusinessException("当前单据审批未通过，无法取消关联发票！");
		}
		try {
		// 拿到纸质发票号字段 有可能没有发票的时候 要更新单据2024-05-15取消限制
		// String vdef1 = hvo.getVdef1();
		// if (vdef1 == null || "".equals(vdef1)) {
		// ExceptionUtils.wrappBusinessException("纸质发票号为空无法取消关联，请检查！");
		// }
		// 拿到关联状态
		// 是否关联
		String vdef2 = hvo.getVdef2();
		Map<String, String> defMap = QueryDefInfo.getInstance().getDefInfo();
		// 获取已关联PK
		String defPk = defMap.get("未关联");
		if (defPk.equals(vdef2)) {
			ExceptionUtils.wrappBusinessException("该发票为未关联状态，无法取消关联，请检查！");
		}
		// 获取上下文
		LoginContext context = getModel().getContext();
		// // 集团
		// String pk_group = context.getPk_group();
		// // 组织
		// String pk_org = context.getPk_org();
		Container container = context.getEntranceUI();
		int buttonval = MessageDialog.showYesNoDlg(container, "提示",
				"是否取消关联数电发票？");
		if (buttonval == UIDialog.ID_YES) {
			if (InvoiceRelationBridge.isControlled(getModel(), aggVo, BUTTON_CODE)) {
				if (InvoiceRelationBridge.execute(getModel(), e, BUTTON_CODE, aggVo, null)) {
					MessageDialog.showHintDlg(null, "提示", "取消关联成功!");
					InvoiceRelationRefresh.afterCommit(getModel(), aggVo, "取消关联");
				}
				return;
			}
			String vdef1 = hvo.getVdef1();
			if (vdef1 != null && !"".equals(vdef1)) {
				// 组合数据
				String str = createAssociatedJson(aggVo);
				QueryInvoiceUtil uiUtil = new QueryInvoiceUtil();
				systemOutDate("取消关联发票开始（发生于取消关联按钮），发送json为：" + str);
				String	retmsg = uiUtil.doQuery("updateInvoiceCon", str);
				
				BarcodeBusiItf_NONE jtekt_none = NCLocator.getInstance().lookup(BarcodeBusiItf_NONE.class);
				String mstime = getMSTime();
				jtekt_none.saveNCLog("发送", "取消关联", str,retmsg, mstime);
				// JSONObject resultJson = (JSONObject) JSON.parse(result);
				boolean isResetable = true; // 是否成功
				JSONObject ret = JSONObject.parseObject(retmsg);
				JSONArray resultArray = ret.getJSONArray("result");
				systemOutDate("取消关联发票结束（发生于取消关联按钮），返回json：" + retmsg);
				for (int i = 0; i < resultArray.size(); i++) {
					JSONObject item = resultArray.getJSONObject(i);
					String code = item.getString("code");
					if ("E001".equals(code)) {
						isResetable = false;
						break;
					}
				}
//		    	   表头：ntotalorigmny   价税合计  表体：
//		    	   norigmny  无税金额      vbdef1   原无税金额
//		    	   ntax   税额    vbdef2  原税额
//		    	   norigtaxmny  价税合计    vbdef3  原价税合计
				UFDouble ntotalorigmnysum=new UFDouble(0);
				if (isResetable) {
					InvoiceItemVO[] ibvo = aggVo.getChildrenVO();
					for (InvoiceItemVO bvo : ibvo) {
						// Vbdef1 原税额 Vbdef2原无税金额 Vbdef3原价税合计
						// 数据恢复
						if (bvo.getVbdef1() != null && !"".equals(bvo.getVbdef1())) {
							// ntotalorigmny 无税金额 decimal(28, 8) 数值 (UFDouble)
							// &&bvo.getVbdef1()!=bvo.getNmny()
							UFDouble ywsdouble = new UFDouble(bvo.getVbdef1());
							UFDouble norigmny = bvo.getNorigmny();
							if (MathTool.compareTo(norigmny,ywsdouble)!=0) {
								bvo.setNorigmny(ywsdouble);
							}
						}
						if (bvo.getVbdef2() != null && !"".equals(bvo.getVbdef2())) {
							// ntax 本币税额 decimal(28, 8) 数值 (UFDouble)
							UFDouble ybbdouble = new UFDouble(bvo.getVbdef2());
							UFDouble ntax = bvo.getNtax();
							if (MathTool.compareTo(ntax, ybbdouble)!=0) {
								bvo.setNtax(ybbdouble);
							}
						}
						if (bvo.getVbdef3() != null && !"".equals(bvo.getVbdef3())) {
							// norigtaxmny 价税合计 decimal(28, 8) 数值 (UFDouble)
							UFDouble yjshjdouble = new UFDouble(bvo.getVbdef3());
							UFDouble norigtaxmny = bvo.getNorigtaxmny();
							if (MathTool.compareTo(norigtaxmny,yjshjdouble)!=0) {
								bvo.setNorigtaxmny(yjshjdouble);
							}
						}
						ntotalorigmnysum=ntotalorigmnysum.add(bvo.getNorigtaxmny());
						aggVo.setChildrenVO(ibvo);
						}
					//
//					ExceptionUtils.wrappBusinessException("防止数据！");
					// 恢复采购发票数据
					NCLocator.getInstance().lookup(IUnUpdateInvoice.class)
							.updateInvoiceData(aggVo);
					////删除附件
					String[] parts = vdef1.split(";");
					List<String> delList = new ArrayList<>();
					for (String bvo : parts) {
						String fphm = bvo;
						delList.add(hpk+"/"+fphm);
					}
					if(delList!=null&&delList.size()>0){
						NCLocator.getInstance().lookup(IFileSystemService.class).deleteNCFileNodes(delList.toArray(new String[delList.size()]));
					}
					MessageDialog.showHintDlg(null, "提示", "取消关联成功!");
					// 更新界面数据
					aggVo.getParentVO().setNtotalorigmny(ntotalorigmnysum);//ntotalorigmny
					aggVo.getParentVO().setVdef1(null);
					aggVo.getParentVO().setVdef2(defMap.get("未关联"));
					aggVo.getParentVO().setVdef3(null);
					this.getModel().directlyUpdate(aggVo);
				} else {
					ExceptionUtils.wrappBusinessException("取消关联失败:" + ret);
				}
			} else {
				// 恢复采购发票数据
				NCLocator.getInstance().lookup(IUnUpdateInvoice.class)
						.updateInvoiceData(aggVo);
				MessageDialog.showHintDlg(null, "提示", "取消关联成功!");
				// 更新界面数据
				aggVo.getParentVO().setVdef1(null);
				aggVo.getParentVO().setVdef2(defMap.get("未关联"));
				aggVo.getParentVO().setVdef3(null);
				this.getModel().directlyUpdate(aggVo);
//				RefreshAction.actionPerformed(e);
			}
			}
			} catch (Exception e1) {
				// TODO 自动生成的 catch 块
				ExceptionUtils.wrappBusinessException("取消关联失败:" + e1.getMessage());
			}
		
	}

	private String createAssociatedJson(InvoiceVO aggvo) {
		JSONObject job = new JSONObject();
		InvoiceHeaderVO hvo = aggvo.getParentVO();
		String vdef1 = hvo.getVdef1();
		String[] parts = vdef1.split(";");
		JSONArray detailData = new JSONArray();
		for (String bvo : parts) {
			JSONObject jobboby = new JSONObject();
			// id 发票表ID（发票主键）
			jobboby.put("id", hvo.getVdef3());
			// fphm 发票号码
			jobboby.put("fphm", bvo);
			// ncglzt NC关联状态（0-未关联，1-已关联）
			jobboby.put("ncglzt", "0");// 取消关联应该默认为0
			// jzje NC采购发票金额
			jobboby.put("jzje", hvo.getNtotalorigmny() == null ? "" : hvo
					.getNtotalorigmny().toString());// ntotalorigmny
			// jzdh NC采购发票单号
			jobboby.put("jzdh", hvo.getVbillcode());// Vbillcode 字符 单据号
			detailData.add(jobboby);
		}
		job.put("invoices", detailData);
		return job.toJSONString();
	}

	public InvoiceBillManageModel getModel() {
		return this.model;
	}

	public void setModel(InvoiceBillManageModel model) {
		this.model = model;
		model.addAppEventListener(this);
	}

	// 按钮可不可用方法
	@Override
	protected boolean isActionEnable() {
		// 设置按钮可见性
		return true;
	}

//	public InvoiceRefreshSingleAction getCardRefreshAction() {
//		return RefreshAction;
//	}
//
//	public void setCardRefreshAction(InvoiceRefreshSingleAction cardRefreshAction) {
//		this.RefreshAction = cardRefreshAction;
//	}

	/**
	 * 输出日志方法
	 * 
	 * @param text
	 */
	private void systemOutDate(String text) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				"yyyy-MM-dd :hh:mm:ss");
		ItfLogger.error(text + dateFormat.format(new Date()));
	}
	
	public static String getMSTime() {
		return new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss.SSS").format(new Date(System.currentTimeMillis()));
	}
}
