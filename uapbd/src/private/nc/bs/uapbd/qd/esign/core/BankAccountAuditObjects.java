package nc.bs.uapbd.qd.esign.core;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.vo.pub.BusinessException;

/** One partner archive can have several currency relations; keep one log representative per archive. */
public final class BankAccountAuditObjects {
    private BankAccountAuditObjects() { }
    public static Set<String> representatives(List<Map<String,Object>> objects) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty(),"银行账户授权范围为空。");
        Map<String,Map<String,Object>> groups=new LinkedHashMap<String,Map<String,Object>>();Set<String> ids=new HashSet<String>();
        for(Map<String,Object> row:objects) {
            EsignValues.require(row!=null,"银行账户授权对象为空。");
            String id=EsignValues.text(row,"client_object_id"),type=EsignValues.text(row,"object_type"),pk=EsignValues.text(row,"object_pk"),
                root=EsignValues.text(row,"policy_owner_root");
            EsignValues.require(present(id) && ids.add(id) && present(pk) && present(root)
                && ("BD_CUSTOMER_BANK".equals(type) || "BD_SUPPLIER_BANK".equals(type)),"银行账户授权对象标识不正确。");
            String key=type+"\u0000"+root;Map<String,Object> previous=groups.get(key);
            if(previous==null) groups.put(key,row);
            else {
                for(String field:new String[]{"policy_owner_org","policy_owner_type","policy_scope_id","target_org","bill_code",
                        "owner_policy_version","owner_control_version","owner_sign_required","owner_reason_required","owner_audit_enabled"})
                    EsignValues.require(Objects.equals(EsignValues.text(previous,field),EsignValues.text(row,field)),
                        "同一银行账户档案的策略或授权信息不一致。");
                // Account saves can replace relation PKs; the prepared client identity remains stable.
                if(id.compareTo(EsignValues.text(previous,"client_object_id"))<0) groups.put(key,row);
            }
        }
        Set<String> result=new HashSet<String>();
        for(Map<String,Object> row:groups.values()) result.add(EsignValues.text(row,"client_object_id"));
        return result;
    }
    /** Older requests may have chosen a representative by relation PK before that PK was replaced. */
    public static void requireProofs(List<Map<String,Object>> objects,boolean signed,boolean committed,
            Set<String> signatureProofs,Set<String> auditProofs) throws BusinessException {
        representatives(objects); // Also validates each archive's shared owner and policy fields.
        EsignValues.require(signatureProofs!=null && auditProofs!=null,"银行账户证据集合缺失。");
        Map<String,Set<String>> groups=new LinkedHashMap<String,Set<String>>();
        Map<String,Map<String,Object>> sample=new LinkedHashMap<String,Map<String,Object>>();Set<String> objectIds=new HashSet<String>();
        for(Map<String,Object> row:objects) {
            String key=EsignValues.text(row,"object_type")+"\u0000"+EsignValues.text(row,"policy_owner_root");
            String id=EsignValues.text(row,"object_id");EsignValues.require(present(id) && objectIds.add(id),"银行账户证据对象重复或缺失。");
            if(!groups.containsKey(key)) { groups.put(key,new HashSet<String>());sample.put(key,row); }
            groups.get(key).add(id);
        }
        EsignValues.require(objectIds.containsAll(signatureProofs) && objectIds.containsAll(auditProofs),"银行账户证据不属于授权对象。");
        for(Map.Entry<String,Set<String>> entry:groups.entrySet()) {
            Map<String,Object> row=sample.get(entry.getKey());String sign=null,audit=null;
            for(String id:entry.getValue()) {
                if(signatureProofs.contains(id)) { EsignValues.require(sign==null,"银行账户档案签名代表重复。");sign=id; }
                if(auditProofs.contains(id)) { EsignValues.require(audit==null,"银行账户档案审计代表重复。");audit=id; }
            }
            boolean requireSign=signed && EsignValues.yes(row,"owner_sign_required");
            boolean requireAudit=committed && EsignValues.yes(row,"owner_audit_enabled");
            EsignValues.require((sign!=null)==requireSign && (audit!=null)==requireAudit
                && (!requireSign || !requireAudit || sign.equals(audit)),"银行账户档案签名与审计证据不一致。");
        }
    }
    private static boolean present(String text) { return text!=null && !text.trim().isEmpty(); }
}
