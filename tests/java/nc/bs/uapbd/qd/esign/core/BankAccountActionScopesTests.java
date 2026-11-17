package nc.bs.uapbd.qd.esign.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;

/** Rejects drift between the account policy mapping and the project's formal operation catalog. */
public final class BankAccountActionScopesTests {
    private BankAccountActionScopesTests() { }
    public static void run() throws Exception {
        List<String> rows=Files.readAllLines(Paths.get("config/qd-esign/operation-scope.csv"),StandardCharsets.UTF_8);
        Map<String,String> formal=new HashMap<String,String>();
        for(String row:rows) {
            String[] fields=row.split(",",-1);
            if(fields.length>=8 && "基础数据".equals(fields[5]) && fields[7].startsWith("银行账户-"))
                formal.put(fields[6]+"\u0000"+fields[7],fields[3]);
        }
        check(formal,"客户","BD_CUSTOMER_BANK");check(formal,"供应商","BD_SUPPLIER_BANK");
        reject("BD_CUSTOMER_BANK","APPROVE");reject("BD_CUSTOMER","ENABLE");
        System.out.println("PASS BankAccountActionScopesTests (formal customer/supplier actions; no NC or Oracle)");
    }
    private static void check(Map<String,String> formal,String name,String domain) throws Exception {
        String[][] operations={{"保存","INSERT"},{"保存","UPDATE"},{"删除","DELETE"},{"停用","DISABLE"},{"启用","ENABLE"}};
        for(String[] pair:operations) if(!BankAccountActionScopes.scopeFor(domain,pair[1]).equals(formal.get(name+"\u0000银行账户-"+pair[0])))
            throw new AssertionError("Formal bank account action changed: "+name+" "+pair[0]);
    }
    private static void reject(String domain,String operation) throws Exception {
        try { BankAccountActionScopes.scopeFor(domain,operation);throw new AssertionError("Unknown bank scope accepted"); }
        catch(BusinessException expected) { }
    }
}
