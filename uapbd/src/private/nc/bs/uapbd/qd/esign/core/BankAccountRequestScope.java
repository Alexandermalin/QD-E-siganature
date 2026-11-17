package nc.bs.uapbd.qd.esign.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;

/** Persists one reference per physical relationship, so membership can be rechecked after authorization. */
public final class BankAccountRequestScope {
    private static final String DEFAULT_PREFIX="bank-default:";
    private BankAccountRequestScope() { }
    public interface Reader extends BankAccountImpactScope.Reader {
        String accountForRelation(String relation,boolean lock) throws BusinessException;
    }
    public static final class Expanded {
        private final BankAccountImpactScope.Selection selection;
        private final List<BusinessObjectRef> objects;
        private Expanded(BankAccountImpactScope.Selection selection,List<BusinessObjectRef> objects) {
            this.selection=selection;this.objects=Collections.unmodifiableList(objects);
        }
        public BankAccountImpactScope.Selection getSelection() { return selection; }
        public List<BusinessObjectRef> getObjects() { return objects; }
    }
    /** NC mirrors a new account to a linked customer/supplier with the same archive PK. */
    public static List<BusinessObjectRef> expandInsert(String group,BusinessObjectRef selected,boolean lock)
            throws BusinessException {
        EsignValues.require(selected!=null && selected.getObjectPk()!=null && selected.getParentClientObjectId()==null
            && selected.getDesiredOwnerOrg()==null,"银行账户新增必须指定父档案。");
        EsignValues.require(defaultCurrency(selected)==null,"设置默认账户必须使用银行账户修改授权。");
        String domain=selected.getObjectType(),archive=selected.getObjectPk();
        boolean customer="BD_CUSTOMER".equals(BankAccountRoute.archiveDomain(domain));
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        SnapshotRow root=archive(reader,archive,customer,lock);
        EsignValues.require(root!=null && group.equals(root.getValues().get("pk_group")),"银行账户父档案不属于当前集团。");
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();
        String rootId=id(archive,domain,archive);
        result.add(new BusinessObjectRef(rootId,domain,archive,root.getValues().get("code"),null));
        SnapshotRow other=archive(reader,archive,!customer,lock);
        if(other!=null && "Y".equals(other.getValues().get(customer?"iscustomer":"issupplier"))) {
            EsignValues.require(group.equals(other.getValues().get("pk_group")),"银行账户关联档案跨集团。");
            String otherDomain=customer?"BD_SUPPLIER_BANK":"BD_CUSTOMER_BANK";
            result.add(new BusinessObjectRef(id(rootId,otherDomain,archive),otherDomain,archive,other.getValues().get("code"),rootId));
        }
        return result;
    }

    public static void verifyInsert(String group,List<BusinessObjectRef> authorized,boolean lock) throws BusinessException {
        verifyObjects(authorized,expandInsert(group,root(authorized),lock));
    }

    private static SnapshotRow archive(PhysicalSnapshotReader reader,String pk,boolean customer,boolean lock) throws BusinessException {
        String table=customer?"bd_customer":"bd_supplier",key=customer?"pk_customer":"pk_supplier";
        List<SnapshotRow> rows=lock?reader.readLocked(table,key,null,key,pk,true):reader.readByPk(table,key,pk);
        EsignValues.require(rows.size()<=1,"银行账户父档案主键不唯一。");
        return rows.isEmpty()?null:rows.get(0);
    }
    /** The account and every relationship come from the server; draft owner, code and account are never trusted. */
    public static Expanded expand(String group,BusinessObjectRef selected,Reader reader,boolean lock) throws BusinessException {
        EsignValues.require(selected!=null && selected.getObjectPk()!=null && selected.getParentClientObjectId()==null
            && selected.getDesiredOwnerOrg()==null && reader!=null,"银行账户操作必须选择一个有效的关系行。");
        String account=reader.accountForRelation(selected.getObjectPk(),lock);
        BankAccountImpactScope.Selection selection=BankAccountImpactScope.select(group,account,selected,reader,lock);
        String rootId=id(account,selected.getObjectType(),selected.getObjectPk());
        String currency=defaultCurrency(selected);
        if(currency!=null) rootId=DEFAULT_PREFIX+currency+":"+rootId;
        BusinessObjectRef root=null;List<BusinessObjectRef> children=new ArrayList<BusinessObjectRef>();
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives()) for(String relation:archive.getRelations()) {
            boolean isRoot=selected.getObjectPk().equals(relation) && selected.getObjectType().equals(archive.getDomain());
            BusinessObjectRef ref=new BusinessObjectRef(isRoot?rootId:id(rootId,archive.getDomain(),relation),
                archive.getDomain(),relation,archive.getCode(),isRoot?null:rootId);
            if(isRoot) { EsignValues.require(root==null,"银行账户选中关系重复。");root=ref; }
            else children.add(ref);
        }
        EsignValues.require(root!=null && children.size()<1000,"银行账户选中关系不存在或影响范围过大。");
        List<BusinessObjectRef> result=new ArrayList<BusinessObjectRef>();result.add(root);result.addAll(children);
        return new Expanded(selection,result);
    }

    public static String defaultCurrency(BusinessObjectRef selected) throws BusinessException {
        String id=selected.getClientObjectId();
        if(!id.startsWith(DEFAULT_PREFIX)) return null;
        String[] parts=id.substring(DEFAULT_PREFIX.length()).split(":",-1);
        EsignValues.require((parts.length==1 || parts.length==2) && parts[0].matches("[A-Za-z0-9_-]{1,20}"),
            "默认账户币种授权标识不正确。");
        return parts[0];
    }

    public static void requireDefaultCurrency(String operation,BusinessObjectRef selected,BankAccountImpactScope.Selection scope)
            throws BusinessException {
        String currency=defaultCurrency(selected);
        if(currency==null) return;
        EsignValues.require("UPDATE".equals(operation) && scope!=null,"设置默认账户必须使用银行账户修改授权。");
        List<SnapshotRow> rows=new PhysicalSnapshotReader().read("bd_bankaccsub","pk_bankaccsub",null,
            "pk_bankaccsub",currency,"pk_bankaccbas",scope.getAccount(),true);
        EsignValues.require(rows.size()==1,"默认账户币种不属于所选账户。");
    }
    /** Compares both the account identity and the complete relationship set with the signed request. */
    public static Expanded verify(String group,List<BusinessObjectRef> authorized,Reader reader,boolean lock) throws BusinessException {
        Expanded current=expand(group,root(authorized),reader,lock);
        verifyObjects(authorized,current.objects);
        return current;
    }
    private static void verifyObjects(List<BusinessObjectRef> authorized,List<BusinessObjectRef> current) throws BusinessException {
        Map<String,BusinessObjectRef> expected=index(authorized);
        for(BusinessObjectRef ref:current) {
            BusinessObjectRef old=expected.remove(ref.getClientObjectId());
            EsignValues.require(old!=null && Objects.equals(old.getObjectType(),ref.getObjectType())
                && Objects.equals(old.getObjectPk(),ref.getObjectPk()) && Objects.equals(old.getBillCode(),ref.getBillCode())
                && Objects.equals(old.getParentClientObjectId(),ref.getParentClientObjectId()) && old.getDesiredOwnerOrg()==null,
                "银行账户、关联档案或关系范围已变化，请重新发起操作。");
        }
        EsignValues.require(expected.isEmpty(),"银行账户关联关系已变化，请重新发起操作。");
    }
    public static BusinessObjectRef root(List<BusinessObjectRef> authorized) throws BusinessException {
        EsignValues.require(authorized!=null && !authorized.isEmpty() && authorized.size()<=1000,"银行账户授权范围为空或过大。");
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:authorized) if(ref!=null && ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"银行账户授权包含多个选中对象。");root=ref;
        }
        EsignValues.require(root!=null,"银行账户授权缺少选中对象。");
        return root;
    }
    private static Map<String,BusinessObjectRef> index(List<BusinessObjectRef> refs) throws BusinessException {
        Map<String,BusinessObjectRef> result=new HashMap<String,BusinessObjectRef>();
        for(BusinessObjectRef ref:refs) EsignValues.require(ref!=null && result.put(ref.getClientObjectId(),ref)==null,
            "银行账户授权包含重复对象。");
        return result;
    }
    private static String id(String account,String domain,String relation) {
        return "bank-"+UUID.nameUUIDFromBytes((account+"\u0000"+domain+"\u0000"+relation).getBytes(Charset.forName("UTF-8"))).toString();
    }
    public static Reader database() {
        return new Reader() {
            private final BankAccountImpactScope.Reader delegate=BankAccountImpactScope.database();
            public String accountForRelation(String relation,boolean lock) throws BusinessException {
                PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
                List<SnapshotRow> rows=lock?reader.readLocked("bd_custbank","pk_custbank",null,"pk_custbank",relation,true)
                    :reader.read("bd_custbank","pk_custbank",null,"pk_custbank",relation,true);
                EsignValues.require(rows.size()==1 && relation.equals(rows.get(0).getRowId()),"选中的银行账户关系不存在。");
                return rows.get(0).getValues().get("pk_bankaccbas");
            }
            public List<BankAccountImpactScope.Relation> relations(String account,boolean lock) throws BusinessException {
                return delegate.relations(account,lock);
            }
            public BankAccountImpactScope.Owner owner(String group,String domain,String relation,boolean lock) throws BusinessException {
                return delegate.owner(group,domain,relation,lock);
            }
        };
    }
}
