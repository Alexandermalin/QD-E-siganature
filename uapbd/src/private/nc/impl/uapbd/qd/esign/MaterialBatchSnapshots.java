package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.vo.bd.material.MaterialVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Candidate material fast-batch snapshot, including the version row updated by the native service. */
final class MaterialBatchSnapshots {
    private MaterialBatchSnapshots() { }

    static List<SnapshotRow> read(PhysicalSnapshotReader reader,String group,BusinessObjectRef ref,MaterialVO value,boolean lock)
            throws BusinessException {
        EsignValues.require(reader!=null && group!=null && ref!=null && value!=null && value.getLatest()!=null
            && ref.getObjectPk()!=null && ref.getObjectPk().equals(value.getPk_material()) && group.equals(value.getPk_group())
            && ref.getPolicyOwnerOrg()!=null && ref.getPolicyOwnerOrg().equals(value.getPk_org())
            && ref.getBillCode()!=null && ref.getBillCode().equals(value.getCode()) && value.getPk_source()!=null,
            "物料批改主档、所属组织、版本或编码已变化。");
        String pk=ref.getObjectPk();
        List<SnapshotRow> root=lock?reader.readLocked("bd_material","pk_material",null,"pk_material",pk,"pk_group",group,true)
            :reader.read("bd_material","pk_material",null,"pk_material",pk,"pk_group",group,true);
        EsignValues.require(root.size()==1 && value.getPk_source().equals(root.get(0).getValues().get("pk_source"))
            && ref.getPolicyOwnerOrg().equals(root.get(0).getValues().get("pk_org"))
            && ref.getBillCode().equals(root.get(0).getValues().get("code"))
            && value.getLatest().toString().equalsIgnoreCase(root.get(0).getValues().get("latest")),
            "物料批改数据与服务端主档或最新版本标记不一致。");
        List<SnapshotRow> result=new ArrayList<SnapshotRow>(root);
        result.addAll(children(reader,"bd_materialconvert","pk_materialconvert",pk,lock));
        result.addAll(children(reader,"bd_materialtaxtype","pk_materialtaxtype",pk,lock));
        if(value.getLatest().booleanValue()) {
            List<SnapshotRow> version=lock?reader.readLocked("bd_material_v","pk_source",null,"pk_source",value.getPk_source(),false)
                :reader.read("bd_material_v","pk_source",null,"pk_source",value.getPk_source(),false);
            EsignValues.require(version.size()==1 && group.equals(version.get(0).getValues().get("pk_group"))
                && pk.equals(version.get(0).getValues().get("pk_material"))
                && ref.getPolicyOwnerOrg().equals(version.get(0).getValues().get("pk_org")),
                "最新物料缺少唯一的集团版本行，不能安全批改。");
            result.addAll(version);
        }
        return result;
    }

    /** More than one selected latest version for the same source has ambiguous version-row ownership. */
    static void requireDistinctLatestSources(List<MaterialVO> values) throws BusinessException {
        EsignValues.require(values!=null && !values.isEmpty(),"物料批改缺少服务端档案。");Set<String> sources=new HashSet<String>();
        for(MaterialVO value:values) {
            EsignValues.require(value!=null && value.getLatest()!=null && value.getPk_source()!=null,
                "物料批改缺少版本来源或最新版本标记。");
            if(value.getLatest().booleanValue()) EsignValues.require(sources.add(value.getPk_source()),
                "物料批改存在多个指向同一来源的最新版本，无法确认版本行归属。");
        }
    }

    private static List<SnapshotRow> children(PhysicalSnapshotReader reader,String table,String key,String material,boolean lock)
            throws BusinessException {
        return lock?reader.readLocked(table,key,"pk_material","pk_material",material,false)
            :reader.read(table,key,"pk_material","pk_material",material,false);
    }
}
