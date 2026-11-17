package nc.bs.uapbd.qd.esign.core;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import nc.vo.pub.BusinessException;
/** 电子签名通用值工具，统一 UUID32、UTC 时间、北京时间归档月、数据库行取值和业务前置条件检查。 */
public final class EsignValues {
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private EsignValues(){}
    /** 生成去除连字符的 UUID32，用作请求、对象和证据记录的标识。 */
    public static String id(){return UUID.randomUUID().toString().replace("-", "");}
    /** 以固定 UTC 时区格式化时间，避免默认时区影响持久化值。 */
    public static String utc(){return utc(System.currentTimeMillis());}
    /** 以固定 UTC 时区格式化时间，避免默认时区影响持久化值。 */
    public static String utc(long millis){return format("yyyy-MM-dd HH:mm:ss","UTC").format(new Date(millis));}
    /** 返回当前北京时间自然月，用作归档路由。 */
    public static String month(){return format("yyyyMM","Asia/Shanghai").format(new Date());}
    /** 把 UTC 时间解析为瞬时时间，再按北京时间计算归档月份，正确处理跨日和跨月边界。 */
    public static String monthForUtc(String utc) throws BusinessException{return format("yyyyMM","Asia/Shanghai").format(new Date(parseUtc(utc)));}
    /** 使用固定 UTC 时区及非宽松日期格式解析时间，解析失败转为 BusinessException。 */
    public static long parseUtc(String value) throws BusinessException {
        try{return format("yyyy-MM-dd HH:mm:ss","UTC").parse(value).getTime();}
        catch(ParseException e){throw new BusinessException("Invalid request time",e);}
    }
    /** 构造指定时区的独立日期格式器，关闭宽松解析，避免共享非线程安全格式器。 */
    private static SimpleDateFormat format(String pattern,String zone){SimpleDateFormat f=new SimpleDateFormat(pattern,java.util.Locale.ENGLISH);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone(zone));return f;}
    /** 从结果行读取可空文本，保留 null 而不转换为字符串形式的空值。 */
    public static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?null:String.valueOf(value);}
    /** 将结果行中的数值转换为 long，缺少值时返回零。 */
    public static long number(Map<String,Object> row,String key){Object v=row.get(key);return v==null?0:Long.parseLong(String.valueOf(v));}
    /** 只把明确的 Y 视为启用，其余值均不按真处理。 */
    public static boolean yes(Map<String,Object> row,String key){return "Y".equals(text(row,key));}
    /** 按交替字段名和值构造有序行映射，调用方需保证参数成对且字段名类型正确。 */
    public static Map<String,Object> row(Object... pairs){Map<String,Object> r=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
    /** 验证指定前置条件，不满足时立即抛出相应异常，阻止后续处理。 */
    public static void require(boolean ok,String message) throws BusinessException{if(!ok)throw new BusinessException(message);}
}
