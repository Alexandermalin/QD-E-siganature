package nc.vo.ic.qd.esign;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import nc.vo.ic.m4455.entity.SapplyBillVO;
import nc.vo.ic.pub.pf.ICPFParameter;
import nc.vo.pub.BusinessException;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.pub.SuperVO;
import nc.vo.pub.workflownote.WorkflownoteVO;
import nc.vo.pub.pf.WfTaskInfo;
import nc.vo.pubapp.pflow.PfUserObject;

/** Canonical approval intent, independent of NC VO/HashMap serialization implementation details. */
public final class SapplyApprovalIntent {
    private static final int MAX_BYTES=8*1024*1024,MAX_ITEMS=100000,MAX_DEPTH=20;
    private static final String PREFIX="sapply-approval:";
    private SapplyApprovalIntent() { }

    public static String clientObjectId(String button,InventoryPfStateCommand command) throws BusinessException {
        if((!"Approve".equals(button) && !"UnApprove".equals(button)) || command==null)
            throw new BusinessException("出库申请审批授权参数不完整。");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            Writer writer=new Writer(new DataOutputStream(new DigestOutputStream(new BoundedSink(),digest)));
            writer.value("QD-SAPPLY-APPROVAL-3",0);writer.value(button,0);writer.value(command.getBillType(),0);
            writer.bill(command.getBill());writer.value(command.getUserObject(),0);writer.value(command.getWorkflowNote(),0);
            writer.value(command.getParameters(),0);writer.out.close();
            return PREFIX+hex(digest.digest());
        } catch(Exception failure){throw new BusinessException("出库申请审批内容无法安全绑定，请检查单据与原生流程参数。",failure);}
    }

    /** Hash the common approval confirmation once for a multi-bill request. */
    public static String batchSharedFingerprint(WorkflownoteVO note,Map<String,Object> parameters) throws BusinessException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            Writer writer=new Writer(new DataOutputStream(new DigestOutputStream(new BoundedSink(),digest)));
            writer.value("QD-SAPPLY-BATCH-APPROVAL-COMMON-1",0);
            writer.value(note,0);writer.value(parameters,0);writer.out.close();return hex(digest.digest());
        } catch(Exception failure){throw new BusinessException("出库申请批量审批确认无法安全绑定。",failure);}
    }

    /** Bind the exact precheck-excluded bills to the approval confirmation. */
    public static String batchSharedFingerprint(WorkflownoteVO note,Map<String,Object> parameters,String[] skippedPks)
            throws BusinessException {
        if(skippedPks==null || skippedPks.length==0)return batchSharedFingerprint(note,parameters);
        if(skippedPks.length>1000)throw new BusinessException("出库申请批量预校验范围过大。");
        String[] sorted=skippedPks.clone();
        for(String pk:sorted)if(pk==null || !pk.matches("[A-Za-z0-9_-]{1,20}"))
            throw new BusinessException("出库申请批量预校验单据主键无效。");
        java.util.Arrays.sort(sorted);
        for(int i=1;i<sorted.length;i++)if(sorted[i].equals(sorted[i-1]))
            throw new BusinessException("出库申请批量预校验单据重复。");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            Writer writer=new Writer(new DataOutputStream(new DigestOutputStream(new BoundedSink(),digest)));
            writer.value("QD-SAPPLY-BATCH-APPROVAL-COMMON-2",0);
            writer.value(note,0);writer.value(parameters,0);writer.value(sorted,0);
            writer.out.close();return hex(digest.digest());
        } catch(Exception failure){throw new BusinessException("出库申请批量审批确认无法安全绑定。",failure);}
    }

    public static String batchClientObjectId(String button,String billType,SapplyBillVO bill,PfUserObject user,
            String sharedFingerprint) throws BusinessException {
        if((!"Approve".equals(button) && !"UnApprove".equals(button)) || sharedFingerprint==null
                || !sharedFingerprint.matches("[0-9A-F]{64}"))
            throw new BusinessException("出库申请批量审批授权参数不完整。");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            Writer writer=new Writer(new DataOutputStream(new DigestOutputStream(new BoundedSink(),digest)));
            writer.value("QD-SAPPLY-BATCH-APPROVAL-BILL-1",0);writer.value(button,0);writer.value(billType,0);
            writer.bill(bill);writer.value(user,0);writer.value(sharedFingerprint,0);writer.out.close();
            return PREFIX+hex(digest.digest());
        } catch(Exception failure){throw new BusinessException("出库申请批量单据无法安全绑定。",failure);}
    }

    /** Identifies the user's selected routes without triggering NC's lazy vector getters. */
    public static String workflowSelectionFingerprint(WorkflownoteVO note) throws BusinessException {
        try {
            Object info=workflowTaskInfo(note);Object selection=null;
            if(info!=null){
                if(!"nc.vo.pub.pf.WfTaskInfo".equals(info.getClass().getName()))
                    throw new IOException("Unsupported workflow task-info subtype");
                Field field=info.getClass().getDeclaredField("transitionSelectableInfos");field.setAccessible(true);
                selection=field.get(info);
            }
            if(selection==null)selection=Collections.emptyList();
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            Writer writer=new Writer(new DataOutputStream(new DigestOutputStream(new BoundedSink(),digest)));
            writer.value(selection,0);writer.out.close();return hex(digest.digest());
        } catch(Exception failure){throw new BusinessException("出库申请审批流转选择无法安全绑定。",failure);}
    }

    /** WorkflownoteVO.getTaskInfo() creates a value when absent, which would change the signed payload. */
    public static WfTaskInfo workflowTaskInfo(WorkflownoteVO note) throws BusinessException {
        if(note==null)return null;
        try {
            if(note.getClass()!=WorkflownoteVO.class)throw new IOException("Unsupported workflow-note subtype");
            Field field=WorkflownoteVO.class.getDeclaredField("taskInfo");field.setAccessible(true);
            return (WfTaskInfo)field.get(note);
        } catch(Exception failure){throw new BusinessException("出库申请审批工作项无法安全读取。",failure);}
    }

    /** NC returns directly after these workflow adjustments instead of dispatching approval. */
    public static boolean isWorkflowAdjustment(WorkflownoteVO note) throws BusinessException {
        WfTaskInfo info=workflowTaskInfo(note);
        nc.vo.wfengine.pub.WFTask task=info==null?null:info.getTask();
        return task!=null && (task.isAddApprover() || task.isAppoint());
    }

    private static String hex(byte[] bytes) {
        char[] chars="0123456789ABCDEF".toCharArray();StringBuilder result=new StringBuilder();
        for(byte b:bytes){result.append(chars[(b>>>4)&15]);result.append(chars[b&15]);}
        return result.toString();
    }

    private static final class Writer {
        final DataOutputStream out;final IdentityHashMap<Object,Integer> visiting=new IdentityHashMap<Object,Integer>();int count;
        Writer(DataOutputStream out){this.out=out;}
        void bill(Object input) throws Exception {
            if(input==null){tag("null-bill");return;}
            if(!(input instanceof SapplyBillVO))throw new IOException("Unsupported outbound request bill");
            SapplyBillVO bill=(SapplyBillVO)input;tag("sapply-bill");tag(bill.getClass().getName());
            nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta meta=bill.getMetaData();
            if(meta==null || meta.getChildren()!=null && meta.getChildren().length>1)
                throw new IOException("Outbound request has unverified child tables");
            // Include actual inherited parent/children and pk_billtemp, not only overridable getters.
            for(Class<?> type=bill.getClass();type!=nc.vo.pub.AggregatedValueObject.class;type=type.getSuperclass())fields(bill,type,0);
            value(bill.getHead(),0);
            CircularlyAccessibleValueObject[] children=bill.getAllChildrenVO();
            // Some native light VOs expose their sole body table through getBodys instead.
            if(children==null)children=bill.getBodys();
            value(children,0);
        }
        void value(Object value,int depth) throws Exception {
            if(depth>MAX_DEPTH || ++count>MAX_ITEMS)throw new IOException("Approval intent is too complex");
            if(value==null){tag("null");return;}
            Class<?> type=value.getClass();
            String name=type.getName();
            if(type==Float.class){tag(name);out.writeInt(Float.floatToRawIntBits(((Float)value).floatValue()));return;}
            if(type==Double.class){tag(name);out.writeLong(Double.doubleToRawLongBits(((Double)value).doubleValue()));return;}
            if(value instanceof Enum<?>){Enum<?> item=(Enum<?>)value;tag(item.getDeclaringClass().getName());tag(item.name());return;}
            if(type==String.class || type==Boolean.class || type==Character.class || type==Byte.class
                    || type==Short.class || type==Integer.class || type==Long.class
                    || type==java.math.BigDecimal.class || type==java.math.BigInteger.class) {
                tag(type.getName());tag(value.toString());return;
            }
            Integer ancestor=visiting.get(value);
            if(ancestor!=null){tag("ancestor-reference");out.writeInt(ancestor.intValue());return;}
            visiting.put(value,Integer.valueOf(depth));
            try {
                if("nc.vo.pub.lang.UFBoolean".equals(name) || "nc.vo.pub.lang.UFDate".equals(name)
                        || "nc.vo.pub.lang.UFDateTime".equals(name) || "nc.vo.pub.lang.UFDouble".equals(name)
                        || "nc.vo.pub.lang.UFTime".equals(name)) {
                    tag("nc-value");fields(value,type,depth);return;
                }
                if(value instanceof WorkflownoteVO) {
                    if(type!=WorkflownoteVO.class)throw new IOException("Unsupported workflow-note subtype");
                    WorkflownoteVO note=(WorkflownoteVO)value;tag("workflow-note");
                    value(Integer.valueOf(note.getStatus()),depth+1);value(Boolean.valueOf(note.isDirty()),depth+1);
                    fields(note,WorkflownoteVO.class,depth);return;
                }
                if(value instanceof PfUserObject) {
                    if(type!=PfUserObject.class && type!=ICPFParameter.class)throw new IOException("Unsupported PF parameter subtype");
                    tag("pf-user-object");tag(type.getName());
                    for(Class<?> c=type;c!=null && c!=Object.class;c=c.getSuperclass())fields(value,c,depth);
                    return;
                }
                if(isWorkflowValue(name)) {
                    // NC workflow payloads contain task identifiers, routes and approvers.
                    // Bind every instance field, including inherited fields, rather than only a display summary.
                    tag("workflow-value");tag(name);
                    for(Class<?> c=type;c!=null && c!=Object.class;c=c.getSuperclass())fields(value,c,depth);
                    return;
                }
                if(value instanceof nc.vo.pub.AggregatedValueObject) {
                    // A workflow task may carry the original bill; other aggregate types remain unverified.
                    bill(value);return;
                }
                if(value instanceof SuperVO) {
                    SuperVO vo=(SuperVO)value;tag("super-vo");tag(type.getName());
                    value(Integer.valueOf(vo.getStatus()),depth+1);value(Boolean.valueOf(vo.isDirty()),depth+1);
                    for(Class<?> c=type;c!=SuperVO.class;c=c.getSuperclass())fields(vo,c,depth);
                    List<String> names=new ArrayList<String>(vo.usedAttributeNames());Collections.sort(names);
                    out.writeInt(names.size());for(String attribute:names){tag(attribute);value(vo.getAttributeValue(attribute),depth+1);}
                    return;
                }
                if(value instanceof Map<?,?>) {
                    TreeMap<String,Object> sorted=new TreeMap<String,Object>();
                    for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                        if(!(entry.getKey() instanceof String))throw new IOException("Unsupported map key");
                        sorted.put((String)entry.getKey(),entry.getValue());
                    }
                    tag("map");out.writeInt(sorted.size());for(Map.Entry<String,Object> entry:sorted.entrySet()){
                        tag(entry.getKey());value(entry.getValue(),depth+1);
                    }return;
                }
                if(value instanceof List<?>) {
                    tag("list");List<?> list=(List<?>)value;out.writeInt(list.size());
                    for(Object item:list)value(item,depth+1);return;
                }
                if(type.isArray()) {
                    tag("array");tag(type.getComponentType().getName());int n=Array.getLength(value);out.writeInt(n);
                    for(int i=0;i<n;i++)value(Array.get(value,i),depth+1);return;
                }
                throw new IOException("Unsupported approval value: "+type.getName());
            } finally{visiting.remove(value);}
        }
        private boolean isWorkflowValue(String name) {
            return "nc.vo.pub.pf.WfTaskInfo".equals(name) || "nc.vo.wfengine.pub.WFTask".equals(name)
                    || "nc.vo.pub.pf.AssignableInfo".equals(name)
                    || "nc.vo.pub.pf.TransitionSelectableInfo".equals(name);
        }
        void fields(Object target,Class<?> type,int depth) throws Exception {
            List<Field> fields=new ArrayList<Field>();for(Field field:type.getDeclaredFields())
                if(!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers()))fields.add(field);
            Collections.sort(fields,new java.util.Comparator<Field>(){public int compare(Field a,Field b){return a.getName().compareTo(b.getName());}});
            tag(type.getName());out.writeInt(fields.size());for(Field field:fields){field.setAccessible(true);tag(field.getName());value(field.get(target),depth+1);}
        }
        void tag(String value) throws IOException {
            out.writeInt(value.length());for(int i=0;i<value.length();i++)out.writeChar(value.charAt(i));
        }
    }
    private static final class BoundedSink extends OutputStream {
        private int size;public void write(int value) throws IOException {add(1);}
        public void write(byte[] value,int offset,int length) throws IOException {add(length);}
        private void add(int amount) throws IOException {if(amount<0 || amount>MAX_BYTES-size)throw new IOException("Approval intent too large");size+=amount;}
    }
}
