package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Checks selection and authorization scope without invoking the native tree or database. */
public final class ArchiveTreeSaveScopeTests {
    private ArchiveTreeSaveScopeTests() { }
    public static void run() throws Exception {
        BusinessObjectRef root=new BusinessObjectRef("root","BD_MARBASCLASS","R","CLIENT_CODE",null);
        ReaderFake reader=new ReaderFake();
        ArchiveTreeSaveScope.Decision ordinary=ArchiveTreeSaveScope.select("G",root,"OLD","P",null,reader);
        require(!ordinary.changesBranch() && ordinary.getObjects().size()==1 && "OLD".equals(ordinary.getObjects().get(0).getBillCode()),
            "Ordinary save must normalize the root without authorizing unrelated descendants");
        ArchiveTreeSaveScope.Decision cascade=ArchiveTreeSaveScope.select("G",root,"NEW","P",null,reader);
        require(cascade.changesBranch() && cascade.getObjects().size()==2,"Changed code must include the native descendant");
        ArchiveTreeSaveScope.verifyAuthorized(cascade.getObjects(),cascade);
        try { ArchiveTreeSaveScope.verifyAuthorized(ordinary.getObjects(),cascade);throw new AssertionError("Changed branch accepted"); }
        catch(BusinessException expected) { }
        reader.duplicate=true;
        try { ArchiveTreeSaveScope.select("G",root,"NEW","P",null,reader);throw new AssertionError("Duplicate branch row accepted"); }
        catch(BusinessException expected) { }
        reader.duplicate=false;reader.group="OTHER";
        try { ArchiveTreeSaveScope.select("G",root,"OLD","P",null,reader);throw new AssertionError("Cross-group archive accepted"); }
        catch(BusinessException expected) { }
        reader.group="G";reader.warehouse="W1";
        BusinessObjectRef rack=new BusinessObjectRef("rack","BD_RACK","R","OLD",null);
        try { ArchiveTreeSaveScope.select("G",rack,"OLD","P","W2",reader);throw new AssertionError("Warehouse change accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS ArchiveTreeSaveScopeTests (signed branch identity; no NC or Oracle)");
    }
    private static final class ReaderFake implements ArchiveTreeSaveScope.Reader {
        boolean duplicate;String group="G",warehouse;
        public ArchiveTreeSaveScope.State load(String group,BusinessObjectRef root) { return new ArchiveTreeSaveScope.State(this.group,"OWNER","OLD","P",warehouse); }
        public List<BusinessObjectRef> branch(String group,BusinessObjectRef root) {
            BusinessObjectRef child=new BusinessObjectRef("child",root.getObjectType(),"CHILD","CHILD",root.getClientObjectId());
            return duplicate?Arrays.asList(root,child,child):Arrays.asList(root,child);
        }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
