package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.pub.SuperVO;

/** Original NC partner rows, including optimistic versions, for one controlled batch. */
public final class PartnerStateCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String,SuperVO> rows;

    public PartnerStateCommand(Map<String,SuperVO> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Partner rows are required");
        this.rows = Collections.unmodifiableMap(new LinkedHashMap<String,SuperVO>(rows));
    }

    public Map<String,SuperVO> getRows() { return rows; }
}
