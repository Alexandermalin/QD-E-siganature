package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.bd.cust.CustomerVO;

/** Selected customer rows, including their native optimistic versions. */
public final class CustomerFreezeCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final Map<String,CustomerVO> rows;
    public CustomerFreezeCommand(Map<String,CustomerVO> rows) {
        if(rows==null || rows.isEmpty()) throw new IllegalArgumentException("Customer rows are required");
        this.rows=Collections.unmodifiableMap(new LinkedHashMap<String,CustomerVO>(rows));
    }
    public Map<String,CustomerVO> getRows() { return rows; }
}
