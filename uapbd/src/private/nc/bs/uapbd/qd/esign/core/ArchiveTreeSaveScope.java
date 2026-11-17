package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.vo.bd.cust.custclass.CustClassVO;
import nc.vo.bd.material.marbasclass.MarBasClassVO;
import nc.vo.bd.rack.RackVO;
import nc.vo.bd.supplier.supplierclass.SupplierclassVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** Selects the authoritative root or whole native branch before an archive tree save. */
public final class ArchiveTreeSaveScope {
    private ArchiveTreeSaveScope() { }

    public static Decision select(String group,BusinessObjectRef root,String submittedCode,String submittedParent,String warehouse)
            throws BusinessException {
        return select(group,root,submittedCode,submittedParent,warehouse,new NativeReader());
    }
    static Decision select(String group,BusinessObjectRef root,String submittedCode,String submittedParent,String warehouse,Reader reader)
            throws BusinessException {
        EsignValues.require(group!=null && root!=null && ArchiveTreeScope.supports(root.getObjectType())
            && root.getObjectPk()!=null && root.getParentClientObjectId()==null && reader!=null,
            "树形档案保存缺少已确定的主档。");
        State state=reader.load(group,root);
        EsignValues.require(state!=null && group.equals(state.group) && state.owner!=null && state.code!=null,
            "无法确定树形档案所属集团、组织或编码。");
        if("BD_RACK".equals(root.getObjectType())) EsignValues.require(state.warehouse!=null && state.warehouse.equals(warehouse),
            "货位所属仓库已变化，无法按原范围保存。");
        boolean branch=ArchiveTreeSaveImpact.changesBranch(root.getObjectType(),state.code,submittedCode,state.parent,submittedParent);
        BusinessObjectRef normalized=new BusinessObjectRef(root.getClientObjectId(),root.getObjectType(),root.getObjectPk(),state.code,null,
            root.getDesiredOwnerOrg(),root.getPolicyOwnerOrg(),root.getTargetOrg());
        List<BusinessObjectRef> objects=branch?reader.branch(group,normalized):Collections.singletonList(normalized);
        requireRootAndBranch(normalized,objects);
        return new Decision(branch,objects);
    }

    public static void verifyAuthorized(List<BusinessObjectRef> authorized,Decision current) throws BusinessException {
        EsignValues.require(current!=null && identities(authorized).equals(identities(current.objects)),
            "树形档案保存影响范围已变化，请重新发起操作。");
    }
    /** Recheck a changed branch after the native operation, before the transaction may commit. */
    public static void verifyBranch(String group,List<BusinessObjectRef> authorized) throws BusinessException {
        BusinessObjectRef root=root(authorized);
        List<BusinessObjectRef> actual=ArchiveTreeScope.expand(group,Collections.singletonList(root),false);
        EsignValues.require(identities(authorized).equals(identities(actual)),"树形档案保存后的级联范围不一致，业务必须回滚。");
    }

    public static final class Decision {
        private final boolean branch;private final List<BusinessObjectRef> objects;
        Decision(boolean branch,List<BusinessObjectRef> objects) {
            this.branch=branch;this.objects=Collections.unmodifiableList(new ArrayList<BusinessObjectRef>(objects));
        }
        public boolean changesBranch() { return branch; }
        public List<BusinessObjectRef> getObjects() { return objects; }
    }
    interface Reader {
        State load(String group,BusinessObjectRef root) throws BusinessException;
        List<BusinessObjectRef> branch(String group,BusinessObjectRef root) throws BusinessException;
    }
    static final class State {
        final String group,owner,code,parent,warehouse;
        State(String group,String owner,String code,String parent,String warehouse) {
            this.group=group;this.owner=owner;this.code=code;this.parent=parent;this.warehouse=warehouse;
        }
    }
    private static final class NativeReader implements Reader {
        public State load(String group,BusinessObjectRef root) throws BusinessException {
            Class<? extends SuperVO> type=type(root.getObjectType());
            SuperVO row=(SuperVO)new BaseDAO().retrieveByPK(type,root.getObjectPk());
            if(row==null) return null;
            return new State(text(row,"pk_group"),text(row,"pk_org"),text(row,"code"),
                text(row,ArchiveTreeSaveImpact.parentField(root.getObjectType())),
                "BD_RACK".equals(root.getObjectType())?text(row,"pk_stordoc"):null);
        }
        public List<BusinessObjectRef> branch(String group,BusinessObjectRef root) throws BusinessException {
            return ArchiveTreeScope.expand(group,Collections.singletonList(root),false);
        }
    }
    private static Class<? extends SuperVO> type(String domain) throws BusinessException {
        if("BD_CUSTCLASS".equals(domain)) return CustClassVO.class;
        if("BD_SUPPLIERCLASS".equals(domain)) return SupplierclassVO.class;
        if("BD_MARBASCLASS".equals(domain)) return MarBasClassVO.class;
        if("BD_RACK".equals(domain)) return RackVO.class;
        throw new BusinessException("未知的树形档案类型。");
    }
    private static String text(SuperVO row,String key) {
        Object value=row.getAttributeValue(key);return value instanceof String?(String)value:null;
    }
    private static void requireRootAndBranch(BusinessObjectRef expected,List<BusinessObjectRef> objects) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty() && objects.size()<=1000,"树形档案保存影响范围无效。");
        BusinessObjectRef selected=root(objects);
        EsignValues.require(expected.getObjectType().equals(selected.getObjectType()) && expected.getObjectPk().equals(selected.getObjectPk())
            && expected.getClientObjectId().equals(selected.getClientObjectId()),"树形档案保存范围缺少选中档案。");
        identities(objects);
    }
    private static BusinessObjectRef root(List<BusinessObjectRef> objects) throws BusinessException {
        EsignValues.require(objects!=null && !objects.isEmpty(),"树形档案保存授权范围为空。");
        BusinessObjectRef selected=null;
        for(BusinessObjectRef ref:objects) if(ref!=null && ref.getParentClientObjectId()==null) {
            EsignValues.require(selected==null,"树形档案保存只能选择一个根对象。");selected=ref;
        }
        EsignValues.require(selected!=null && selected.getObjectPk()!=null && ArchiveTreeScope.supports(selected.getObjectType()),"树形档案保存根对象无效。");
        return selected;
    }
    private static Set<String> identities(List<BusinessObjectRef> objects) throws BusinessException {
        BusinessObjectRef root=root(objects);Set<String> keys=new HashSet<String>(),clients=new HashSet<String>(),identities=new HashSet<String>();
        for(BusinessObjectRef ref:objects) {
            EsignValues.require(ref!=null && root.getObjectType().equals(ref.getObjectType()) && ref.getObjectPk()!=null
                && clients.add(ref.getClientObjectId()) && keys.add(ref.getObjectPk())
                && (ref==root || root.getClientObjectId().equals(ref.getParentClientObjectId())),"树形档案保存范围包含无效或重复对象。");
            identities.add(ref.getObjectType()+"\u0000"+ref.getObjectPk()+"\u0000"+ref.getClientObjectId()+"\u0000"
                +(ref.getParentClientObjectId()==null?"":ref.getParentClientObjectId()));
        }
        return identities;
    }
}
