package nc.ui.uapbd.qd.esign;

import java.util.Map;
import java.util.TreeMap;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;

/** Human-readable values for the exact native fields and related values submitted for signing. */
final class BatchSignatureSummary {
    private BatchSignatureSummary() { }
    static String format(PartnerBatchDraft draft) {
        if(draft==null) return "";
        StringBuilder text=new StringBuilder();
        String[] targets=draft.getTargetOrgs();
        if(targets.length>0) {
            text.append("目标组织主键：");
            for(int i=0;i<targets.length;i++) { if(i>0) text.append('、');text.append(targets[i]); }
        }
        if(draft.hasPageSelection()) {
            if(text.length()>0) text.append('\n');
            text.append("本批固定页签行数：").append(draft.getSelectedPageIds().length)
                .append("；计划后新增的行不包含在本批。");
        }
        for(Map.Entry<String,Object> item:new TreeMap<String,Object>(draft.getValues()).entrySet()) {
            if(text.length()>0) text.append('\n');
            text.append(item.getKey()).append("：").append(item.getValue()==null?"（空）":item.getValue().toString());
        }
        return text.toString();
    }
}
