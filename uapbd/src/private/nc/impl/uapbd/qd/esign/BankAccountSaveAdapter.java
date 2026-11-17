package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.PhysicalSnapshotReader;
import nc.bs.uapbd.qd.esign.core.BankAccountDatabaseLock;
import nc.bs.uapbd.qd.esign.core.BankAccountImpactScope;
import nc.bs.uapbd.qd.esign.core.BankAccountRequestScope;
import nc.bs.uapbd.qd.esign.core.BankAccountRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.itf.bd.bankacc.cust.ICustBankaccService;
import nc.itf.uapbd.qd.esign.IBankAccountSaveAdapter;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.bankaccount.BankAccbasVO;
import nc.vo.bd.bankaccount.BankAccSubVO;
import nc.vo.bd.bankaccount.BankAccUseVO;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BankAccountSaveCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;
import nc.vo.pub.SuperVO;
import nc.vo.pub.lang.UFBoolean;

/**
 * 银行账户保存（新增/修改）的受控适配器。
 * 修改共享账户影响全部关联档案：先按原生顺序取得档案与账户业务锁，再取数据库级统一账户锁，
 * 展开影响范围并按档案所属组织逐项解析策略；版本核对把并发过期修改变成明确失败。
 * 新增只作用于单个档案，原生可能创建银行档案；新增只需执行后快照，修改需要前后快照。
 */
public final class BankAccountSaveAdapter implements IBankAccountSaveAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { draft(context,command); }

    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        BankAccountSaveCommand input=draft(context,command);
        if("UPDATE".equals(context.getOperationType())) {
            BankAccountRequestScope.Expanded impact=BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),
                BankAccountRequestScope.database(),false);
            requireSelected(context,input,impact.getSelection());
            BDPKLockUtil.lockString(new String[]{selectedArchivePk(impact.getSelection())});
            BankAccbasVO account=account(input.getUnion().getBankaccbasVO().getPk_bankaccbas());
            EsignValues.require(account!=null && context.getGroupId().equals(account.getPk_group()),"银行账户不属于当前集团。");
            BDPKLockUtil.lockSuperVO(new SuperVO[]{account});
            // 原生业务锁之后取数据库级统一账户锁，与删除、启停共用同一锁定顺序。
            new BankAccountDatabaseLock().acquire(account.getPk_bankaccbas());
            BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),BankAccountRequestScope.database(),true);
        } else {
            CustbankVO relation=input.getUnion().getCustbankVO();
            BDPKLockUtil.lockString(new String[]{relation.getPk_cust()});
            BankAccountRequestScope.verifyInsert(context.getGroupId(),context.getObjects(),true);
        }
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        BankAccountSaveCommand input=draft(context,command);
        CustBankaccUnionVO union=input.getUnion();
        ICustBankaccService service=NCLocator.getInstance().lookup(ICustBankaccService.class);
        AuditCaptureScope scope=AuditCaptureScope.current();
        EsignValues.require(scope!=null,"银行账户保存缺少受控事务上下文。");
        return "INSERT".equals(context.getOperationType())?insert(context,union,service,scope)
            :update(context,input,union,service,scope);
    }

    /** NC may add one relation per currency for both roles of the same customer/supplier. */
    private static Serializable insert(OperationContext context,CustBankaccUnionVO union,
            ICustBankaccService service,AuditCaptureScope scope) throws BusinessException {
        BusinessObjectRef root=root(context);
        boolean customer="BD_CUSTOMER_BANK".equals(root.getObjectType());
        CustbankVO relation=union.getCustbankVO();
        String archive=relation.getPk_cust();
        BankAccountRequestScope.verifyInsert(context.getGroupId(),context.getObjects(),false);
        Set<String> archives=Collections.singleton(archive);
        Map<String,SnapshotRow> previous=BankAccountSaveEffects.siblingRelations(archives,null,true);
        Map<String,SnapshotRow> banks=bankDocsBefore(context,union.getBankaccbasVO());
        validateAccountRows(union.getBankaccbasVO(),null);
        CustBankaccUnionVO result=service.insertCustBankacc(union);
        requireUnion(result,archive,customer);
        BankAccountImpactScope.Selection current=savedSelection(context,root,result);
        Map<String,String> expected=new LinkedHashMap<String,String>();
        for(BusinessObjectRef ref:context.getObjects()) expected.put(BankAccountSaveEffects.key(ref.getObjectType(),archive),ref.getPolicyOwnerOrg());
        EsignValues.require(expected.equals(BankAccountSaveEffects.archiveKeys(current)),"原生新增改变了未授权的银行账户关联档案。");
        Map<String,List<SnapshotRow>> before=new LinkedHashMap<String,List<SnapshotRow>>();
        Map<String,List<SnapshotRow>> after=BankAccountSaveEffects.accountRows(context.getGroupId(),current,false);
        BankAccountSaveEffects.includeDefaultChanges(before,after,previous,
            BankAccountSaveEffects.siblingRelations(archives,current.getAccount(),false));
        includeBankDoc(context,root,archive,banks,result,before,after);
        BankAccountSaveEffects.capture(context,null,scope,before,after);
        return result;
    }

    /** 修改：共享账户影响全部关联档案；锁内核对版本，采集前后快照并复核剩余关系未变化。 */
    private static Serializable update(OperationContext context,BankAccountSaveCommand input,CustBankaccUnionVO union,
            ICustBankaccService service,AuditCaptureScope scope) throws BusinessException {
        BankAccountRequestScope.Expanded impact=BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),
            BankAccountRequestScope.database(),false);
        BankAccountImpactScope.Selection selection=impact.getSelection();
        requireSelected(context,input,selection);
        BusinessObjectRef root=root(context);
        boolean customer="BD_CUSTOMER_BANK".equals(root.getObjectType());
        BankAccbasVO stored=account(union.getBankaccbasVO().getPk_bankaccbas());
        EsignValues.require(stored!=null && context.getGroupId().equals(stored.getPk_group())
            && stored.getTs()!=null && input.getVersion()!=null && input.getVersion().equals(stored.getTs().toString()),
            "银行账户已被修改，请刷新后重试。");
        EsignValues.require(Objects.equals(stored.getPk_org(),union.getBankaccbasVO().getPk_org()),"银行账户所属组织已变化。");
        String archive=selectedArchivePk(selection);
        Set<String> archives=BankAccountSaveEffects.archiveIds(selection);
        Map<String,SnapshotRow> previous=BankAccountSaveEffects.siblingRelations(archives,selection.getAccount(),true);
        Map<String,List<SnapshotRow>> before=BankAccountSaveEffects.accountRows(context.getGroupId(),selection,true);
        String currency=input.getDefaultCurrency();
        String archiveKey=BankAccountSaveEffects.key(root.getObjectType(),archive);
        Map<String,SnapshotRow> banks=null;
        Serializable nativeResult;
        CustBankaccUnionVO result;
        if(currency==null) {
            validateAccountRows(union.getBankaccbasVO(),stored);
            banks=bankDocsBefore(context,union.getBankaccbasVO());
            result=service.updateCustBankacc(union);
            nativeResult=result;
        } else {
            BankAccountRequestScope.requireDefaultCurrency(context.getOperationType(),root,selection);
            EsignValues.require(!UFBoolean.TRUE.equals(stored.getSharetag()),"原生共享标记的银行账户不能设置默认币种。");
            requireCurrencyRelation(before.get(archiveKey),currency,false);
            CustbankVO link=(CustbankVO)new BaseDAO().retrieveByPK(CustbankVO.class,selection.getSelectedRelation());
            EsignValues.require(link!=null && Objects.equals(link.getTs(),union.getCustbankVO().getTs()),
                "银行账户关系已被修改，请刷新后重试。");
            union.setCustbankVO(link);
            union.setBankaccbasVO(stored);
            CustBankaccUnionVO[] refreshed=service.processIsDefaultOperation(union,currency);
            result=selectedResult(refreshed,selection.getAccount());
            nativeResult=refreshed;
        }
        requireUnion(result,archive,customer);
        BankAccountImpactScope.Selection current=savedSelection(context,root,result);
        EsignValues.require(selection.getAccount().equals(current.getAccount())
            && BankAccountSaveEffects.archiveKeys(selection).equals(BankAccountSaveEffects.archiveKeys(current)),
            "原生修改改变了未授权的银行账户关联档案。");
        Map<String,List<SnapshotRow>> after=BankAccountSaveEffects.accountRows(context.getGroupId(),current,false);
        Map<String,SnapshotRow> siblings=BankAccountSaveEffects.siblingRelations(archives,current.getAccount(),false);
        if(currency!=null) {
            BankAccountSaveEffects.requireOnlyDefaultChanges(before,after);
            requireCurrencyRelation(after.get(archiveKey),currency,true);
            for(SnapshotRow relation:siblings.values()) EsignValues.require(!archive.equals(relation.getParentRowId())
                || !(customer?"1":"3").equals(relation.getValues().get("accclass")) || !"Y".equals(relation.getValues().get("isdefault")),
                "同一档案仍有其他默认银行账户，业务必须回滚。");
        }
        BankAccountSaveEffects.includeDefaultChanges(before,after,previous,siblings);
        includeBankDoc(context,root,archive,banks,result,before,after);
        BankAccountSaveEffects.capture(context,selection,scope,before,after);
        return nativeResult;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        BusinessObjectRef root=root(context);
        CustBankaccUnionVO saved;
        if(result instanceof CustBankaccUnionVO[]) {
            EsignValues.require(BankAccountRequestScope.defaultCurrency(root)!=null,"银行账户保存结果类型不正确。");
            saved=selectedResult((CustBankaccUnionVO[])result,
                BankAccountRequestScope.database().accountForRelation(root.getObjectPk(),false));
        } else {
            EsignValues.require(result instanceof CustBankaccUnionVO,"银行账户保存结果类型不正确。");
            saved=(CustBankaccUnionVO)result;
        }
        BankAccountImpactScope.Selection current=savedSelection(context,root,saved);
        Map<String,BankAccountImpactScope.AffectedArchive> affected=new LinkedHashMap<String,BankAccountImpactScope.AffectedArchive>();
        for(BankAccountImpactScope.AffectedArchive archive:current.getArchives())
            affected.put(BankAccountSaveEffects.key(archive.getDomain(),archive.getArchive()),archive);
        List<BusinessObjectRef> refs=new ArrayList<BusinessObjectRef>();
        Map<String,String> parents=new LinkedHashMap<String,String>();
        for(BusinessObjectRef ref:context.getObjects())parents.put(ref.getClientObjectId(),ref.getParentClientObjectId());
        for(Map<String,Object> row:new QdEsignStore().loadObjects(context.getRequestId())) {
            String domain=EsignValues.text(row,"object_type"),client=EsignValues.text(row,"client_object_id");
            EsignValues.require(parents.containsKey(client),"银行账户保存结果包含未授权对象。");
            BankAccountImpactScope.AffectedArchive archive=affected.get(BankAccountSaveEffects.key(domain,EsignValues.text(row,"policy_owner_root")));
            EsignValues.require(archive!=null && !archive.getRelations().isEmpty(),"无法核实银行账户保存后的关系。");
            String old=EsignValues.text(row,"object_pk");
            String relation=archive.getRelations().contains(old)?old:archive.getRelations().get(0);
            refs.add(new BusinessObjectRef(client,domain,relation,archive.getCode(),parents.remove(client)));
        }
        EsignValues.require(parents.isEmpty(),"银行账户保存结果缺少授权对象。");
        return refs;
    }

    private static CustBankaccUnionVO selectedResult(CustBankaccUnionVO[] rows,String account) throws BusinessException {
        CustBankaccUnionVO selected=null;
        if(rows!=null) for(CustBankaccUnionVO row:rows) {
            if(row==null || row.getBankaccbasVO()==null || !account.equals(row.getBankaccbasVO().getPk_bankaccbas())) continue;
            EsignValues.require(selected==null,"默认银行账户返回了重复结果。");
            selected=row;
        }
        EsignValues.require(selected!=null,"无法核实原生默认银行账户结果。");
        return selected;
    }

    private static void requireCurrencyRelation(List<SnapshotRow> rows,String currency,boolean selected) throws BusinessException {
        int matches=0;
        for(SnapshotRow row:rows) if("bd_custbank".equals(row.getEntity())) {
            boolean target=currency.equals(row.getValues().get("pk_bankaccsub"));
            if(target) matches++;
            boolean defaulted="Y".equals(row.getValues().get("isdefault"));
            EsignValues.require(selected?target==defaulted:!target || !defaulted,"银行账户默认币种已变化或未正确写入。");
        }
        EsignValues.require(matches==1,"无法核实银行账户所选币种的关联行。");
    }

    private static BankAccountImpactScope.Selection savedSelection(OperationContext context,BusinessObjectRef root,
            CustBankaccUnionVO result) throws BusinessException {
        return BankAccountImpactScope.select(context.getGroupId(),result.getBankaccbasVO().getPk_bankaccbas(),
            new BusinessObjectRef(root.getClientObjectId(),root.getObjectType(),result.getCustbankVO().getPk_custbank(),null,null),
            BankAccountImpactScope.database(),false);
    }

    private static void requireUnion(CustBankaccUnionVO result,String archivePk,boolean customer) throws BusinessException {
        EsignValues.require(result!=null && result.getBankaccbasVO()!=null && result.getCustbankVO()!=null
            && result.getBankaccbasVO().getPk_bankaccbas()!=null && result.getCustbankVO().getPk_custbank()!=null
            && archivePk!=null && archivePk.equals(result.getCustbankVO().getPk_cust())
            && (customer?Integer.valueOf(1):Integer.valueOf(3)).equals(result.getCustbankVO().getAccclass()),
            "原生银行账户保存结果与授权档案不一致。");
    }

    private static Map<String,SnapshotRow> bankDocsBefore(OperationContext context,BankAccbasVO draft) throws BusinessException {
        if(!blank(draft.getPk_bankdoc()) || blank(draft.getPk_banktype()) || blank(draft.getName())) return null;
        Map<String,SnapshotRow> result=new LinkedHashMap<String,SnapshotRow>();
        for(SnapshotRow row:new PhysicalSnapshotReader().read("bd_bankdoc","pk_bankdoc",null,"name",draft.getName(),
                "pk_group",context.getGroupId(),true)) result.put(row.getRowId(),row);
        return result;
    }
    private static void includeBankDoc(OperationContext context,BusinessObjectRef root,String archive,Map<String,SnapshotRow> previous,
            CustBankaccUnionVO result,Map<String,List<SnapshotRow>> before,Map<String,List<SnapshotRow>> after) throws BusinessException {
        if(previous==null) return;
        String id=result.getBankaccbasVO().getPk_bankdoc();
        EsignValues.require(!blank(id),"原生银行账户保存未返回开户银行。");
        List<SnapshotRow> rows=new PhysicalSnapshotReader().read("bd_bankdoc","pk_bankdoc",null,"pk_bankdoc",id,
            "pk_group",context.getGroupId(),true);
        EsignValues.require(rows.size()==1,"无法核实原生保存返回的开户银行。");
        SnapshotRow old=previous.get(id);
        if(old!=null && old.getValues().equals(rows.get(0).getValues())) return;
        String key=BankAccountSaveEffects.key(root.getObjectType(),archive);
        if(old!=null) {
            List<SnapshotRow> values=before.get(key);
            if(values==null) { values=new ArrayList<SnapshotRow>();before.put(key,values); }
            values.add(old);
        }
        after.get(key).addAll(rows);
    }
    private static boolean blank(String value) { return value==null || value.trim().isEmpty(); }

    static void validateAccountRows(BankAccbasVO draft,BankAccbasVO stored) throws BusinessException {
        Map<String,BankAccSubVO> currencies=new HashMap<String,BankAccSubVO>();
        if(stored!=null && stored.getBankaccsub()!=null) for(BankAccSubVO row:stored.getBankaccsub())
            currencies.put(row.getPk_bankaccsub(),row);
        BankAccSubVO[] rows=draft.getBankaccsub();
        EsignValues.require(rows!=null && rows.length>0,"银行账户缺少币种子行。");
        Set<String> seen=new HashSet<String>();
        for(BankAccSubVO row:rows) {
            EsignValues.require(row!=null && row.getClass()==BankAccSubVO.class,"银行账户币种载荷不正确。");
            String pk=row.getPk_bankaccsub(),parent=row.getPk_bankaccbas();
            EsignValues.require((parent==null || parent.equals(draft.getPk_bankaccbas()))
                && (pk==null || currencies.containsKey(pk) && seen.add(pk)),"银行账户币种子行属于其他账户或重复。");
            validateUseRows(draft.getPk_bankaccbas(),row,currencies.get(pk));
        }
    }

    private static void validateUseRows(String account,BankAccSubVO currency,BankAccSubVO stored) throws BusinessException {
        Set<String> previous=new HashSet<String>(),seen=new HashSet<String>();
        if(stored!=null && stored.getSubaccuse()!=null) for(BankAccUseVO row:stored.getSubaccuse()) previous.add(row.getPk_bankaccuse());
        BankAccUseVO[] rows=currency.getSubaccuse();
        if(rows==null) return;
        for(BankAccUseVO row:rows) {
            EsignValues.require(row!=null && row.getClass()==BankAccUseVO.class,"银行账户使用组织载荷不正确。");
            String pk=row.getPk_bankaccuse();
            EsignValues.require((row.getPk_bankaccbas()==null || row.getPk_bankaccbas().equals(account))
                && (row.getPk_bankaccsub()==null || row.getPk_bankaccsub().equals(currency.getPk_bankaccsub()))
                && (pk==null || previous.contains(pk) && seen.add(pk)),"银行账户使用组织子行不属于授权币种或重复。");
        }
    }

    private static void requireSelected(OperationContext context,BankAccountSaveCommand input,
            BankAccountImpactScope.Selection selection) throws BusinessException {
        BusinessObjectRef root=root(context);
        CustbankVO relation=input.getUnion().getCustbankVO();
        EsignValues.require(relation!=null && input.getUnion().getBankaccbasVO()!=null
            && relation.getPk_custbank()!=null && relation.getPk_custbank().equals(root.getObjectPk())
            && relation.getPk_custbank().equals(selection.getSelectedRelation())
            && selection.getAccount().equals(relation.getPk_bankaccbas())
            && selection.getAccount().equals(input.getUnion().getBankaccbasVO().getPk_bankaccbas())
            && selectedArchivePk(selection).equals(relation.getPk_cust()),
            "银行账户保存选中的关系已变化，请重新发起。");
    }

    static String selectedArchivePk(BankAccountImpactScope.Selection selection) throws BusinessException {
        String archive=null;
        for(BankAccountImpactScope.AffectedArchive item:selection.getArchives())
            if(item.getRelations().contains(selection.getSelectedRelation())) {
                EsignValues.require(archive==null,"银行账户选中关系归属不唯一。");archive=item.getArchive();
            }
        EsignValues.require(archive!=null,"银行账户选中关系归属不存在。");return archive;
    }

    static BankAccbasVO account(String pk) throws BusinessException {
        Object found=MDPersistenceService.lookupPersistenceQueryService().queryBillOfVOByPK(BankAccbasVO.class,pk,false);
        EsignValues.require(found==null || found instanceof BankAccbasVO,"银行账户查询返回了未知类型。");return (BankAccbasVO)found;
    }

    private static BankAccountSaveCommand draft(OperationContext context,Serializable value) throws BusinessException {
        EsignValues.require(context!=null && isRoute(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol())
            && context.getGroupId()!=null && context.getOrgId()!=null
            && value instanceof BankAccountSaveCommand,"银行账户保存载荷或节点不正确。");
        BankAccountSaveCommand command=(BankAccountSaveCommand)value;
        CustBankaccUnionVO union=command.getUnion();
        BusinessObjectRef root=root(context);
        EsignValues.require(Objects.equals(BankAccountRequestScope.defaultCurrency(root),command.getDefaultCurrency())
            && (command.getDefaultCurrency()==null || "UPDATE".equals(context.getOperationType())),"默认账户币种与授权不一致。");
        boolean customer="BD_CUSTOMER_BANK".equals(root.getObjectType());
        EsignValues.require(("10140CBA".equals(context.getNodeCode()))==customer,"银行账户保存节点与档案类型不一致。");
        CustbankVO relation=union.getCustbankVO();BankAccbasVO account=union.getBankaccbasVO();
        EsignValues.require(relation!=null && account!=null && relation.getPk_cust()!=null
            && (customer?Integer.valueOf(1):Integer.valueOf(3)).equals(relation.getAccclass()),
            "银行账户保存关系数据不正确。");
        if("INSERT".equals(context.getOperationType())) {
            EsignValues.require(context.getObjects().size()<=2 && relation.getPk_cust().equals(root.getObjectPk())
                && root.getPolicyOwnerOrg()!=null
                && relation.getPk_custbank()==null && relation.getPk_bankaccbas()==null
                && account.getPk_bankaccbas()==null && context.getGroupId().equals(account.getPk_group()),
                "银行账户新增载荷或授权对象不正确。");
            return command;
        }
        EsignValues.require(context.getObjects().size()>=1 && root.getObjectPk()!=null
            && relation.getPk_custbank()!=null && relation.getPk_custbank().equals(root.getObjectPk())
            && account.getPk_bankaccbas()!=null && account.getPk_bankaccbas().equals(relation.getPk_bankaccbas())
            && context.getGroupId().equals(account.getPk_group()) && command.getVersion()!=null && command.getVersion().length()<=80,
            "银行账户修改载荷或授权对象不正确。");
        for(BusinessObjectRef ref:context.getObjects()) EsignValues.require(ref.getPolicyOwnerOrg()!=null
            && ref.getObjectPk()!=null && (ref==root || root.getClientObjectId().equals(ref.getParentClientObjectId())),
            "银行账户保存影响对象不完整。");
        return command;
    }

    static boolean isRoute(String node,String button,String operation) {
        return ("INSERT".equals(operation) || "UPDATE".equals(operation)) && BankAccountRoute.matches(node,button,operation);
    }

    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"银行账户保存存在多个选中对象。");root=ref;
        }
        EsignValues.require(root!=null && ("BD_CUSTOMER_BANK".equals(root.getObjectType())
            || "BD_SUPPLIER_BANK".equals(root.getObjectType())),"银行账户保存缺少选中对象。");return root;
    }
}
