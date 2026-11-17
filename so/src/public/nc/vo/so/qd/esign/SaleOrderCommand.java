package nc.vo.so.qd.esign;
import java.io.Serializable;
import nc.vo.so.m30.entity.SaleOrderVO;
import nc.vo.pubapp.pflow.PfUserObject;
/** 销售订单原生服务的类型化命令，保存待处理单据、原始单据及流程用户对象；数组结构使用副本隔离。 */
public final class SaleOrderCommand implements Serializable {
    /** serialVersionUID：Java 序列化版本号，保持跨 RPC 或 Swing 组件序列化的兼容标识。 */
    private static final long serialVersionUID=1L;
    /**
     * bills：传给原生业务服务的待处理单据数组。
     * originalBills：原生更新所需的原始单据数组。
     */
    private final SaleOrderVO[] bills,originalBills;
    /** userObject：销售流程服务所需的原生用户对象。 */
    private final PfUserObject userObject;
    /** 复制当前及原始销售订单数组，保留原生流程用户对象；VO 内部业务字段仍由原生服务处理。 */
    public SaleOrderCommand(SaleOrderVO[] bills,SaleOrderVO[] originalBills,PfUserObject userObject){this.bills=bills==null?null:bills.clone();this.originalBills=originalBills==null?null:originalBills.clone();this.userObject=userObject;}
    /** 返回传给原生业务服务的待处理单据数组。 */
    public SaleOrderVO[] getBills(){return bills==null?null:bills.clone();}
    /** 返回原生更新所需的原始单据数组。 */
    public SaleOrderVO[] getOriginalBills(){return originalBills==null?null:originalBills.clone();}
    /** 返回销售流程服务所需的原生用户对象。 */
    public PfUserObject getUserObject(){return userObject;}
}
