package nc.bs.uapbd.qd.esign.audit;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.uapbd.qd.esign.audit.FieldChange;
import nc.vo.uapbd.qd.esign.audit.SnapshotPayload;

/** 显式字段白名单驱动的快照采集与差异计算工具，不反射导出全部 VO 或数据库属性；拒绝敏感凭据字段。 */
public final class SnapshotSupport {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private SnapshotSupport(){}
    /** 按规范化字段名识别密码、令牌、密钥等敏感字段，防止进入审计白名单。 */
    public static boolean sensitive(String field){String n=field.toLowerCase(Locale.ENGLISH).replace("_","");return n.contains("password")||n.contains("passwd")||n.contains("pwd")||n.contains("passcode")||n.contains("secret")||n.contains("token")||n.contains("privatekey")||n.contains("credential")||n.contains("apikey")||n.contains("accesskey")||n.contains("signingkey")||n.contains("encryptionkey")||n.contains("sharedkey")||"key".equals(n);}
    /** 把字段值转换为稳定文本：数值去除无意义尾零、日期转 UTC；拒绝二进制、字符数组及非有限数值。 */
    public static String normalize(Object value){
        if(value==null)return null;
        if(value instanceof Number){try{return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();}catch(NumberFormatException e){throw new IllegalArgumentException("Non-finite numeric snapshot value",e);}}
        if(value instanceof Date)return EsignValues.utc(((Date)value).getTime());
        if(value instanceof byte[]||value instanceof char[])throw new IllegalArgumentException("Binary/credential fields cannot be audited");
        return value.toString();
    }
    /** 仅遍历显式允许的字段，从真实 VO 读取值、类型及标签；敏感字段即使列在白名单中也拒绝采集。 */
    public static SnapshotRow collect(SuperVO vo,String entity,String stableId,String parent,Map<String,String> allowed){
        Map<String,String> values=new TreeMap<String,String>(),types=new TreeMap<String,String>();
        for(String field:allowed.keySet()){
            if(sensitive(field))throw new IllegalArgumentException("Sensitive audit field rejected: "+field);
            Object v=vo.getAttributeValue(field);values.put(field,normalize(v));types.put(field,v==null?"NULL":v.getClass().getName());
        }
        return new SnapshotRow(entity,stableId,parent,values,types,allowed);
    }
    /** 按实体与稳定行标识对齐前后快照，计算新增、删除、字段修改及父行关系变化，保留字段缺失与 null 的区别。 */
    public static List<FieldChange> diff(List<SnapshotRow> before,List<SnapshotRow> after){
        // 实体名与稳定行标识共同定位一行，不能按数组位置比较主子表。
        Map<String,SnapshotRow> old=index(before),now=index(after);Set<String> keys=new TreeSet<String>();keys.addAll(old.keySet());keys.addAll(now.keySet());
        List<FieldChange> changes=new ArrayList<FieldChange>();
        for(String key:keys){SnapshotRow a=old.get(key),b=now.get(key),row=b==null?a:b;Set<String> fields=new TreeSet<String>();
            if(a!=null)fields.addAll(a.getValues().keySet());if(b!=null)fields.addAll(b.getValues().keySet());
            for(String f:fields){String x=a==null?null:a.getValues().get(f),y=b==null?null:b.getValues().get(f);
                if(a==null||b==null||!equal(x,y)||a.getValues().containsKey(f)!=b.getValues().containsKey(f))changes.add(new FieldChange(row,f,a==null?"INSERT":b==null?"DELETE":"UPDATE",x,y));}
            // 父子关系变化单独形成差异，即使普通字段未改也需要留下证据。
            if(a!=null&&b!=null&&!equal(a.getParentRowId(),b.getParentRowId()))changes.add(new FieldChange(row,"__parent__","UPDATE",a.getParentRowId(),b.getParentRowId()));
        }return changes;
    }
    /** 执行可处理 null 的值比较，避免将未提供值误作普通对象比较。 */
    private static boolean equal(Object a,Object b){return a==null?b==null:a.equals(b);}
    /** 按实体及稳定行标识建立快照索引，重复行标识明确失败，避免覆盖前后比较依据。 */
    private static Map<String,SnapshotRow> index(List<SnapshotRow> rows){Map<String,SnapshotRow> index=new TreeMap<String,SnapshotRow>();for(SnapshotRow row:rows){String id=row.getEntity()+"\u0000"+row.getRowId();if(index.put(id,row)!=null)throw new IllegalArgumentException("Duplicate snapshot row: "+row.getRowId());}return index;}
    /** 按快照字段的稳定顺序生成 JSON，保留 null 并对文本进行必要转义。 */
    public static String json(SnapshotRow row){return SnapshotPayload.encode(row);}
    /** 将实体、行标识和父行关系与物理内容一起绑定，避免不同表的相同行值互换后摘要不变。 */
    public static String rowFingerprint(SnapshotRow row){
        if(row==null)throw new IllegalArgumentException("Snapshot row is required");
        StringBuilder value=new StringBuilder("snapshot-row-v1");
        for(String item:new String[]{row.getEntity(),row.getRowId(),row.getParentRowId(),json(row)})
            value.append(item==null?-1:item.length()).append(':').append(item==null?"":item);
        return sha256(value.toString());
    }
    /** 对 UTF-8 快照文本计算 SHA-256 十六进制摘要，用于核验内容一致性，不代表电子证书签名。 */
    public static String sha256(String text){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(text.getBytes(Charset.forName("UTF-8")));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(Locale.ENGLISH,"%02x",b&255));return s.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
