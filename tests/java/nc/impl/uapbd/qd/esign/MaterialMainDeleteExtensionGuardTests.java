package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.BusinessException;

/** The server-side extension fence rejects unknown client configuration and plugins. */
public final class MaterialMainDeleteExtensionGuardTests {
    private MaterialMainDeleteExtensionGuardTests() { }
    public static void run() throws Exception {
        String expected=MaterialMainDeleteExtensionGuard.expectedCandidate("10140MAG");
        List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        rows.add(EsignValues.row("paramname","BeanConfigFilePath","paramvalue",expected));
        MaterialMainDeleteExtensionGuard.requireParameters(expected,rows);
        rows.add(EsignValues.row("paramname","PluginBeanConfigFilePath1","paramvalue","nc/custom.xml"));
        reject(expected,rows,"Unverified plugin accepted");
        rows.remove(1);
        rows.add(EsignValues.row("paramname","BeanConfigFilePath","paramvalue",expected));
        reject(expected,rows,"Ambiguous node configuration accepted");
        rows.clear();rows.add(EsignValues.row("paramname","BeanConfigFilePath","paramvalue","nc/custom.xml"));
        reject(expected,rows,"Custom material configuration accepted");
        rows.clear();reject(expected,rows,"Missing node configuration accepted");
        MaterialMainDeleteExtensionGuard.verifyResources("10140MAG",Thread.currentThread().getContextClassLoader());
        MaterialMainDeleteExtensionGuard.verifyResources("10140MAO",Thread.currentThread().getContextClassLoader());
        System.out.println("PASS MaterialMainDeleteExtensionGuardTests (configured paths and plugin fail-closed; no NC or Oracle)");
    }
    private static void reject(String expected,List<Map<String,Object>> rows,String message) throws Exception {
        try { MaterialMainDeleteExtensionGuard.requireParameters(expected,rows);throw new AssertionError(message); }
        catch(BusinessException required) { }
    }
}
