package nc.impl.uapbd.qd.esign;

import java.util.Arrays;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;

/** Shared version rows must have exactly one selected latest material. */
public final class MaterialBatchSnapshotsTests {
    private MaterialBatchSnapshotsTests() { }
    public static void run() throws Exception {
        MaterialBatchSnapshots.requireDistinctLatestSources(Arrays.asList(material("S1",true),material("S1",false),material("S2",true)));
        try { MaterialBatchSnapshots.requireDistinctLatestSources(Arrays.asList(material("S1",true),material("S1",true)));
            throw new AssertionError("Duplicate latest versions of one source accepted"); }
        catch(BusinessException expected) { }
        try { MaterialBatchSnapshots.requireDistinctLatestSources(Arrays.asList(material(null,true)));
            throw new AssertionError("Missing material version source accepted"); }
        catch(BusinessException expected) { }
        System.out.println("PASS MaterialBatchSnapshotsTests (version ownership; no NC or Oracle)");
    }
    private static MaterialVO material(String source,boolean latest) {
        MaterialVO value=new MaterialVO();value.setPk_source(source);value.setLatest(latest?UFBoolean.TRUE:UFBoolean.FALSE);return value;
    }
}
