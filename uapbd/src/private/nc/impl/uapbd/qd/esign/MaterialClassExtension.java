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
import nc.itf.bd.marbasclsext.IMarbasclsextMaintain;
import nc.itf.bd.marbasclsext.IMarbasclsextQueryService;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.ui.bd.pub.extend.PrivateServiceInfo;
import nc.vo.bd.marbasclsext.entity.AggMarBasClsExtVO;
import nc.vo.bd.marbasclsext.entity.MarBasClsExtBVO;
import nc.vo.bd.marbasclsext.entity.MarBasClsExtVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Native SRM extension registered for the group and business-unit material class nodes. */
final class MaterialClassExtension {
    private static final String HEADER="srmsm_marbasclsext";
    private static final String HEAD_PK="pk_marbasclsext";
    private static final String BODY="srmsm_marbasclsext_b";
    private static final String BODY_PK="pk_marbasclsext_b";
    private static final String CLASS_FK="cmarbasclsid";

    private MaterialClassExtension() { }

    static List<SnapshotRow> rows(PhysicalSnapshotReader reader,String pk,String group,String owner,boolean lock)
            throws BusinessException {
        List<SnapshotRow> heads=lock?reader.readLocked(HEADER,HEAD_PK,CLASS_FK,CLASS_FK,pk,true)
            :reader.read(HEADER,HEAD_PK,CLASS_FK,CLASS_FK,pk,true);
        List<SnapshotRow> body=lock?reader.readLocked(BODY,BODY_PK,HEAD_PK,CLASS_FK,pk,true)
            :reader.read(BODY,BODY_PK,HEAD_PK,CLASS_FK,pk,true);
        EsignValues.require(heads.size()<=1,"物料分类存在重复的供应商扩展档案。");
        List<SnapshotRow> result=new ArrayList<SnapshotRow>(heads);
        result.addAll(body);
        for(SnapshotRow row:result) {
            EsignValues.require(group.equals(row.getValues().get("pk_group")) && owner.equals(row.getValues().get("pk_org")),
                "物料分类供应商扩展的所属组织与分类不一致。");
            if(BODY.equals(row.getEntity())) EsignValues.require(!heads.isEmpty()
                && heads.get(0).getRowId().equals(row.getParentRowId()),"物料分类供应商扩展子行缺少对应的表头。");
        }
        if(!heads.isEmpty()) {
            List<SnapshotRow> linked=lock?reader.readLocked(BODY,BODY_PK,HEAD_PK,HEAD_PK,heads.get(0).getRowId(),true)
                :reader.read(BODY,BODY_PK,HEAD_PK,HEAD_PK,heads.get(0).getRowId(),true);
            EsignValues.require(linked.size()==body.size(),"物料分类供应商扩展子行关联了其他分类。");
        }
        return result;
    }

    static PrivateServiceContext saveContext(String node,MarBasClassVO value,List<SnapshotRow> stored)
            throws BusinessException {
        boolean configured=configured(node);
        Map.Entry<String,Object> entry=entry(value.getExtendObjMap());
        EsignValues.require(configured || (entry==null && stored.isEmpty()),"物料分类供应商扩展配置与数据不一致。");
        if(entry==null || entry.getValue()==null) return new PrivateServiceContext();
        EsignValues.require(entry.getValue().getClass()==AggMarBasClsExtVO.class,"物料分类包含未知扩展对象。");
        validatePayload(value,(AggMarBasClsExtVO)entry.getValue(),stored);
        return context(entry.getKey());
    }

    static PrivateServiceContext deleteContext(String node,MarBasClassVO value,List<SnapshotRow> stored)
            throws BusinessException {
        boolean configured=configured(node);
        Map.Entry<String,Object> entry=entry(value.getExtendObjMap());
        EsignValues.require(configured || (entry==null && stored.isEmpty()),"物料分类供应商扩展配置与数据不一致。");
        if(stored.isEmpty()) {
            value.setExtendObjMap(null);
            return new PrivateServiceContext();
        }
        SnapshotRow header=stored.get(0);
        AggMarBasClsExtVO[] loaded=new BillQuery<AggMarBasClsExtVO>(AggMarBasClsExtVO.class).query(new String[]{header.getRowId()});
        AggMarBasClsExtVO current=loaded!=null && loaded.length==1?loaded[0]:null;
        validateDeleteLoaded(value,current,stored);
        String identifier="QD_MATERIAL_CLASS_EXTENSION";
        value.setExtendObjMap(Collections.<String,Object>singletonMap(identifier,current));
        return context(identifier);
    }

    /** The native delete must receive every active extension child, loaded by the server. */
    static void validateDeleteLoaded(MarBasClassVO base,AggMarBasClsExtVO current,List<SnapshotRow> stored)
            throws BusinessException {
        EsignValues.require(base!=null && current!=null && current.getParentVO()!=null && stored!=null && !stored.isEmpty(),
            "无法加载待删除的物料分类供应商扩展。");
        validatePayload(base,current,stored);
        MarBasClsExtVO head=current.getParentVO();List<String> children=new ArrayList<String>();
        MarBasClsExtBVO[] loaded=current.getChildrenVO();
        if(loaded!=null) for(MarBasClsExtBVO child:loaded) {
            EsignValues.require(child!=null && child.getPk_marbasclsext_b()!=null
                && head.getPk_marbasclsext()!=null && head.getPk_marbasclsext().equals(child.getPk_marbasclsext())
                && base.getPk_marbasclass()!=null && base.getPk_marbasclass().equals(child.getCmarbasclsid())
                && base.getPk_group()!=null && base.getPk_group().equals(child.getPk_group())
                && base.getPk_org()!=null && base.getPk_org().equals(child.getPk_org()),"物料分类供应商扩展子行未完整加载。");
            children.add(child.getPk_marbasclsext_b());
        }
        requireCompleteDeleteRows(base.getPk_marbasclass(),base.getPk_group(),base.getPk_org(),stored,
            head.getPk_marbasclsext(),head.getCmarbasclsid(),head.getPk_group(),head.getPk_org(),children);
    }

    static void requireCompleteDeleteRows(String classPk,String group,String owner,List<SnapshotRow> stored,
            String headerPk,String loadedClass,String loadedGroup,String loadedOwner,List<String> children) throws BusinessException {
        EsignValues.require(classPk!=null && group!=null && owner!=null && headerPk!=null && stored!=null && children!=null
            && classPk.equals(loadedClass) && group.equals(loadedGroup) && owner.equals(loadedOwner),
            "物料分类供应商扩展表头不属于待删分类。");
        Map<String,SnapshotRow> expected=new HashMap<String,SnapshotRow>();
        for(SnapshotRow row:stored) {
            EsignValues.require(row!=null && (HEADER.equals(row.getEntity()) || BODY.equals(row.getEntity()))
                && classPk.equals(row.getValues().get(CLASS_FK)) && group.equals(row.getValues().get("pk_group"))
                && owner.equals(row.getValues().get("pk_org"))
                && expected.put(row.getEntity()+"\u0000"+row.getRowId(),row)==null,
                "物料分类供应商扩展物理行无效或重复。");
        }
        EsignValues.require(expected.remove(HEADER+"\u0000"+headerPk)!=null,"物料分类供应商扩展表头缺失。");
        for(String child:children) {
            SnapshotRow row=child==null?null:expected.remove(BODY+"\u0000"+child);
            EsignValues.require(row!=null && headerPk.equals(row.getParentRowId()),"物料分类供应商扩展子行未完整加载或关联有误。");
        }
        EsignValues.require(expected.isEmpty(),"物料分类供应商扩展子行未完整加载，不能删除。");
    }

    static void validatePayload(MarBasClassVO base,AggMarBasClsExtVO extension,List<SnapshotRow> stored)
            throws BusinessException {
        MarBasClsExtVO head=extension.getParentVO();
        String expected=stored.isEmpty()?null:stored.get(0).getRowId();
        EsignValues.require(head!=null && head.getClass()==MarBasClsExtVO.class
            && java.util.Objects.equals(expected,head.getPk_marbasclsext())
            && matchesIfPresent(base.getPk_marbasclass(),head.getCmarbasclsid())
            && matchesIfPresent(base.getPk_group(),head.getPk_group()) && matchesIfPresent(base.getPk_org(),head.getPk_org()),
            "物料分类供应商扩展表头不属于授权分类。");
        Set<String> existing=new HashSet<String>(),seen=new HashSet<String>();
        for(SnapshotRow row:stored) if(BODY.equals(row.getEntity())) existing.add(row.getRowId());
        MarBasClsExtBVO[] children=extension.getChildrenVO();
        if(children==null) return;
        for(MarBasClsExtBVO child:children) {
            EsignValues.require(child!=null && child.getClass()==MarBasClsExtBVO.class
                && matchesIfPresent(expected,child.getPk_marbasclsext())
                && matchesIfPresent(base.getPk_marbasclass(),child.getCmarbasclsid())
                && matchesIfPresent(base.getPk_group(),child.getPk_group()) && matchesIfPresent(base.getPk_org(),child.getPk_org()),
                "物料分类供应商扩展子行不属于授权分类。");
            String pk=child.getPk_marbasclsext_b();
            EsignValues.require(pk==null || existing.contains(pk) && seen.add(pk),"物料分类供应商扩展子行主键不正确。");
        }
    }

    private static boolean matchesIfPresent(String expected,Object value) {
        return value==null || "".equals(value) || value.equals(expected);
    }

    private static Map.Entry<String,Object> entry(Map<String,Object> objects) throws BusinessException {
        if(objects==null || objects.isEmpty()) return null;
        EsignValues.require(objects.size()==1,"物料分类包含未经验证的扩展对象。");
        Map.Entry<String,Object> entry=objects.entrySet().iterator().next();
        EsignValues.require(entry.getKey()!=null && !entry.getKey().trim().isEmpty() && entry.getKey().length()<=128,
            "物料分类供应商扩展标识不正确。");
        return entry;
    }

    private static PrivateServiceContext context(String key) {
        PrivateServiceInfo info=new PrivateServiceInfo();
        info.setIdentifier(key);
        info.setPersistenceService(IMarbasclsextMaintain.class.getName());
        info.setQueryService(IMarbasclsextQueryService.class.getName());
        PrivateServiceContext context=new PrivateServiceContext();
        context.addExtendInfo(info);
        return context;
    }

    private static boolean configured(String node) throws BusinessException {
        String expected="nc/ui/bd/marbasclsext/maintain/config/marbasclsext"
            +("10140MCLO".equals(node)?"_org":"")+".xml";
        return ArchiveExtensionConfig.requireKnown(node,expected);
    }
}
