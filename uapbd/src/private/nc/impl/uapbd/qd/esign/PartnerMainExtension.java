package nc.impl.uapbd.qd.esign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.impl.pubapp.pattern.data.bill.BillQuery;
import nc.itf.bd.supplierext.IExtendQueryService_SupplierEx;
import nc.itf.bd.supplierext.ISupplierextMaintain;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.ui.bd.pub.extend.PrivateServiceInfo;
import nc.vo.bd.supplierext.entity.SupplierExtAVO;
import nc.vo.bd.supplierext.entity.SupplierExtIVO;
import nc.vo.bd.supplierext.entity.SupplierExtPVO;
import nc.vo.bd.supplierext.entity.SupplierExtQVO;
import nc.vo.bd.supplierext.entity.SupplierExtVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Validates the native SRM supplier extension and captures its physical header and four child tables. */
final class PartnerMainExtension {
    private static final String HEADER="srmsm_supplierext_h";
    private static final String SUPPLIER="pk_supplier";
    private static final String[] TABLES={HEADER,"srmsm_supplierext_a","srmsm_supplierext_i","srmsm_supplierext_p","srmsm_supplierext_q"};
    private static final String[] KEYS={SUPPLIER,"pk_supplierext_a","pk_supplierext_i","pk_supplierext_p","pk_supplierext_q"};
    private static final Class<?>[] CHILD_TYPES={SupplierExtAVO.class,SupplierExtIVO.class,SupplierExtPVO.class,SupplierExtQVO.class};

    private PartnerMainExtension() { }

    static final class DeletePlan {
        final Map<String,Object> objects;final PrivateServiceContext context;
        DeletePlan(Map<String,Object> objects,PrivateServiceContext context) { this.objects=objects;this.context=context; }
    }

    /** Reload the complete extension from the server instead of trusting the selected editor tab. */
    static DeletePlan deletePlan(String node,SuperVO partner,List<SnapshotRow> stored) throws BusinessException {
        boolean supplier="10140SUG".equals(node) || "10140SUO".equals(node);
        String path=supplier?"nc/ui/bd/supplierext/maintain/config/supplierext"+("10140SUO".equals(node)?"_org":"")+".xml":null;
        boolean configured=ArchiveExtensionConfig.requireKnown(node,path);
        EsignValues.require(stored!=null && (configured || stored.isEmpty()),"主档扩展配置与现存数据不一致。");
        if(stored.isEmpty()) return new DeletePlan(Collections.<String,Object>emptyMap(),new PrivateServiceContext());
        EsignValues.require(supplier && partner!=null && partner.getPrimaryKey()!=null,"供应商扩展删除缺少主档。");
        SupplierExtVO[] loaded=new BillQuery<SupplierExtVO>(SupplierExtVO.class).query(new String[]{partner.getPrimaryKey()});
        SupplierExtVO current=loaded!=null && loaded.length==1?loaded[0]:null;
        validateDeleteLoaded(partner,current,stored);
        Map<String,Object> objects=Collections.<String,Object>singletonMap("QD_SUPPLIER_EXTENSION",current);
        return new DeletePlan(objects,context(node,partner,objects,stored));
    }

    static void validateDeleteLoaded(SuperVO partner,SupplierExtVO current,List<SnapshotRow> stored) throws BusinessException {
        EsignValues.require(partner!=null && partner.getPrimaryKey()!=null && current!=null && current.getParentVO()!=null
            && partner.getPrimaryKey().equals(current.getParentVO().getPk_supplier())
            && partner.getAttributeValue("pk_group")!=null
            && partner.getAttributeValue("pk_group").equals(current.getParentVO().getPk_group()),"供应商扩展表头不属于待删档案。");
        validate(partner,current,stored);
        List<String> children=new ArrayList<String>();
        SuperVO[][] arrays=current.getAllChildren();
        if(arrays!=null) for(SuperVO[] array:arrays) if(array!=null) for(SuperVO row:array) {
            int index=-1;for(int i=0;row!=null && i<CHILD_TYPES.length;i++) if(row.getClass()==CHILD_TYPES[i]) index=i;
            EsignValues.require(index>=0 && row.getPrimaryKey()!=null,"供应商扩展子行未完整加载。");
            children.add(TABLES[index+1]+"\u0000"+row.getPrimaryKey());
        }
        requireCompleteDeleteRows(partner.getPrimaryKey(),partner.getAttributeValue("pk_group").toString(),stored,
            current.getParentVO().getPk_supplier(),current.getParentVO().getPk_group(),children);
    }

    static void requireCompleteDeleteRows(String supplier,String group,List<SnapshotRow> stored,String loadedSupplier,
            String loadedGroup,List<String> children) throws BusinessException {
        EsignValues.require(supplier!=null && group!=null && supplier.equals(loadedSupplier) && group.equals(loadedGroup)
            && stored!=null && children!=null,"供应商扩展表头不属于待删档案。");
        Map<String,SnapshotRow> expected=new HashMap<String,SnapshotRow>();
        for(SnapshotRow row:stored) {
            boolean known=false;for(String table:TABLES) if(row!=null && table.equals(row.getEntity())) known=true;
            EsignValues.require(known && supplier.equals(row.getValues().get(SUPPLIER)) && group.equals(row.getValues().get("pk_group"))
                && expected.put(row.getEntity()+"\u0000"+row.getRowId(),row)==null,"供应商扩展物理行无效或重复。");
        }
        SnapshotRow head=expected.remove(HEADER+"\u0000"+supplier);
        EsignValues.require(head!=null,"供应商扩展表头缺失或不属于当前集团。");
        for(String child:children) EsignValues.require(child!=null && expected.remove(child)!=null,"供应商扩展子行未完整加载或重复。");
        EsignValues.require(expected.isEmpty(),"供应商扩展子行未完整加载，不能删除。");
    }

    static List<SnapshotRow> rows(PhysicalSnapshotReader reader,String group,String supplier,boolean lock) throws BusinessException {
        List<SnapshotRow> result=new ArrayList<SnapshotRow>();
        for(int index=0;index<TABLES.length;index++) {
            String table=TABLES[index],key=KEYS[index],parent=index==0?null:SUPPLIER;
            List<SnapshotRow> rows=lock?reader.readLocked(table,key,parent,SUPPLIER,supplier,true)
                :reader.read(table,key,parent,SUPPLIER,supplier,true);
            if(index==0) EsignValues.require(rows.size()<=1,"供应商扩展表头不唯一。");
            for(SnapshotRow row:rows) EsignValues.require(group.equals(row.getValues().get("pk_group"))
                && supplier.equals(row.getValues().get(SUPPLIER)),"供应商扩展包含其他集团或档案的数据。");
            result.addAll(rows);
        }
        return result;
    }

    static PrivateServiceContext context(String node,SuperVO partner,Map<String,Object> input,List<SnapshotRow> stored)
            throws BusinessException {
        boolean supplier="10140SUG".equals(node) || "10140SUO".equals(node);
        String path=supplier?"nc/ui/bd/supplierext/maintain/config/supplierext"+("10140SUO".equals(node)?"_org":"")+".xml":null;
        boolean configured=ArchiveExtensionConfig.requireKnown(node,path);
        EsignValues.require(input!=null && input.size()<=1 && (configured || input.isEmpty()),"主档包含未经验证的扩展对象。");
        PrivateServiceContext context=new PrivateServiceContext();
        if(input.isEmpty()) return context;
        Map.Entry<String,Object> entry=input.entrySet().iterator().next();
        String identifier=entry.getKey();
        EsignValues.require(identifier!=null && !identifier.trim().isEmpty() && identifier.length()<=128,
            "供应商扩展标识不正确。");
        if(entry.getValue()==null) return context;
        EsignValues.require(entry.getValue().getClass()==SupplierExtVO.class,"供应商扩展类型不正确。");
        validate(partner,(SupplierExtVO)entry.getValue(),stored);
        PrivateServiceInfo info=new PrivateServiceInfo();
        info.setIdentifier(identifier);
        info.setPersistenceService(ISupplierextMaintain.class.getName());
        info.setQueryService(IExtendQueryService_SupplierEx.class.getName());
        context.addExtendInfo(info);
        return context;
    }

    private static void validate(SuperVO supplier,SupplierExtVO input,List<SnapshotRow> stored) throws BusinessException {
        String pk=supplier.getPrimaryKey();Object group=supplier.getAttributeValue("pk_group");
        SuperVO head=input.getParentVO();
        EsignValues.require(head!=null && sameIfPresent(pk,head.getAttributeValue(SUPPLIER))
            && sameIfPresent(group,head.getAttributeValue("pk_group")),"供应商扩展表头不属于授权档案。");
        Map<String,SnapshotRow> existing=new HashMap<String,SnapshotRow>();
        for(SnapshotRow row:stored) existing.put(row.getEntity()+"\u0000"+row.getRowId(),row);
        SuperVO[][] children=input.getAllChildren();
        if(children==null) return;
        Set<String> seen=new HashSet<String>();
        for(SuperVO[] childTable:children) {
            if(childTable==null) continue;
            for(SuperVO child:childTable) {
                int type=-1;
                for(int index=0;child!=null && index<CHILD_TYPES.length;index++) if(child.getClass()==CHILD_TYPES[index]) type=index;
                EsignValues.require(type>=0 && sameIfPresent(pk,child.getAttributeValue(SUPPLIER))
                    && sameIfPresent(group,child.getAttributeValue("pk_group")),"供应商扩展子行不属于授权档案。");
                String id=child.getPrimaryKey();
                String key=TABLES[type+1]+"\u0000"+id;
                EsignValues.require(id==null || existing.containsKey(key) && seen.add(key),"供应商扩展子行主键不正确。");
            }
        }
    }

    private static boolean sameIfPresent(Object expected,Object supplied) {
        return supplied==null || "".equals(supplied) || supplied.equals(expected);
    }
}
