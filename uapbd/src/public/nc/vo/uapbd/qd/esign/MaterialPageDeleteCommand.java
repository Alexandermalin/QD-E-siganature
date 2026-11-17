package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.pub.SuperVO;

/** Selected rows keyed by the client object IDs saved during preparation. */
public final class MaterialPageDeleteCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String,SuperVO> rows;
    public MaterialPageDeleteCommand(Map<String,SuperVO> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Material page rows required");
        this.rows = Collections.unmodifiableMap(new LinkedHashMap<String,SuperVO>(rows));
    }
    public Map<String,SuperVO> getRows() { return rows; }
}
