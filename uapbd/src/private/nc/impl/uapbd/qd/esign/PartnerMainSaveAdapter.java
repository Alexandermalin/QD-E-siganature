package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nc.bs.bd.baseservice.BDUniqueFieldTrimUtil;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PartnerMainSaveSnapshots;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.audit.WmsArchiveSnapshots;
import nc.bs.uapbd.qd.esign.core.BaseArchiveOwnerType;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.core.PartnerMainRequestScope;
import nc.bs.uapbd.qd.esign.core.PartnerMainSaveImpact;
import nc.bs.uapbd.qd.esign.core.PartnerMainSaveRoute;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.itf.bd.cust.baseinfo.ICustBaseInfoExtendService;
import nc.itf.bd.supplier.baseinfo.ISupBaseInfoExtendService;
import nc.itf.uapbd.qd.esign.IPartnerMainSaveAdapter;
import nc.ui.bd.pub.extend.PrivateServiceContext;
import nc.vo.bd.cust.CustomerVO;
import nc.vo.bd.supplier.SupplierVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.CircularlyAccessibleValueObject;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PartnerMainSaveCommand;
import nc.vo.uapbd.qd.esign.PartnerMainSaveDraft;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;
import nc.vo.util.bizlock.BizlockDataUtil;

/** Main-card updates and group inserts through the native extension services. */
public final class PartnerMainSaveAdapter implements IPartnerMainSaveAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { checked(context,command); }

    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        SuperVO value=checked(context,command).getValue();
        boolean customer=value instanceof CustomerVO;
        String metadata=customer?"e4f48eaf-5567-4383-a370-a59cb3e8a451":"720dcc7c-ff19-48f4-b9c5-b90906682f45";
        new BDUniqueFieldTrimUtil(metadata).trimUniqueFields(new SuperVO[]{value});
        if("UPDATE".equals(context.getOperationType())) BDPKLockUtil.lockSuperVO(new SuperVO[]{value});
        BizlockDataUtil.lockDataByBizlock(new CircularlyAccessibleValueObject[]{value});
        authorizedParent(context,value,true);
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        PartnerMainSaveCommand input=checked(context,command);
        SuperVO value=input.getValue();
        boolean insert="INSERT".equals(context.getOperationType());
        BusinessObjectRef root=PartnerMainRequestScope.root(context.getObjects());
        PartnerMainSaveImpact.Selection impact=authorizedParent(context,value,false);
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"客户或供应商保存缺少受控事务上下文。");
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        List<SnapshotRow> extension=Collections.emptyList();
        if(insert) {
            requireExternalPartner(value);
            PartnerMainRows.prepare(value,Collections.<SnapshotRow>emptyList());
            for(BusinessObjectRef ref:context.getObjects()) if(ref!=root && scope.isEnabledFor(ref.getClientObjectId()))
                scope.before(ref.getClientObjectId(),parentRows(reader,context,ref,true));
        } else {
            requireExistingRelationship(value);
            PartnerMainRows.prepare(value,PartnerMainRows.read(reader,value instanceof CustomerVO,value.getPrimaryKey(),true));
            Map<String,List<SnapshotRow>> before=PartnerMainSaveSnapshots.capture(context.getGroupId(),impact,Collections.<String,String>emptyMap(),false,
                PartnerMainSaveSnapshots.database(),true);
            if(value instanceof SupplierVO) extension=PartnerMainExtension.rows(reader,context.getGroupId(),value.getPrimaryKey(),true);
            capture(scope,reader,context,before,extension,root,true);
        }
        String parent=parent(value),related=related(value);
        if(insert) value.setAttributeValue(value instanceof CustomerVO?"pk_supplier":"corcustomer",null);
        PrivateServiceContext nativeContext=PartnerMainExtension.context(context.getNodeCode(),value,input.getExtensions(),extension);
        SuperVO saved=save(value,input,nativeContext,insert);
        String pk=saved==null?null:saved.getPrimaryKey();
        EsignValues.require(saved!=null && saved.getClass()==value.getClass() && pk!=null
            && (insert || root.getObjectPk().equals(pk)) && context.getGroupId().equals(saved.getAttributeValue("pk_group"))
            && Objects.equals(parent,parent(saved)) && Objects.equals(related,related(saved)),
            "原生保存改变了未经授权的档案、集团、关联或上级。");
        PartnerMainSaveImpact.Selection current=PartnerMainSaveImpact.select(context.getGroupId(),root.getObjectType(),pk,parent,false,
            PartnerMainSaveImpact.database(),false);
        EsignValues.require(current.hasCounterpart()==(!insert && impact.hasCounterpart()),"原生保存改变了客户供应商关联范围。");
        if(insert) {
            Map<String,List<SnapshotRow>> snapshot=PartnerMainSaveSnapshots.capture(context.getGroupId(),current,
                Collections.<String,String>emptyMap(),true,PartnerMainSaveSnapshots.database(),false);
            String identity=identity(root.getObjectType(),pk);
            if(scope.isEnabledFor(root.getClientObjectId())) {
                List<SnapshotRow> rows=new ArrayList<SnapshotRow>(snapshot.get(identity));
                rows.addAll(PartnerMainRows.read(reader,value instanceof CustomerVO,pk,false));
                if(value instanceof SupplierVO) rows.addAll(PartnerMainExtension.rows(reader,context.getGroupId(),pk,false));
                rows.addAll(WmsArchiveSnapshots.read(reader,root.getObjectType(),pk,false));
                scope.after(root.getClientObjectId(),rows);
            }
            for(BusinessObjectRef ref:context.getObjects()) if(ref!=root && scope.isEnabledFor(ref.getClientObjectId()))
                scope.after(ref.getClientObjectId(),parentRows(reader,context,ref,false));
        } else {
            Map<String,String> owners=new LinkedHashMap<String,String>();
            for(BusinessObjectRef ref:context.getObjects()) if(ref.getDesiredOwnerOrg()!=null)
                owners.put(identity(ref.getObjectType(),ref.getObjectPk()),ref.getDesiredOwnerOrg());
            Map<String,List<SnapshotRow>> after=PartnerMainSaveSnapshots.capture(context.getGroupId(),impact,owners,true,
                PartnerMainSaveSnapshots.database(),false);
            List<SnapshotRow> persistedExtension=value instanceof SupplierVO?PartnerMainExtension.rows(reader,context.getGroupId(),pk,false)
                :Collections.<SnapshotRow>emptyList();
            capture(scope,reader,context,after,persistedExtension,root,false);
        }
        return saved;
    }

    private static void capture(AuditCaptureScope scope,PhysicalSnapshotReader reader,OperationContext context,
            Map<String,List<SnapshotRow>> main,List<SnapshotRow> selectedExtension,BusinessObjectRef root,boolean before) throws BusinessException {
        for(BusinessObjectRef ref:context.getObjects()) {
            if(!scope.isEnabledFor(ref.getClientObjectId())) continue;
            List<SnapshotRow> rows=new ArrayList<SnapshotRow>(main.get(identity(ref.getObjectType(),ref.getObjectPk())));
            if(ref==root) rows.addAll(PartnerMainRows.read(reader,"BD_CUSTOMER".equals(ref.getObjectType()),ref.getObjectPk(),before));
            if("BD_SUPPLIER".equals(ref.getObjectType())) rows.addAll(ref==root?selectedExtension:
                PartnerMainExtension.rows(reader,context.getGroupId(),ref.getObjectPk(),before));
            rows.addAll(WmsArchiveSnapshots.read(reader,ref.getObjectType(),ref.getObjectPk(),before));
            if(before) scope.before(ref.getClientObjectId(),rows);
            else scope.after(ref.getClientObjectId(),rows);
        }
    }

    private static List<SnapshotRow> parentRows(PhysicalSnapshotReader reader,OperationContext context,BusinessObjectRef ref,boolean lock)
            throws BusinessException {
        BaseArchiveOwnerType type=BaseArchiveOwnerType.forDomain(ref.getObjectType());
        String pk=ref.getObjectPk();
        List<SnapshotRow> rows=lock?reader.readLocked(type.rootTable,type.rootKey,null,type.rootKey,pk,"pk_group",context.getGroupId(),true)
            :reader.read(type.rootTable,type.rootKey,null,type.rootKey,pk,"pk_group",context.getGroupId(),true);
        EsignValues.require(rows.size()==1 && ref.getPolicyOwnerOrg().equals(rows.get(0).getValues().get("pk_org")),
            "新增档案的上级或所属组织已变化。");
        String structure=type==BaseArchiveOwnerType.CUSTOMER?"bd_custstructure":"bd_supstructure";
        rows.addAll(lock?reader.readLocked(structure,type.rootKey,null,type.rootKey,pk,false)
            :reader.read(structure,type.rootKey,null,type.rootKey,pk,false));
        return rows;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof CustomerVO || result instanceof SupplierVO,"客户或供应商保存结果无效。");
        SuperVO saved=(SuperVO)result;
        BusinessObjectRef root=PartnerMainRequestScope.root(context.getObjects());
        QdEsignStore store=new QdEsignStore();
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        for(BusinessObjectRef ref:context.getObjects()) {
            String pk=ref==root?saved.getPrimaryKey():ref.getObjectPk();
            Map<String,Object> row=store.loadBaseArchiveOwner(context.getGroupId(),ref.getObjectType(),pk,false);
            EsignValues.require(row!=null && EsignValues.text(row,"code")!=null,"无法核实主档保存后的编码。");
            refs.add(new BusinessObjectRef(ref.getClientObjectId(),ref.getObjectType(),pk,EsignValues.text(row,"code"),ref.getParentClientObjectId()));
        }
        return refs;
    }

    private static SuperVO save(SuperVO value,PartnerMainSaveCommand input,PrivateServiceContext extensions,boolean insert)
            throws BusinessException {
        NCLocator locator=NCLocator.getInstance();
        if(value instanceof CustomerVO) {
            ICustBaseInfoExtendService service=locator.lookup(ICustBaseInfoExtendService.class);
            return insert?service.insertCustomerWithExtendVO((CustomerVO)value,input.getExtensions(),extensions,input.isSkipTaxMatch())
                :service.updateCustomerWithExtendVO((CustomerVO)value,input.getExtensions(),extensions,input.isSkipTaxMatch());
        }
        ISupBaseInfoExtendService service=locator.lookup(ISupBaseInfoExtendService.class);
        return insert?service.insertSupplierWithExtendVO((SupplierVO)value,input.getExtensions(),extensions,input.isSkipTaxMatch())
            :service.updateSupplierWithExtendVO((SupplierVO)value,input.getExtensions(),extensions,input.isSkipTaxMatch());
    }

    private static PartnerMainSaveImpact.Selection authorizedParent(OperationContext context,SuperVO value,boolean lock) throws BusinessException {
        PartnerMainSaveImpact.Reader reader=PartnerMainSaveImpact.database();
        if("INSERT".equals(context.getOperationType())) {
            String authorized=PartnerMainRequestScope.verifyInsert(context.getGroupId(),context.getObjects(),reader,lock);
            EsignValues.require(Objects.equals(authorized,parent(value)),"新增档案上级与授权不一致。");
            return null;
        }
        PartnerMainSaveImpact.Selection impact=PartnerMainRequestScope.verify(context.getGroupId(),context.getObjects(),reader,lock).getImpact();
        EsignValues.require(Objects.equals(impact.getDesiredParent(),parent(value)),"主档保存上级与授权不一致。");
        return impact;
    }

    private static void requireExistingRelationship(SuperVO value) throws BusinessException {
        SuperVO stored=(SuperVO)new BaseDAO().retrieveByPK(value.getClass(),value.getPrimaryKey());
        boolean customer=value instanceof CustomerVO;
        String relation=related(value);
        EsignValues.require(stored!=null && Objects.equals(related(stored),relation)
            && (relation==null || relation.equals(value.getPrimaryKey()))
            && Objects.equals(stored.getAttributeValue(customer?"issupplier":"iscustomer"),
                value.getAttributeValue(customer?"issupplier":"iscustomer")),
            "主档保存涉及新的客户供应商关联，请核实关联范围。");
        for(String field:new String[]{"enablestate","deletestate","dr"}) {
            EsignValues.require(Objects.equals(stored.getAttributeValue(field),value.getAttributeValue(field)),
                "主档保存改变了档案状态："+field);
        }
        if(customer) EsignValues.require(Objects.equals(stored.getAttributeValue("frozenflag"),value.getAttributeValue("frozenflag")),
            "客户冻结状态需从冻结或解冻入口变更。");
        String property=customer?"custprop":"supprop";
        EsignValues.require(Objects.equals(stored.getAttributeValue(property),value.getAttributeValue(property))
            && Objects.equals(stored.getAttributeValue("pk_financeorg"),value.getAttributeValue("pk_financeorg")),
            "变更内外部属性或财务组织会触发银行账户级联，需核实级联范围。");
    }

    private static void requireExternalPartner(SuperVO value) throws BusinessException {
        boolean customer=value instanceof CustomerVO;
        Object property=value.getAttributeValue(customer?"custprop":"supprop");
        EsignValues.require((property==null || Integer.valueOf(0).equals(property))
            && PartnerMainSaveDraft.normalizeParent(text(value,"pk_financeorg"))==null,
            "新增内部档案会同步银行账户，需核实级联范围。");
        EsignValues.require(PartnerMainSaveDraft.normalizeParent(text(value,customer?"pk_customerpf":"pk_supplier_pf"))==null
            && !"Y".equals(String.valueOf(value.getAttributeValue(customer?"issupplier":"iscustomer"))),
            "新增主档包含审批来源或现有档案关联。");
    }

    private static PartnerMainSaveCommand checked(OperationContext context,Serializable command) throws BusinessException {
        EsignValues.require(context!=null && PartnerMainSaveRoute.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null
            && context.getOrgId()!=null && command instanceof PartnerMainSaveCommand,"客户或供应商保存载荷或入口不正确。");
        PartnerMainSaveCommand input=(PartnerMainSaveCommand)command;
        SuperVO value=input.getValue();
        BusinessObjectRef root=PartnerMainRequestScope.root(context.getObjects());
        String domain=PartnerMainSaveRoute.selectedDomain(context.getNodeCode());
        Class<?> type="BD_CUSTOMER".equals(domain)?CustomerVO.class:SupplierVO.class;
        String owner=root.getDesiredOwnerOrg()==null?root.getPolicyOwnerOrg():root.getDesiredOwnerOrg();
        EsignValues.require(domain.equals(root.getObjectType()) && value!=null && value.getClass()==type
            && context.getGroupId().equals(value.getAttributeValue("pk_group")) && owner!=null && owner.equals(value.getAttributeValue("pk_org"))
            && Objects.equals(root.getObjectPk(),value.getPrimaryKey()),"客户或供应商保存与授权主档不一致。");
        if("INSERT".equals(context.getOperationType())) EsignValues.require(context.getGroupId().equals(context.getOrgId())
            && context.getGroupId().equals(owner) && value.getPrimaryKey()==null && related(value)==null,
            "新增主档的组织或客户供应商关联超出授权范围。");
        return input;
    }

    private static String identity(String domain,String pk) { return domain+"\u0000"+pk; }
    private static String parent(SuperVO value) {
        return PartnerMainSaveDraft.normalizeParent(text(value,value instanceof CustomerVO?"pk_customer_main":"pk_supplier_main"));
    }
    private static String related(SuperVO value) {
        return PartnerMainSaveDraft.normalizeParent(text(value,value instanceof CustomerVO?"pk_supplier":"corcustomer"));
    }
    private static String text(SuperVO value,String field) {
        Object raw=value.getAttributeValue(field);
        return raw instanceof String && !((String)raw).trim().isEmpty()?(String)raw:null;
    }
}
