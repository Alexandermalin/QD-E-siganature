package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteCommand;
import nc.vo.uapbd.qd.esign.MaterialPageDeleteResult;

/** Validates the whole batch before any independent unit can be claimed. */
public final class MaterialMainDeleteBatchTests {
    private MaterialMainDeleteBatchTests() { }
    public static void run() throws Exception {
        require(MaterialPageDeleteResult.isRoute("10140MAG","Delete","DELETE"),"Group route absent");
        require(MaterialPageDeleteResult.isMaterialMain("10140MAO","Delete","DELETE"),"Unit route absent");
        require(!MaterialPageDeleteResult.isMaterialMain("10140MAO","FiDelete","DELETE"),"Page route misclassified");
        List<Map<String,Object>> objects=Arrays.asList(EsignValues.row("client_object_id","C1","object_pk","PK1"),
            EsignValues.row("client_object_id","C2","object_pk","PK2"));
        Map<String,SuperVO> rows=new LinkedHashMap<String,SuperVO>();rows.put("C1",material("PK1"));rows.put("C2",material("PK2"));
        MaterialPageDeleteServiceImpl.requireCommand(objects,new MaterialPageDeleteCommand(rows));
        MaterialPageDeleteServiceImpl.requireMaterialCommands(objects,new MaterialPageDeleteCommand(rows));
        rows.put("C2",material("OTHER"));
        try { MaterialPageDeleteServiceImpl.requireMaterialCommands(objects,new MaterialPageDeleteCommand(rows));
            throw new AssertionError("A foreign material payload was accepted"); }
        catch(BusinessException required) { }
        System.out.println("PASS MaterialMainDeleteBatchTests (route and complete batch payload guards; no NC transaction)");
    }
    private static MaterialVO material(String key) { MaterialVO row=new MaterialVO();row.setPk_material(key);return row; }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
