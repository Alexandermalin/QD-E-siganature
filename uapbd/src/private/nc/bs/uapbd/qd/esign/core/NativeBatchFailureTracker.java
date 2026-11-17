package nc.bs.uapbd.qd.esign.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;

/** Records objects explicitly removed by a native batch validation stage, without parsing free-text errors. */
public final class NativeBatchFailureTracker {
    private final Map<String,String> removed=new HashMap<String,String>();

    /** Capture the input before calling NC because a validator is allowed to mutate its input collection. */
    public static Set<String> snapshot(Iterable<? extends SuperVO> rows) throws BusinessException {
        EsignValues.require(rows!=null,"原生批改校验输入不存在。");
        Set<String> ids=new HashSet<String>();
        for(SuperVO row:rows) EsignValues.require(row!=null && present(row.getPrimaryKey()) && ids.add(row.getPrimaryKey()),
            "原生批改校验输入包含重复或无主键的档案。");
        return Collections.unmodifiableSet(ids);
    }

    /** The caller supplies the actual returned native objects; additions and duplicates are rejected. */
    public void filtered(String stage,Set<String> before,Iterable<? extends SuperVO> after) throws BusinessException {
        EsignValues.require(("PERMISSION".equals(stage) || "VALIDATION".equals(stage) || "BEFORE_EVENT".equals(stage))
            && before!=null && after!=null,"原生批改校验阶段或结果无效。");
        Set<String> remaining=snapshot(after);
        EsignValues.require(before.containsAll(remaining),"原生批改校验返回了未输入的档案。");
        for(String pk:before) EsignValues.require(present(pk) && !removed.containsKey(pk),"原生批改校验重复处理了已排除的档案。");
        for(String pk:before) if(!remaining.contains(pk)) removed.put(pk,stage);
    }

    public Map<String,String> excluded() { return Collections.unmodifiableMap(new HashMap<String,String>(removed)); }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }
}
