package nc.vo.so.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import nc.vo.so.m30.entity.SaleOrderVO;

/** 销售订单直接服务动作（冻结/解冻/整单关闭）的类型化命令；数组与业务检查标记以副本隔离。 */
public final class SaleOrderStateCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 冻结、解冻、关闭实际操作的原生单据集合。 */
    private final SaleOrderVO[] bills;
    /** 整单关闭时由原生输入框取得的关闭原因；签名事由另行在上下文中传递，二者不混用。 */
    private final String closeReason;
    /** 解冻 ATP 提示后由用户确认的继续标记；未确认时必须为空，不得默认跳过检查。 */
    private final Map<String, Boolean> businessCheckMap;

    public SaleOrderStateCommand(SaleOrderVO[] bills, String closeReason, Map<String, Boolean> businessCheckMap) {
        this.bills = bills == null ? null : bills.clone();
        this.closeReason = closeReason;
        this.businessCheckMap = businessCheckMap == null ? Collections.<String, Boolean>emptyMap()
                : Collections.unmodifiableMap(new HashMap<String, Boolean>(businessCheckMap));
    }

    /** 返回待处理单据副本，保留原生 VO 内部字段。 */
    public SaleOrderVO[] getBills() { return bills == null ? null : bills.clone(); }

    /** 返回关闭原因；非关闭动作必须为空。 */
    public String getCloseReason() { return closeReason; }

    /** 返回副本形式的业务检查标记，调用方不能据返回值改动受控执行参数。 */
    public Map<String, Boolean> getBusinessCheckMap() {
        return businessCheckMap.isEmpty() ? Collections.<String, Boolean>emptyMap()
                : new HashMap<String, Boolean>(businessCheckMap);
    }
}
