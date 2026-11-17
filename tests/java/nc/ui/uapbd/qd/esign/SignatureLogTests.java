package nc.ui.uapbd.qd.esign;

import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import nc.itf.uapbd.qd.esign.ILogExportService;
import nc.itf.uapbd.qd.esign.ILogQueryService;
import nc.ui.bd.ref.busi.UserDefaultRefModel;
import nc.ui.org.ref.BusinessUnitDefaultRefModel;
import nc.ui.pub.beans.UIComboBox;
import nc.ui.pub.beans.UIRefPane;
import nc.ui.querytemplate.querytype.UFDateTimeTextField;
import nc.vo.uapbd.qd.esign.ButtonPolicyRow;
import nc.vo.uapbd.qd.esign.OrganizationPolicyView;
import nc.vo.uapbd.qd.esign.log.LogKind;
import nc.vo.uapbd.qd.esign.log.LogQuery;

/** 签名列表、原生参照及版本化调用的离线回归。 */
public final class SignatureLogTests {
    private static int assertions;
    private SignatureLogTests() { }
    public static void run() throws Exception {
        assertions=0; testEnum(); testVersionContracts();
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { try { testPanel(); testPolicyStatus(); } catch(Exception error) { throw new IllegalStateException(error); } } });
        System.out.println("PASS SignatureLogTests assertions="+assertions+" (native controls, columns and result mapping)");
    }
    private static void testEnum() {
        check(SignatureResultOption.ALL.code()==null,"all clears result filter");
        check("签名成功".equals(SignatureResultOption.display("SUCCESS")),"signature success label");
        check("密码错误".equals(SignatureResultOption.display("PASSWORD_INCORRECT")),"incorrect password label");
        check("账号锁定".equals(SignatureResultOption.display("ACCOUNT_LOCKED")),"locked label");
        check("账号不可用".equals(SignatureResultOption.display("ACCOUNT_UNAVAILABLE")),"unavailable label");
        check("已取消".equals(SignatureResultOption.display("CANCELLED")),"cancelled label");
        check("已过期".equals(SignatureResultOption.display("EXPIRED")),"expired label");
        check("OLD_STATUS".equals(SignatureResultOption.display("OLD_STATUS")),"unknown historical code preserved");
    }
    private static void testVersionContracts() throws Exception {
        check(ILogQueryService.class.getMethod("querySignatureV2",LogQuery.class)!=null,"versioned signature query exists");
        check(ILogExportService.class.getMethod("exportSignatureV2",LogQuery.class,Boolean.TYPE)!=null,"versioned filtered export exists");
        check(ILogExportService.class.getMethod("exportSignatureLoadedV2",LogQuery.class,String[].class)!=null,"versioned loaded export exists");
        Method mismatch=QdLogQueryPanel.class.getDeclaredMethod("versionMismatch",Throwable.class);mismatch.setAccessible(true);
        check(Boolean.TRUE.equals(mismatch.invoke(null,new NoSuchMethodException("querySignatureV2"))),"old service version is recognized");
        check(Boolean.FALSE.equals(mismatch.invoke(null,new IllegalStateException("access denied"))),"permission failure is not mislabeled as version mismatch");
    }
    private static void testPanel() throws Exception {
        QdLogQueryPanel panel=new QdLogQueryPanel(LogKind.SIGNATURE,null);
        check(((UIRefPane)field(panel,"orgRef")).getRefModel() instanceof BusinessUnitDefaultRefModel,"native business unit reference");
        UIRefPane operator=(UIRefPane)field(panel,"operatorRef");
        UserDefaultRefModel users=(UserDefaultRefModel)operator.getRefModel();
        check(!users.isAddEnableStateWherePart(),"disabled accounts can be selected for historical logs");
        check(operator.isReturnCode(),"operator reference displays user code");
        check(field(panel,"auditFrom") instanceof UFDateTimeTextField && field(panel,"auditTo") instanceof UFDateTimeTextField,"native date-time controls");
        UIComboBox result=(UIComboBox)field(panel,"signatureResult");result.setSelectedItem(SignatureResultOption.CANCELLED);
        check(result.getSelectedItem()==SignatureResultOption.CANCELLED,"result control selects code-backed enum");
        check(findButton(panel,"关联签名")==null && findButton(panel,"审计明细")==null && findButton(panel,"关联审计")!=null,"signature buttons match confirmed behavior");
        check(!hasLabel(panel,"操作人员主键"),"user primary key query removed");
        Object model=field(panel,"tableModel");Method show=model.getClass().getDeclaredMethod("show",List.class);show.setAccessible(true);
        Map<String,Object> first=new LinkedHashMap<String,Object>();first.put("user_id","private-user-pk");first.put("pk_org","org-990");first.put("qd_org_name","测试业务单元1");
        first.put("user_code","yonyou02");first.put("user_name","测试用户");first.put("attempt_id","attempt-1");first.put("result_code","CANCELLED");
        Map<String,Object> second=new LinkedHashMap<String,Object>(first);second.put("pk_org","missing");second.put("qd_org_name",null);second.put("result_code","UNKNOWN");
        show.invoke(model,Arrays.asList(first,second));JTable table=(JTable)field(panel,"table");
        check("操作组织".equals(table.getColumnName(0)) && "测试业务单元1".equals(table.getValueAt(0,0)),"translated organization is first column");
        check("未识别组织".equals(table.getValueAt(1,0)),"missing organization retains row and displays explicit marker");
        check(findColumn(table,"人员主键")<0 && findColumn(table,"操作人编码")>=0 && findColumn(table,"操作人")>=0,"operator columns localized and primary key hidden");
        check(findColumn(table,"签名处理编号")>=0,"attempt ID uses Chinese label");
        int status=findColumn(table,"结果类型");check(status>=0 && "已取消".equals(table.getValueAt(0,status)) && "UNKNOWN".equals(table.getValueAt(1,status)),"result values mapped without hiding unknown codes");
        QdLogQueryPanel audit=new QdLogQueryPanel(LogKind.AUDIT,null);
        check(findButton(audit,"关联审计")!=null && findButton(audit,"关联签名")!=null,"audit association buttons remain available");
    }
    private static void testPolicyStatus() throws Exception {
        QdPolicyPanel panel=new QdPolicyPanel(null,null);
        QdPolicyTableModel model=(QdPolicyTableModel)field(panel,"tableModel");
        List<ButtonPolicyRow> rows=Arrays.asList(
            new ButtonPolicyRow("material","10140MAO","物料","Save","保存","PILOT",true,false,false,false,0L),
            new ButtonPolicyRow("customer","10140CUO","客户","Save","保存","PILOT",true,false,false,false,0L));
        model.load(rows);model.setEditingAllowed(true);model.setValueAt(Boolean.TRUE,0,QdPolicyTableModel.SIGN);
        model.setEditingAllowed(true);model.setValueAt(Boolean.TRUE,1,QdPolicyTableModel.AUDIT);
        Field loaded=panel.getClass().getDeclaredField("loaded");loaded.setAccessible(true);
        loaded.set(panel,new OrganizationPolicyView("org",false,0L,true,true,rows));
        @SuppressWarnings("unchecked") javax.swing.table.TableRowSorter<QdPolicyTableModel> sorter=(javax.swing.table.TableRowSorter<QdPolicyTableModel>)field(panel,"nodeSorter");
        sorter.setRowFilter(QdPolicyPanel.nodeNameFilter("物料"));
        String message=((JLabel)field(panel,"accessStatus")).getText();
        check(message.contains("待保存 2 条") && message.contains("隐藏 1 条") && message.contains("提交全部"),"hidden pending changes are clearly reported");
        check(model.changes().size()==2,"filter retains both visible and hidden pending changes");
    }
    private static int findColumn(JTable table,String label){for(int i=0;i<table.getColumnCount();i++)if(label.equals(table.getColumnName(i)))return i;return -1;}
    private static Object field(Object target,String name) throws Exception {Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static JButton findButton(Container root,String text){for(java.awt.Component c:root.getComponents()){if(c instanceof JButton&&text.equals(((JButton)c).getText()))return(JButton)c;if(c instanceof Container){JButton found=findButton((Container)c,text);if(found!=null)return found;}}return null;}
    private static boolean hasLabel(Container root,String text){for(java.awt.Component c:root.getComponents()){if(c instanceof JLabel&&text.equals(((JLabel)c).getText()))return true;if(c instanceof Container&&hasLabel((Container)c,text))return true;}return false;}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
