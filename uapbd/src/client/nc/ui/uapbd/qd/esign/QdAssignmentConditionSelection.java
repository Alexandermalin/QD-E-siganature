package nc.ui.uapbd.qd.esign;

import java.util.HashSet;
import java.util.Set;
import nc.itf.bd.pub.assign.AssignStatus;
import nc.vo.pub.BusinessException;

/** Freezes NC's condition result and target organizations before starting a signed assignment. */
final class QdAssignmentConditionSelection {
    private QdAssignmentConditionSelection() { }
    interface Query {
        String[] find(String where,AssignStatus status,String[] targets) throws BusinessException;
    }
    static final class Selection {
        private final String[] objects,targets;
        Selection(String[] objects,String[] targets) { this.objects=objects.clone();this.targets=targets.clone(); }
        String[] objects() { return objects.clone(); }
        String[] targets() { return targets.clone(); }
    }
    static Selection resolve(String where,AssignStatus status,String[] targets,Query query) throws BusinessException {
        if(status==null || targets==null || targets.length==0 || targets.length>1000 || query==null)
            throw new BusinessException("条件分配缺少有效状态或目标组织。");
        String[] frozenTargets=targets.clone();requireUnique(frozenTargets,"目标组织");
        // Use the native permission-filtered query. Its result, not the mutable condition, is signed and submitted.
        String[] result=query.find(where,status,frozenTargets.clone());
        if(result==null || result.length==0) throw new BusinessException("条件查询没有可处理的档案。");
        if((long)result.length*frozenTargets.length>1000)
            throw new BusinessException("一次最多处理 1000 个档案和目标组织组合，请缩小条件范围。");
        String[] frozenObjects=result.clone();requireUnique(frozenObjects,"档案");
        return new Selection(frozenObjects,frozenTargets);
    }
    private static void requireUnique(String[] values,String name) throws BusinessException {
        Set<String> unique=new HashSet<String>();
        for(String value:values) if(value==null || !value.matches("[A-Za-z0-9_-]{1,20}") || !unique.add(value))
            throw new BusinessException("条件分配的"+name+"主键无效或重复。");
    }
}
