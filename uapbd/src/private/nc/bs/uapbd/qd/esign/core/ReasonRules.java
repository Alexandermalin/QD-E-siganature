package nc.bs.uapbd.qd.esign.core;
import java.nio.charset.Charset;
import nc.vo.pub.BusinessException;
/** 操作事由的字符数和 UTF-8 字节数边界校验，兼顾中文长度与 Oracle 字节列上限；取消时提供可落库的截断文本。 */
public final class ReasonRules {
    /** UTF8：用于计算事由实际落库字节长度的 UTF-8 字符集。 */
    private static final Charset UTF8=Charset.forName("UTF-8");
    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private ReasonRules(){}
    /** 允许空事由，其余值要求不超过 2000 个 UTF-16 代码单元且不超过 4000 个 UTF-8 字节，避免超出数据库列的字节上限。 */
    public static void validate(String reason) throws BusinessException {
        EsignValues.require(reason==null||(reason.length()<=2000&&reason.getBytes(UTF8).length<=4000),"Operation reason must fit 2000 characters and 4000 UTF-8 bytes");
    }

    /** 将未完成的事由输入转为可记录的取消原因，避免超长文本导致取消本身无法留痕。 */
    public static String cancellation(String reason){
        if(reason==null)return null;
        if(reason.length()<=2000&&reason.getBytes(UTF8).length<=4000)return reason;
        String suffix=" [truncated]";int end=Math.min(reason.length(),2000-suffix.length());
        if(end>0&&Character.isHighSurrogate(reason.charAt(end-1)))end--;
        while(end>0&&(reason.substring(0,end)+suffix).getBytes(UTF8).length>4000)end-=Character.charCount(reason.codePointBefore(end));
        return reason.substring(0,end)+suffix;
    }
}
