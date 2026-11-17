package nc.bs.uapbd.qd.esign.core;

import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.ArchiveTreeSaveDraft;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Compares branch decisions to the inspected NC tree save behavior without a database. */
public final class ArchiveTreeSaveImpactTests {
    private ArchiveTreeSaveImpactTests() { }
    public static void run() throws Exception {
        require(!ArchiveTreeSaveImpact.changesBranch("BD_CUSTCLASS","A","B","P","P"),"Customer code-only save wrongly cascades");
        require(ArchiveTreeSaveImpact.changesBranch("BD_CUSTCLASS","A","A","P","Q"),"Customer move missed its descendants");
        require(ArchiveTreeSaveImpact.changesBranch("BD_SUPPLIERCLASS","A","A",null,"~"),"Different native root parent values were collapsed");
        require(!ArchiveTreeSaveImpact.changesBranch("BD_MARBASCLASS","A"," A ","P","P"),"Native code trimming was not applied");
        require(ArchiveTreeSaveImpact.changesBranch("BD_MARBASCLASS","A","AB","P","P"),"Material code update missed its descendants");
        require(ArchiveTreeSaveImpact.changesBranch("BD_RACK","A","A","P","Q"),"Rack move missed its descendants");
        require("parent_id".equals(ArchiveTreeSaveImpact.parentField("BD_CUSTCLASS"))
            && "pk_parent".equals(ArchiveTreeSaveImpact.parentField("BD_RACK")),"Physical parent mapping is wrong");
        reject("BD_RACK","A",null);reject("BD_UNKNOWN","A","B");
        ArchiveTreeSaveDraft draft=new ArchiveTreeSaveDraft("NEW","P","W");
        BusinessObjectRef root=new BusinessObjectRef("r","BD_RACK","R","OLD",null);
        OperationContext context=new OperationContext(null,null,"G","B","10140RACK","货位","Save","保存","UPDATE",null,
            java.util.Collections.singletonList(root),OperationContext.OWNER_POLICY_PROTOCOL,null,draft);
        OperationContext copied=context.withRequest("REQ","REQ","G");
        require("NEW".equals(copied.getArchiveTreeSaveDraft().getCode()) && "P".equals(copied.getArchiveTreeSaveDraft().getParent()),
            "Prepared request lost the tree draft");
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream output=new java.io.ObjectOutputStream(bytes);output.writeObject(copied);output.close();
        java.io.ObjectInputStream input=new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        OperationContext restored=(OperationContext)input.readObject();input.close();
        require("W".equals(restored.getArchiveTreeSaveDraft().getWarehouse()),"Tree draft was lost across the RPC contract");
        System.out.println("PASS ArchiveTreeSaveImpactTests (native branch rules; no NC or Oracle)");
    }
    private static void reject(String domain,String oldCode,String newCode) throws Exception {
        try { ArchiveTreeSaveImpact.changesBranch(domain,oldCode,newCode,null,null);throw new AssertionError("Invalid tree save impact accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
