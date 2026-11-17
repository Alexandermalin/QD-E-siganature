package nc.ui.uapbd.qd.esign;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.material.fi.MaterialFiVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;
import nc.vo.uapbd.qd.esign.OperationContext;

/** The UI accepts only receipts matching its signed rows and reported per-unit outcomes. */
public final class MaterialPageDeleteClientTests {
    private MaterialPageDeleteClientTests() { }
    public static void run() throws Exception {
        OperationContext request=new OperationContext("request", "batch", "G", "A", "10140MAO", "物料", "FiDelete", "删除",
            "DELETE", null, Arrays.asList(new BusinessObjectRef("a","BD_MATERIAL_FI","A",null,null),
                new BusinessObjectRef("b","BD_MATERIAL_FI","B",null,null)), OperationContext.OWNER_POLICY_PROTOCOL);
        MaterialPageDeleteResult.Unit a=unit("a","A","SUCCESS"), b=unit("b","B","FAILED");
        QdMaterialOrgInfoDeleteAction.validateReceipt(request,new MaterialPageDeleteResult("request","PARTIAL",Arrays.asList(a,b)));
        expectRejected(request,new MaterialPageDeleteResult("request","SUCCESS",Arrays.asList(a,b)));
        expectRejected(request,new MaterialPageDeleteResult("request","PARTIAL",Arrays.asList(a,unit("b","FORGED","FAILED"))));
        expectRejected(request,new MaterialPageDeleteResult("request","SUCCESS",Collections.singletonList(a)));
        expectRejected(request,new MaterialPageDeleteResult("other","PARTIAL",Arrays.asList(a,b)));
        QdMaterialOrgInfoDeleteAction.validateReceipt(request,new MaterialPageDeleteResult("request","UNKNOWN",
            Arrays.asList(a,unit("b","B","UNKNOWN"))));
        expectRejected(request,new MaterialPageDeleteResult("request","PARTIAL",Arrays.asList(a,unit("b","B","SKIPPED"))));
        OperationContext warehouse=new OperationContext("request","batch","G","A","10140WARH","仓库","Delete","删除",
            "DELETE",null,Arrays.asList(new BusinessObjectRef("a","BD_STORDOC","A",null,null),
                new BusinessObjectRef("b","BD_STORDOC","B",null,null)),OperationContext.OWNER_POLICY_PROTOCOL);
        QdMaterialOrgInfoDeleteAction.validateReceipt(warehouse,new MaterialPageDeleteResult("request","PARTIAL",
            Arrays.asList(a,unit("b","B","SKIPPED"))));
        expectRejected(warehouse,new MaterialPageDeleteResult("request","PARTIAL",
            Arrays.asList(unit("a","A","SKIPPED"),unit("b","B","SUCCESS"))));
        MaterialFiVO first=row("A","G","MAT"),second=row("B","G","MAT");
        Map<String,SuperVO> bound=new LinkedHashMap<String,SuperVO>();bound.put("a",first);bound.put("b",second);
        require(QdMaterialOrgInfoDeleteAction.sameRows(request,bound,new Object[]{second,first},"MAT"),
            "Reordered selected rows should match");
        require(!QdMaterialOrgInfoDeleteAction.sameRows(request,bound,new Object[]{first,first},"MAT"),
            "A duplicate selection must not match");
        require(!QdMaterialOrgInfoDeleteAction.sameRows(request,bound,new Object[]{first,row("B","G","OTHER")},"MAT"),
            "A row from another material must not match");
        bound.put("b",row("OTHER","G","MAT"));
        require(!QdMaterialOrgInfoDeleteAction.sameRows(request,bound,new Object[]{first,second},"MAT"),
            "Changed signed payload must not match");
        System.out.println("PASS MaterialPageDeleteClientTests (partial and unknown receipt validation; no NC worker invoked)");
    }
    private static MaterialPageDeleteResult.Unit unit(String id,String pk,String state) {
        return new MaterialPageDeleteResult.Unit(id,pk,"MATERIAL","ORG",state,null);
    }
    private static MaterialFiVO row(final String pk,final String group,final String material) {
        return new MaterialFiVO() {
            @Override public String getPrimaryKey() { return pk; }
            @Override public Object getAttributeValue(String name) {
                return "pk_group".equals(name)?group:"pk_material".equals(name)?material:null;
            }
        };
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
    private static void expectRejected(OperationContext request,MaterialPageDeleteResult result) throws Exception {
        try { QdMaterialOrgInfoDeleteAction.validateReceipt(request,result);throw new AssertionError("Invalid delete receipt accepted"); }
        catch (BusinessException expected) { }
    }
}
