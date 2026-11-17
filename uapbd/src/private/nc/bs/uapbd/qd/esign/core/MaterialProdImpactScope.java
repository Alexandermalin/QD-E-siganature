package nc.bs.uapbd.qd.esign.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.TreeSet;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.md.data.access.NCObject;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.material.prod.MaterialProdVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.MaterialProdImpact;
import nc.vo.util.BDPKLockUtil;

/** Candidate scope for the rows changed by NC's production cost-class feature cascade. */
public final class MaterialProdImpactScope {
    private static final String DOMAIN="BD_MATERIAL_PROD";
    private static final int LIMIT=1000;
    private MaterialProdImpactScope() { }

    public static List<BusinessObjectRef> expand(String group,BusinessObjectRef selected,MaterialProdImpact impact)
            throws BusinessException { return expand(group,selected,impact,new NativeReader()); }
    public static void verify(String group,List<BusinessObjectRef> authorized,MaterialProdImpact impact)
            throws BusinessException { verify(group,authorized,impact,new NativeReader()); }

    /** Match the native production service's material-PK business lock before database row locks. */
    public static void lockMaterials(String group,List<BusinessObjectRef> authorized,String targetOrg) throws BusinessException {
        EsignValues.require(present(group) && safePk(targetOrg) && authorized!=null && !authorized.isEmpty(),"无法确定生产保存加锁范围。");
        QdEsignStore store=new QdEsignStore();Set<String> pks=new TreeSet<String>();
        for(BusinessObjectRef ref:authorized) {
            EsignValues.require(ref!=null && DOMAIN.equals(ref.getObjectType()) && safePk(ref.getObjectPk())
                && ref.getPolicyOwnerOrg()!=null && targetOrg.equals(ref.getTargetOrg()),"生产保存授权对象缺少所属或目标组织。");
            Map<String,Object> row=store.loadMaterialOwner(group,DOMAIN,ref.getObjectPk(),false);
            EsignValues.require(row!=null && group.equals(EsignValues.text(row,"pk_group"))
                && ref.getPolicyOwnerOrg().equals(EsignValues.text(row,"pk_org"))
                && targetOrg.equals(EsignValues.text(row,"target_org")) && safePk(EsignValues.text(row,"pk_material")),
                "生产保存关联物料或组织已变化，请重新发起。");
            pks.add(EsignValues.text(row,"pk_material"));
        }
        BDPKLockUtil.lockString(pks.toArray(new String[pks.size()]));
    }

    interface Reader { List<Row> affected(String group,String targetOrg,String costClass,String selectedPk) throws BusinessException; }
    static final class Row {
        final String pk,group,org,cost,material,code;
        Row(String pk,String group,String org,String cost,String material,String code) {
            this.pk=pk;this.group=group;this.org=org;this.cost=cost;this.material=material;this.code=code;
        }
    }
    static List<BusinessObjectRef> expand(String group,BusinessObjectRef selected,MaterialProdImpact impact,Reader reader)
            throws BusinessException {
        EsignValues.require(present(group) && selected!=null && DOMAIN.equals(selected.getObjectType())
            && safePk(selected.getObjectPk()) && selected.getParentClientObjectId()==null && impact!=null
            && safePk(impact.getTargetOrg()) && reader!=null,"物料生产保存缺少有效的档案或目标组织。");
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();result.add(selected);
        if(!impact.isClassFeature()) return Collections.unmodifiableList(result);
        EsignValues.require(safePk(impact.getCostClass()),"启用成本类特征前必须确定有效的成本类。");
        List<Row> rows=reader.affected(group,impact.getTargetOrg(),impact.getCostClass(),selected.getObjectPk());
        EsignValues.require(rows!=null,"无法确定生产成本类影响范围。");
        rows=new ArrayList<Row>(rows);
        for(Row row:rows) EsignValues.require(row!=null && safePk(row.pk),"生产成本类影响范围缺少行标识。");
        Collections.sort(rows,new Comparator<Row>() { public int compare(Row a,Row b) { return a.pk.compareTo(b.pk); } });
        Set<String> keys=new HashSet<String>(),clients=new HashSet<String>();clients.add(selected.getClientObjectId());
        for(Row row:rows) {
            EsignValues.require(!selected.getObjectPk().equals(row.pk) && keys.add(row.pk) && group.equals(row.group)
                && impact.getTargetOrg().equals(row.org) && impact.getCostClass().equals(row.cost)
                && safePk(row.material) && present(row.code),"生产成本类影响范围包含重复、跨集团或不匹配的数据。");
            String clientId="prod-"+UUID.nameUUIDFromBytes((selected.getObjectPk()+"\u0000"+row.pk)
                .getBytes(Charset.forName("UTF-8"))).toString();
            EsignValues.require(clients.add(clientId),"生产成本类影响对象标识冲突。");
            result.add(new BusinessObjectRef(clientId,DOMAIN,row.pk,row.code,selected.getClientObjectId()));
            EsignValues.require(result.size()<=LIMIT,"生产成本类影响超过当前支持的 1000 行，请联系管理员处理。");
        }
        return Collections.unmodifiableList(result);
    }
    static void verify(String group,List<BusinessObjectRef> authorized,MaterialProdImpact impact,Reader reader)
            throws BusinessException {
        EsignValues.require(authorized!=null && !authorized.isEmpty(),"生产保存授权范围为空。");
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:authorized) if(ref!=null && ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"生产保存只能选择一个主对象。");root=ref;
        }
        EsignValues.require(root!=null,"无法确定生产保存主对象。");
        List<BusinessObjectRef> current=expand(group,root,impact,reader);
        EsignValues.require(identities(authorized).equals(identities(current)),"生产成本类影响范围已变化，请重新发起保存。");
    }
    private static Map<String,String> identities(List<BusinessObjectRef> objects) throws BusinessException {
        Map<String,String> result=new HashMap<String,String>();Set<String> clients=new HashSet<String>();
        for(BusinessObjectRef ref:objects) EsignValues.require(ref!=null && DOMAIN.equals(ref.getObjectType())
            && safePk(ref.getObjectPk()) && clients.add(ref.getClientObjectId())
            && result.put(ref.getObjectPk(),ref.getClientObjectId()+"\u0000"+(ref.getParentClientObjectId()==null?"":ref.getParentClientObjectId()))==null,
            "生产保存授权对象无效或重复。");
        return result;
    }
    private static boolean safePk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty(); }

    private static final class NativeReader implements Reader {
        public List<Row> affected(String group,String targetOrg,String costClass,String selectedPk) throws BusinessException {
            String where="pk_org = '"+targetOrg+"' and pk_marcostclass = '"+costClass+"' and pk_materialprod <> '"+selectedPk+"'";
            final NCObject[] nativeRows;
            try { nativeRows=MDPersistenceService.lookupPersistenceQueryService()
                    .queryBillOfNCObjectByCond(MaterialProdVO.class,where,true); }
            catch(Exception error) { throw new BusinessException("无法查询物料生产成本类影响范围。",error); }
            List<Row> rows=new ArrayList<Row>();QdEsignStore store=new QdEsignStore();
            if(nativeRows==null) return rows;
            for(NCObject entry:nativeRows) {
                Object raw=entry==null?null:entry.getContainmentObject();
                EsignValues.require(raw instanceof MaterialProdVO,"生产成本类查询返回了未知类型。");
                MaterialProdVO value=(MaterialProdVO)raw;
                Map<String,Object> owner=store.loadMaterialOwner(group,DOMAIN,value.getPk_materialprod(),false);
                EsignValues.require(owner!=null && group.equals(EsignValues.text(owner,"pk_group"))
                    && value.getPk_material()!=null && value.getPk_material().equals(EsignValues.text(owner,"pk_material"))
                    && value.getPk_org()!=null && value.getPk_org().equals(EsignValues.text(owner,"target_org")),
                    "无法核实生产成本类关联物料的集团、主档或目标组织。");
                rows.add(new Row(value.getPk_materialprod(),value.getPk_group(),value.getPk_org(),value.getPk_marcostclass(),
                    value.getPk_material(),EsignValues.text(owner,"code")));
            }
            return rows;
        }
    }
}
