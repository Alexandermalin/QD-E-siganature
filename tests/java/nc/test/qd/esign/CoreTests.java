package nc.test.qd.esign;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import nc.bs.uapbd.qd.esign.audit.*;
import nc.bs.uapbd.qd.esign.core.RequestRules;
import nc.bs.uapbd.qd.esign.core.MappingRules;
import nc.bs.uapbd.qd.esign.core.ObjectRelations;
import nc.bs.uapbd.qd.esign.core.ReasonRules;
import nc.bs.uapbd.qd.esign.core.DatabaseTextRules;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.*;
import nc.vo.uapbd.qd.esign.audit.*;
/** 核心纯算法与 DTO 的离线断言入口，覆盖状态规则、事由边界、快照差异、对象关联和敏感字段限制。 */
public final class CoreTests {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;
    /** 验证策略组合、对象关系、动作域、事由字节边界、快照及线程隔离；匿名 Runnable 中的同名方法只执行并发隔离测试任务。 */
    public static void run() throws Exception {
        assertions=0;
        // 枚举签名与审计组合，验证集团总控和事由必填的生效关系，同时保留原始配置供管理展示。
        for(boolean sign:new boolean[]{false,true})for(boolean audit:new boolean[]{false,true}){
            PolicyDecision p=new PolicyDecision(true,sign,true,audit,true,1);check(p.isSignatureRequired()==sign&&p.isAuditEnabled()==audit,"Independent policy switches");
            PolicyDecision off=new PolicyDecision(false,sign,true,audit,true,1);check(!off.isControlled()&&!off.isReasonRequired(),"Master switch");
        }
        check(!new PolicyDecision(true,false,true,true,true,1).isReasonRequired(),"No mandatory signature reason without signing");
        PolicyDecision retained=new PolicyDecision(false,true,true,true,true,2);check(retained.getConfiguredSignature()&&retained.getConfiguredReason()&&retained.getConfiguredAudit(),"Master off must retain configured switches for management");
        check(RequestRules.mayExecute("SUCCESS","READY"),"Signed ready request");check(RequestRules.mayExecute("NOT_REQUIRED","READY"),"Audit-only request");
        check(!RequestRules.mayExecute("SUCCESS","SUCCESS")&&!RequestRules.mayExecute("FAILED","READY"),"No failed/replayed execution");
        // 检查上下文副本、主键唯一、父子无环及映射动作域，防止签名范围被外部修改或换用。
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();refs.add(new BusinessObjectRef("tmp1","TYPE",null,null,null));
        OperationContext c=new OperationContext(null,null,null,"org","node","Node","save","Save","INSERT",null,refs);refs.clear();check(c.getObjects().size()==1,"Context owns defensive selection copy");
        try{c.getObjects().clear();throw new AssertionError("Mutable context");}catch(UnsupportedOperationException expected){assertions++;}
        try{new OperationContext(null,null,null,"org","node","Node","save","Save","UPDATE",null,Arrays.asList(new BusinessObjectRef("a","T","pk",null,"a")));throw new AssertionError("Self-cycle accepted");}catch(IllegalArgumentException expected){assertions++;}
        try{new OperationContext(null,null,null,"org","node","Node","save","Save","UPDATE",null,Arrays.asList(new BusinessObjectRef("a","T","pk",null,null),new BusinessObjectRef("b","T","pk",null,null)));throw new AssertionError("Duplicate object key accepted");}catch(IllegalArgumentException expected){assertions++;}
        Map<String,Object> domain=EsignValues.row("validated","Y","operation_types","INSERT,UPDATE","object_type","TYPE");
        MappingRules.requireDomain(domain,"INSERT",c.getObjects());assertions++;
        try{MappingRules.requireDomain(domain,"DELETE",c.getObjects());throw new AssertionError("SAVE authorization used for DELETE");}catch(BusinessException expected){assertions++;}
        try{MappingRules.requireDomain(domain,"INSERT",Arrays.asList(new BusinessObjectRef("a","OTHER",null,null,null)));throw new AssertionError("Object type substitution accepted");}catch(BusinessException expected){assertions++;}
        List<Map<String,Object>> stored=Arrays.asList(EsignValues.row("object_id","db-parent","client_object_id","parent","object_type","T","object_pk","p"),EsignValues.row("object_id","db-child","client_object_id","child","object_type","T","object_pk","c","parent_object_id","db-parent"));
        check("parent".equals(ObjectRelations.restore(stored).get(1).getParentClientObjectId()),"Replay preserves persisted parent-child relations");
        // 中文与补充平面字符分别验证字节上限和代理对截断，取消也必须产生可落库文本。
        char[] chinese=new char[1400];Arrays.fill(chinese,'中');String longReason=new String(chinese);
        try{ReasonRules.validate(longReason);throw new AssertionError("Oversize UTF-8 reason accepted");}catch(BusinessException expected){assertions++;}
        String cancelled=ReasonRules.cancellation(longReason);ReasonRules.validate(cancelled);check(cancelled.endsWith("[truncated]"),"Cancellation still records bounded, visibly truncated reason");
        StringBuilder emoji=new StringBuilder();for(int n=0;n<1100;n++)emoji.append("\uD83D\uDE00");String shortEmoji=ReasonRules.cancellation(emoji.toString());ReasonRules.validate(shortEmoji);check(!Character.isHighSurrogate(shortEmoji.charAt(shortEmoji.length()-1)),"Cancellation preserves surrogate pairs");
        java.nio.charset.Charset gbk=DatabaseTextRules.oracleCharset("ZHS16GBK"),utf8=DatabaseTextRules.oracleCharset("AL32UTF8");
        check(DatabaseTextRules.lossless("电子签名测试",gbk)&&"电子签名测试".getBytes(gbk).length==12,"Site GBK encodes Chinese losslessly");
        check(!DatabaseTextRules.lossless("\uD83D\uDE00",gbk)&&DatabaseTextRules.lossless("\uD83D\uDE00",utf8),"GBK must reject supplementary characters instead of substituting question marks");
        check(!DatabaseTextRules.lossless("\uD800",utf8),"Malformed surrogate must not be silently replaced even on UTF8");
        String escaped=DatabaseTextRules.cancellation("取消\uD83D\uDE00",gbk);
        check(escaped.contains("\\uD83D\\uDE00")&&escaped.endsWith("[escaped for database]")&&DatabaseTextRules.lossless(escaped,gbk),"Cancelled unsupported text has explicit Unicode evidence");
        check("正常取消".equals(DatabaseTextRules.cancellation("正常取消",gbk)),"Representable cancellation is unchanged");
        String bounded=DatabaseTextRules.cancellation(emoji.toString(),gbk);ReasonRules.validate(bounded);
        check(DatabaseTextRules.lossless(bounded,gbk)&&bounded.endsWith("[truncated]"),"Escaped cancellation remains bounded and visibly truncated");
        try{DatabaseTextRules.oracleCharset("UNREVIEWED_ENCODING");throw new AssertionError("Unknown Oracle charset guessed");}catch(IllegalArgumentException expected){assertions++;}
        check("1".equals(SnapshotSupport.normalize(new BigDecimal("1.000"))),"Decimal scale is not a business change");
        check(SnapshotSupport.normalize(null)==null,"Null preserved");
        check(SnapshotSupport.sensitive("user_password")&&SnapshotSupport.sensitive("access_token")&&!SnapshotSupport.sensitive("author"),"Sensitive fields excluded without excluding author");
        check(SnapshotSupport.sensitive("api_key")&&SnapshotSupport.sensitive("access_key")&&!SnapshotSupport.sensitive("primary_key_id"),"Keys excluded without dropping business identifiers");
        // 按稳定行标识验证字段差异、新增删除、行重排及父关系变化，而不是按数组位置比较。
        SnapshotRow old=row("a","p","qty","1"),same=row("a","p","qty","1"),changed=row("a","p","qty","2");
        check(SnapshotSupport.diff(Arrays.asList(old),Arrays.asList(same)).isEmpty(),"No-change save");
        List<FieldChange> changes=SnapshotSupport.diff(Arrays.asList(old),Arrays.asList(changed));check(changes.size()==1&&"1".equals(changes.get(0).getBeforeValue())&&"2".equals(changes.get(0).getAfterValue()),"Before/after values");
        check("DELETE".equals(SnapshotSupport.diff(Arrays.asList(old),Collections.<SnapshotRow>emptyList()).get(0).getChangeType()),"Deleted row retained");
        check("INSERT".equals(SnapshotSupport.diff(Collections.<SnapshotRow>emptyList(),Arrays.asList(old)).get(0).getChangeType()),"New row retained");
        check(SnapshotSupport.diff(Arrays.asList(old,row("b","p","qty","3")),Arrays.asList(row("b","p","qty","3"),same)).isEmpty(),"Body row reorder ignored");
        check("__parent__".equals(SnapshotSupport.diff(Arrays.asList(old),Arrays.asList(row("a","other","qty","1"))).get(0).getFieldCode()),"Parent relation change");
        try{SnapshotSupport.diff(Arrays.asList(old,old),Arrays.asList(same));throw new AssertionError("Duplicate rows accepted");}catch(IllegalArgumentException expected){assertions++;}
        String json=SnapshotSupport.json(row("a","p","text","中文\n\"\\"));check(json.contains("\\u000a")&&json.contains("\\\"")&&json.contains("\\\\"),"Canonical escaped snapshot");
        check(SnapshotSupport.sha256(json).length()==64&&SnapshotSupport.sha256(json).equals(SnapshotSupport.sha256(json)),"Deterministic snapshot digest");
        check("202610".equals(EsignValues.monthForUtc("2026-10-31 15:59:59"))&&"202611".equals(EsignValues.monthForUtc("2026-10-31 16:00:00")),"Archive month derives from the same persisted event instant");
        // 分别验证嵌套恢复、非法快照拒绝和并发线程隔离，结束后不能残留线程作用域。
        AuditCaptureScope outer=new AuditCaptureScope();try{AuditCaptureScope nested=new AuditCaptureScope();try{check(AuditCaptureScope.current()==nested,"Nested request scope");}finally{nested.close();}check(AuditCaptureScope.current()==outer,"Outer scope restored");}finally{outer.close();}check(AuditCaptureScope.current()==null,"Scope removed after request");
        AuditCaptureScope guarded=new AuditCaptureScope(c,true);try{
            try{guarded.before("tmp1",Collections.<SnapshotRow>emptyList());throw new AssertionError("Empty snapshot accepted");}catch(IllegalArgumentException expected){assertions++;}
            try{guarded.before("other",Arrays.asList(old));throw new AssertionError("Unselected snapshot accepted");}catch(IllegalArgumentException expected){assertions++;}
        }finally{guarded.close();}
        final CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);final AtomicReference<Throwable> error=new AtomicReference<Throwable>();
        Runnable task=new Runnable(){
        /** 验证策略组合、对象关系、动作域、事由字节边界、快照及线程隔离；匿名 Runnable 中的同名方法只执行并发隔离测试任务。 */
        public void run(){AuditCaptureScope scope=new AuditCaptureScope();try{entered.countDown();release.await();if(AuditCaptureScope.current()!=scope)throw new AssertionError("Request context leaked across threads");}catch(Throwable t){error.set(t);}finally{scope.close();}}};
        Thread a=new Thread(task),b=new Thread(task);a.start();b.start();entered.await();release.countDown();a.join();b.join();check(error.get()==null&&AuditCaptureScope.current()==null,"Concurrent requests isolated");
        System.out.println("PASS CoreTests assertions="+assertions);
    }
    /** 构造具有实体、稳定行标识、父行及一个字段的最小快照，供差异、转义和父子关系测试复用。 */
    private static SnapshotRow row(String id,String parent,String field,String value){Map<String,String> values=new HashMap<String,String>(),types=new HashMap<String,String>(),labels=new HashMap<String,String>();values.put(field,value);types.put(field,"STRING");labels.put(field,field);return new SnapshotRow("body",id,parent,values,types,labels);}
    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);assertions++;}
}
