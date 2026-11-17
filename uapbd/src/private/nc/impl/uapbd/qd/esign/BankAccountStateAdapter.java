package nc.impl.uapbd.qd.esign;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.NCLocator;
import nc.bs.uapbd.qd.esign.audit.AuditCaptureScope;
import nc.bs.uapbd.qd.esign.audit.BankAccountStateSnapshots;
import nc.bs.uapbd.qd.esign.core.BankAccountImpactScope;
import nc.bs.uapbd.qd.esign.core.BankAccountRequestScope;
import nc.bs.uapbd.qd.esign.core.BankAccountRoute;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.bd.bankacc.cust.ICustBankaccService;
import nc.itf.uapbd.qd.esign.IBankAccountStateAdapter;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.bankaccount.BankAccbasVO;
import nc.vo.bd.bankaccount.cust.CustBankaccUnionVO;
import nc.vo.bd.cust.CustbankVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.SuperVO;
import nc.vo.uapbd.qd.esign.BankAccountStateCommand;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.audit.SnapshotRow;
import nc.vo.util.BDPKLockUtil;

/** Candidate NC account-state transaction; the client cannot submit editable account or currency values. */
public final class BankAccountStateAdapter implements IBankAccountStateAdapter {
    public void validate(OperationContext context,Serializable command) throws BusinessException { selected(context,command); }

    /** Match the native service's selected-partner lock, then its bank-account business lock. */
    static void prelock(OperationContext context,Serializable command) throws BusinessException {
        BankAccountStateCommand input=selected(context,command);
        BankAccountRequestScope.Expanded impact=BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),
            BankAccountRequestScope.database(),false);
        EsignValues.require(input.getAccount().equals(impact.getSelection().getAccount()),"选中的银行账户已变化。");
        String archive=selectedArchive(impact.getSelection());
        BDPKLockUtil.lockString(new String[]{archive});
        BankAccbasVO account=account(input.getAccount());
        EsignValues.require(account!=null && context.getGroupId().equals(account.getPk_group()),"银行账户不属于当前集团。");
        BDPKLockUtil.lockSuperVO(new SuperVO[]{account});
        // 原生业务锁之后取数据库级统一账户锁，与保存、删除共用同一锁定顺序。
        new nc.bs.uapbd.qd.esign.core.BankAccountDatabaseLock().acquire(account.getPk_bankaccbas());
        BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),BankAccountRequestScope.database(),false);
    }

    public Serializable execute(OperationContext context,Serializable command) throws BusinessException {
        BankAccountStateCommand input=selected(context,command);
        BankAccountRequestScope.Expanded impact=BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),
            BankAccountRequestScope.database(),false);
        BankAccountImpactScope.Selection selection=impact.getSelection();
        EsignValues.require(input.getAccount().equals(selection.getAccount()),"银行账户已变化，请重新发起操作。");
        CustbankVO relation=(CustbankVO)new BaseDAO().retrieveByPK(CustbankVO.class,input.getRelation());
        BankAccbasVO account=account(input.getAccount());String archive=selectedArchive(selection);
        BusinessObjectRef root=root(context);
        EsignValues.require(relation!=null && account!=null && input.getRelation().equals(relation.getPk_custbank())
            && input.getAccount().equals(relation.getPk_bankaccbas()) && archive.equals(relation.getPk_cust())
            && input.getAccount().equals(account.getPk_bankaccbas()) && context.getGroupId().equals(account.getPk_group())
            && ("BD_CUSTOMER_BANK".equals(root.getObjectType())?Integer.valueOf(1):Integer.valueOf(3)).equals(relation.getAccclass()),
            "银行账户或关联档案已变化，请重新发起操作。");
        boolean enable="ENABLE".equals(context.getOperationType());
        ServerIdentity identity=ServerIdentity.current();
        EsignValues.require(context.getGroupId().equals(identity.groupId),"银行账户操作集团不一致。");
        verifyNativeState(account,input.getVersion(),identity.userId,enable);
        if(enable) {
            account.setEnableuser(identity.userId);
        } else account.setDisableuser(identity.userId);
        AuditCaptureScope scope=AuditCaptureScope.current();EsignValues.require(scope!=null,"银行账户启停缺少受控事务上下文。");
        if(scope.isEnabled()) capture(context,selection,scope,true,true);
        CustBankaccUnionVO inputVo=new CustBankaccUnionVO();inputVo.setCustbankVO(relation);inputVo.setBankaccbasVO(account);
        ICustBankaccService service=NCLocator.getInstance().lookup(ICustBankaccService.class);
        CustBankaccUnionVO result=enable?service.enableCustBankacc(inputVo):service.disableCustBankacc(inputVo);
        EsignValues.require(result!=null && result.getBankaccbasVO()!=null && result.getCustbankVO()!=null
            && input.getAccount().equals(result.getBankaccbasVO().getPk_bankaccbas())
            && input.getRelation().equals(result.getCustbankVO().getPk_custbank()),"原生银行账户启停结果不完整。");
        BankAccountRequestScope.verify(context.getGroupId(),context.getObjects(),BankAccountRequestScope.database(),false);
        BankAccbasVO persisted=account(input.getAccount());
        EsignValues.require(persisted!=null && context.getGroupId().equals(persisted.getPk_group())
            && (enable?Integer.valueOf(2):Integer.valueOf(3)).equals(persisted.getEnablestate()),"银行账户启停结果无法确认。");
        if(scope.isEnabled()) capture(context,selection,scope,false,false);
        return result;
    }

    public List<BusinessObjectRef> describeResult(OperationContext context,Serializable result) throws BusinessException {
        EsignValues.require(result instanceof CustBankaccUnionVO,"银行账户启停结果类型不正确。");return context.getObjects();
    }
    private static void capture(OperationContext context,BankAccountImpactScope.Selection selection,AuditCaptureScope scope,
            boolean before,boolean lock) throws BusinessException {
        Map<String,List<SnapshotRow>> snapshots=BankAccountStateSnapshots.capture(context.getGroupId(),selection,
            BankAccountStateSnapshots.database(),lock);
        for(BankAccountImpactScope.AffectedArchive archive:selection.getArchives()) {
            String client=null;
            for(BusinessObjectRef ref:context.getObjects()) if(archive.getDomain().equals(ref.getObjectType())
                    && archive.getRelations().contains(ref.getObjectPk()) && scope.isEnabledFor(ref.getClientObjectId())) {
                EsignValues.require(client==null,"银行账户同一档案的审计对象重复。");client=ref.getClientObjectId();
            }
            if(client!=null) {
                List<SnapshotRow> rows=snapshots.get(archive.getDomain()+"\u0000"+archive.getArchive());
                EsignValues.require(rows!=null && !rows.isEmpty(),"银行账户档案快照不完整。");
                if(before) scope.before(client,rows);else scope.after(client,rows);
            }
        }
    }
    private static String selectedArchive(BankAccountImpactScope.Selection selection) throws BusinessException {
        String archive=null;
        for(BankAccountImpactScope.AffectedArchive item:selection.getArchives()) if(item.getRelations().contains(selection.getSelectedRelation())) {
            EsignValues.require(archive==null,"银行账户选中关系归属不唯一。");archive=item.getArchive();
        }
        EsignValues.require(archive!=null,"银行账户选中关系归属不存在。");return archive;
    }
    private static BankAccbasVO account(String pk) throws BusinessException {
        Object found=MDPersistenceService.lookupPersistenceQueryService().queryBillOfVOByPK(BankAccbasVO.class,pk,false);
        EsignValues.require(found==null || found instanceof BankAccbasVO,"银行账户查询返回了未知类型。");return (BankAccbasVO)found;
    }
    static void verifyNativeState(BankAccbasVO account,String version,String user,boolean enable) throws BusinessException {
        EsignValues.require(account!=null && present(user) && present(version) && account.getTs()!=null
            && version.equals(account.getTs().toString()),"银行账户已被修改，请刷新后重试。");
        Integer prior=account.getEnablestate();
        EsignValues.require(enable?(Integer.valueOf(1).equals(prior) || Integer.valueOf(3).equals(prior)):Integer.valueOf(2).equals(prior),
            "银行账户状态已变化，请刷新后重试。");
        if(enable) EsignValues.require(!user.equals(account.getCreator()) && !user.equals(account.getModifier()),
            "银行账户创建人或最后修改人不能执行启用。");
    }
    private static BankAccountStateCommand selected(OperationContext context,Serializable value) throws BusinessException {
        EsignValues.require(context!=null && BankAccountRoute.matches(context.getNodeCode(),context.getButtonCode(),context.getOperationType())
            && ("ENABLE".equals(context.getOperationType()) || "DISABLE".equals(context.getOperationType()))
            && OperationContext.OWNER_POLICY_PROTOCOL.equals(context.getPolicyProtocol()) && context.getGroupId()!=null && context.getOrgId()!=null
            && value instanceof BankAccountStateCommand,"银行账户启停载荷或节点不正确。");
        BankAccountStateCommand command=(BankAccountStateCommand)value;BusinessObjectRef root=root(context);
        EsignValues.require(root.getObjectType().equals(BankAccountRoute.selectedDomain(context.getNodeCode()))
            && root.getObjectPk()!=null && root.getObjectPk().equals(command.getRelation()) && present(command.getAccount())
            && present(command.getVersion()) && command.getVersion().length()<=80,"银行账户启停对象或原生版本不正确。");
        for(BusinessObjectRef ref:context.getObjects()) EsignValues.require(present(ref.getPolicyOwnerOrg()) && present(ref.getObjectPk())
            && ("BD_CUSTOMER_BANK".equals(ref.getObjectType()) || "BD_SUPPLIER_BANK".equals(ref.getObjectType()))
            && ref.getDesiredOwnerOrg()==null && context.getOrgId().equals(ref.getTargetOrg())
            && (ref==root || root.getClientObjectId().equals(ref.getParentClientObjectId())),
            "银行账户影响对象、所属组织或操作组织不一致。");
        return command;
    }
    private static BusinessObjectRef root(OperationContext context) throws BusinessException {
        BusinessObjectRef root=null;
        for(BusinessObjectRef ref:context.getObjects()) if(ref.getParentClientObjectId()==null) {
            EsignValues.require(root==null,"银行账户启停存在多个选中对象。");root=ref;
        }
        EsignValues.require(root!=null,"银行账户启停缺少选中对象。");return root;
    }
    private static boolean present(String value) { return value!=null && !value.trim().isEmpty() && value.length()<=80; }
}
