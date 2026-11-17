package nc.impl.uapbd.qd.esign;

import java.util.Collections;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveTreeSaveDraft;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Exercises the signature preparation boundary without registering a route or invoking NC. */
public final class ArchiveTreeSignatureDraftTests {
    private ArchiveTreeSignatureDraftTests() { }
    public static void run() throws Exception {
        for(String node:new String[]{"10140MCLG","10140MCLO"}) {
            SignatureServiceImpl.requireTreeSaveDraft(save(node,"BD_MARBASCLASS","UPDATE","M",null,true));
            SignatureServiceImpl.requireTreeSaveDraft(save(node,"BD_MARBASCLASS","INSERT",null,"ORG",true));
            SignatureServiceImpl.requireTreeDeleteDraft(delete(node,"BD_MARBASCLASS","M",null));
        }
        SignatureServiceImpl.requireTreeSaveDraft(save("10140CCLO","BD_CUSTCLASS","UPDATE","C",null,true));
        SignatureServiceImpl.requireTreeDeleteDraft(delete("10140SCLO","BD_SUPPLIERCLASS","S",null));
        rejectSave(save("10140MCLO","BD_CUSTCLASS","UPDATE","C",null,true));
        rejectSave(save("10140MCLO","BD_MARBASCLASS","UPDATE","M",null,false));
        rejectSave(save("10140MCLO","BD_MARBASCLASS","INSERT",null,null,true));
        rejectDelete(delete("10140MCLO","BD_CUSTCLASS","C",null));
        rejectDelete(delete("10140MCLO","BD_MARBASCLASS",null,null));
        rejectDelete(delete("10140MCLO","BD_MARBASCLASS","M","OTHER"));
        System.out.println("PASS ArchiveTreeSignatureDraftTests (material class signature scope; no NC or Oracle)");
    }
    private static OperationContext save(String node,String domain,String operation,String pk,String owner,boolean impact) {
        BusinessObjectRef root=new BusinessObjectRef("root",domain,pk,"CODE",null,owner);
        return new OperationContext(null,null,"G","ORG",node,"分类","Save","保存",operation,null,
            Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL,null,
            impact?new ArchiveTreeSaveDraft("CODE",null,null):null);
    }
    private static OperationContext delete(String node,String domain,String pk,String owner) {
        BusinessObjectRef root=new BusinessObjectRef("root",domain,pk,"CODE",null,owner);
        return new OperationContext(null,null,"G","ORG",node,"分类","Delete","删除","DELETE",null,
            Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL);
    }
    private static void rejectSave(OperationContext value) throws Exception {
        try { SignatureServiceImpl.requireTreeSaveDraft(value);throw new AssertionError("Invalid tree save reached signature"); }
        catch(BusinessException expected) { }
    }
    private static void rejectDelete(OperationContext value) throws Exception {
        try { SignatureServiceImpl.requireTreeDeleteDraft(value);throw new AssertionError("Invalid tree delete reached signature"); }
        catch(BusinessException expected) { }
    }
}
