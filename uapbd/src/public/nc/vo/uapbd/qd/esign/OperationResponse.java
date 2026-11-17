package nc.vo.uapbd.qd.esign;
import java.io.Serializable;
import java.util.*;

/** 受控业务执行结果。重入成功请求时仅返回已保存的对象标识，不再次执行业务，也不重建原业务返回值。 */
public final class OperationResponse implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /** requestId：贯通准备、签名、执行及日志关联的请求标识。 */
    private final String requestId;
    /** replayed：是否为成功请求的结果重入，标记为真时不重新执行业务。 */
    private final boolean replayed;
    /** businessResult：原生业务返回值；结果重入时可能为空。 */
    private final Serializable businessResult;
    /** objects：本次授权或结果关联的业务对象集合。 */
    private final List<BusinessObjectRef> objects;
    /** 保存请求及业务结果，复制对象列表并包装为只读；replayed 为真时业务返回值可为空。 */
    public OperationResponse(String requestId,boolean replayed,Serializable result,List<BusinessObjectRef> objects){this.requestId=requestId;this.replayed=replayed;this.businessResult=result;this.objects=Collections.unmodifiableList(new ArrayList<BusinessObjectRef>(objects));}
    /** 返回贯通准备、签名、执行及日志关联的请求标识。 */
    public String getRequestId(){return requestId;}
    /** 返回是否为成功请求的结果重入，标记为真时不重新执行业务。 */
    public boolean isReplayed(){return replayed;}
    /** 返回原生业务返回值；结果重入时可能为空。 */
    public Serializable getBusinessResult(){return businessResult;}
    /** 返回本次授权或结果关联的业务对象集合。 */
    public List<BusinessObjectRef> getObjects(){return objects;}
}
