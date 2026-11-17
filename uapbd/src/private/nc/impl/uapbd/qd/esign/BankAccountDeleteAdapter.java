package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uif2.validation.ValidationFrameworkUtil;
import nc.bs.uif2.validation.Validator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.BankAccountDatabaseLock;
import nc.bs.uapbd.qd.esign.core.BankAccountImpactScope;
import nc.bs.uapbd.qd.esign.core.BankAccountRequestScope;
import nc.bs.uapbd.qd.esign.core.BankAccountRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.itf.bd.bankacc.cust.ICustBankaccService;
import nc.itf.uapbd.qd.esign.IBankAccountDeleteAdapter;
import nc.vo.bd.bankaccount.BankAccbasVO;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.bd.pub.DistributedSubEntiryValidator;
import nc.vo.bd.pub.SingleDistributedDeleteValidator;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BankAccountDeleteCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/**
 * 银行账户删除的受控适配器。
 * 删除同一客户供应商主键下的币种关系。其他档案仍引用账户时，调用原生关系删除服务，
 * 并在提交前核实账户和其他档案的关系仍然完整。
 */
public final class BankAccountDeleteAdapter implements IBankAccountDeleteAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,command); }

    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        draft(context,command);
        BankAccountRequestScope.Expanded impact=BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),
            BankAccountRequestScope.database(),false);
        String archive=BankAccountSaveAdapter.selectedArchivePk(impact.getSelection());
        BDPKLockUtil.lockString(new String[]{archive});
        BankAccbasVO account=BankAccountSaveAdapter.account(impact.getSelection().getAccount());
        EsignValues.require(account!=null && context.getGroupId().equals(account.getPk_group()),"银行账户不属于当前集团。");
        BDPKLockUtil.lockSuperVO(new SuperVO[]{account});
        // 原生业务锁之后取数据库级统一账户锁，与保存、启停共用同一锁定顺序。
        new BankAccountDatabaseLock().acquire(account.getPk_bankaccbas());
        BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),BankAccountRequestScope.database(),true);
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        BankAccountDeleteCommand input=draft(context,command);
        BankAccountRequestScope.Expanded impact=BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),
            BankAccountRequestScope.database(),false);
        BankAccountImpactScope.Selection selection=impact.getSelection();
        CustbankVO relation=input.getUnion().getCustbankVO();
        String archive=BankAccountSaveAdapter.selectedArchivePk(selection);
        EsignValues.require(relation.getPk_custbank().equals(selection.getSelectedRelation())
            && selection.getAccount().equals(relation.getPk_bankaccbas()) && archive.equals(relation.getPk_cust()),
            "银行账户删除选中的关系已变化，请重新发起。");
        Set<String> archives=BankAccountSaveEffects.archiveIds(selection);
        boolean deleteAccount=archives.size()==1;
        BankAccbasVO stored=BankAccountSaveAdapter.account(selection.getAccount());
        EsignValues.require(stored!=null && context.getGroupId().equals(stored.getPk_group())
            && stored.getTs()!=null && input.getVersion()!=null && input.getVersion().equals(stored.getTs().toString()),
            "银行账户已被修改，请刷新后重试。");
        CustbankVO current=(CustbankVO)new BaseDAO().retrieveByPK(CustbankVO.class,selection.getSelectedRelation());
        EsignValues.require(current!=null && archive.equals(current.getPk_cust()) && selection.getAccount().equals(current.getPk_bankaccbas())
            && current.getAccclass().equals(relation.getAccclass()) && java.util.Objects.equals(current.getTs(),relation.getTs()),
            "银行账户关系已被修改，请刷新后重试。");
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"银行账户删除缺少受控事务上下文。");
        Map<String,List<SnapshotRow>> before=BankAccountSaveEffects.accountRows(context.getGroupId(),selection,true);
        Map<String,SnapshotRow> previous=BankAccountSaveEffects.siblingRelations(archives,selection.getAccount(),true);
        input.getUnion().setBankaccbasVO(stored);
        input.getUnion().setCustbankVO(current);
        ICustBankaccService service=NCLocator.getInstance().lookup(ICustBankaccService.class);
        Set<String> removed=new HashSet<String>(),retained=new HashSet<String>();
        for(BankAccountImpactScope.AffectedArchive affected:selection.getArchives()) {
            if(archive.equals(affected.getArchive())) removed.addAll(affected.getRelations());
            else retained.addAll(affected.getRelations());
        }
        if(deleteAccount) service.deleteCustBankacc(input.getUnion());
        else detach(service,selection,archive,removed);
        Set<String> remaining=new HashSet<String>();
        for(BankAccountImpactScope.Relation link:BankAccountImpactScope.database().relations(selection.getAccount(),false))
            remaining.add(link.id);
        EsignValues.require(remaining.equals(retained),"原生删除后的账户关系范围不一致，业务必须回滚。");
        Map<String,List<SnapshotRow>> after=verifyDeleted(context,selection,before,removed,deleteAccount);
        BankAccountSaveEffects.includeDefaultChanges(before,after,previous,
            BankAccountSaveEffects.siblingRelations(archives,selection.getAccount(),false));
        BankAccountSaveEffects.capture(context,selection,scope,before,after);
        return input.getUnion();
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof CustBankaccUnionVO,"银行账户删除结果类型不正确。");return context.getObjects();
    }

    private static void detach(ICustBankaccService service,BankAccountImpactScope.Selection selection,String archive,Set<String> removed)
            throws BusinessException {
        List<CustbankVO> rows=new ArrayList<CustbankVO>();
        BaseDAO dao=new BaseDAO();
        for(BankAccountImpactScope.AffectedArchive affected:selection.getArchives()) if(archive.equals(affected.getArchive())) {
            boolean customer="BD_CUSTOMER_BANK".equals(affected.getDomain());
            String metadata=customer?"e4f48eaf-5567-4383-a370-a59cb3e8a451":"720dcc7c-ff19-48f4-b9c5-b90906682f45";
            String bankMetadata=customer?"916e1bc9-8f73-4c22-a096-6550c9ba7a70":"e839198a-f12b-461c-b165-1639b6e2e3ce";
            new DistributedSubEntiryValidator().distributedEditCheck(metadata,bankMetadata,new String[]{archive});
            for(String pk:affected.getRelations()) {
                CustbankVO row=(CustbankVO)dao.retrieveByPK(CustbankVO.class,pk);
                EsignValues.require(row!=null && archive.equals(row.getPk_cust()) && selection.getAccount().equals(row.getPk_bankaccbas())
                    && (customer?Integer.valueOf(1):Integer.valueOf(3)).equals(row.getAccclass()),"待移除的共享账户关系已变化。");
                ValidationFrameworkUtil.createValidationService(new Validator[]{new SingleDistributedDeleteValidator()}).validate(row);
                rows.add(row);
            }
        }
        EsignValues.require(rows.size()==removed.size() && !rows.isEmpty(),"无法核实待移除的共享账户关系。");
        service.deleteCustBankVOs(rows.toArray(new CustbankVO[rows.size()]));
    }

    private static Map<String,List<SnapshotRow>> verifyDeleted(OperationContext context,BankAccountImpactScope.Selection selection,
            Map<String,List<SnapshotRow>> before,Set<String> removed,boolean deleteAccount) throws BusinessException {
        PhysicalSnapshotReader reader=new PhysicalSnapshotReader();
        Map<String,List<SnapshotRow>> after=new LinkedHashMap<String,List<SnapshotRow>>();
        Map<String,List<SnapshotRow>> checked=new LinkedHashMap<String,List<SnapshotRow>>();
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives()) {
            String key=BankAccountSaveEffects.key(archive.getDomain(),archive.getArchive());
            List<SnapshotRow> retained=new ArrayList<SnapshotRow>();
            for(SnapshotRow row:before.get(key)) {
                String table=row.getEntity();
                if("bd_customer".equals(table) || "bd_supplier".equals(table)) {
                    List<SnapshotRow> roots=reader.readByPk(table,"bd_customer".equals(table)?"pk_customer":"pk_supplier",row.getRowId());
                    EsignValues.require(roots.size()==1 && archive.getOwner().equals(roots.get(0).getValues().get("pk_org"))
                        && context.getGroupId().equals(roots.get(0).getValues().get("pk_group")),"银行账户删除后父档案或所属组织已变化。");
                    retained.add(roots.get(0));
                } else {
                    String identity=BankAccountSaveEffects.key(table,row.getRowId());
                    List<SnapshotRow> current=checked.get(identity);
                    if(current==null) {
                        current=reader.readByPk(table,"pk_"+table.substring(3),row.getRowId());
                        checked.put(identity,current);
                    }
                    boolean deleted=deleteAccount || "bd_custbank".equals(table) && removed.contains(row.getRowId());
                    EsignValues.require(deleted?current.isEmpty():current.size()==1 && row.getValues().equals(current.get(0).getValues()),
                        "银行账户或子行的删除范围不一致，业务必须回滚。");
                    if(!deleted) retained.add(row);
                }
            }
            after.put(key,retained);
        }
        return after;
    }

    private static BankAccountDeleteCommand draft(OperationContext context,Serializable value) throws BusinessException {
        EsignValues.require(context!=null && isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && context.getGroupId()!=null && context.getOrgId()!=null
            && value instanceof BankAccountDeleteCommand,"银行账户删除载荷或节点不正确。");
        BankAccountDeleteCommand command=(BankAccountDeleteCommand)value;
        CustBankaccUnionVO union=command.getUnion();
        BusinessObjectRef root=root(context);
        boolean customer="BD_CUSTOMER_BANK".equals(root.getObjectType());
        EsignValues.require(("10140CBA".equals(context.getNodeCode()))==customer,"银行账户删除节点与档案类型不一致。");
        CustbankVO relation=union.getCustbankVO();
        EsignValues.require(union.getBankaccbasVO()!=null && relation!=null
            && context.getGroupId().equals(union.getBankaccbasVO().getPk_group())
            && relation.getPk_custbank()!=null && relation.getPk_custbank().equals(root.getObjectPk())
            && relation.getPk_bankaccbas()!=null && union.getBankaccbasVO().getPk_bankaccbas()!=null
            && relation.getPk_bankaccbas().equals(union.getBankaccbasVO().getPk_bankaccbas())
            && (customer?Integer.valueOf(1):Integer.valueOf(3)).equals(relation.getAccclass())
            && command.getVersion()!=null && command.getVersion().length()<=80,
            "银行账户删除载荷或授权对象不正确。");
        for(BusinessObjectRef ref:context.getObjects()) EsignValues.require(ref.getPolicyOwnerOrg()!=null
            && ref.getObjectPk()!=null && (ref==root || root.getClientObjectId().equals(ref.getParentClientObjectId())),
            "银行账户删除影响对象不完整。");
        return command;
    }

    static boolean isRoute(String node,String button,String operation) {
        return "DELETE".equals(operation) && BankAccountRoute.matches(node,button,operation);
    }

    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"银行账户删除存在多个选中对象。");root=ref;
        }
        EsignValues.require(root!=null && ("BD_CUSTOMER_BANK".equals(root.getObjectType())
            || "BD_SUPPLIER_BANK".equals(root.getObjectType())),"银行账户删除缺少选中对象。");return root;
    }

}
