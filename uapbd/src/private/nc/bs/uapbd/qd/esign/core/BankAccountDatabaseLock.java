package nc.bs.uapbd.qd.esign.core;

import nc.bs.dao.BaseDAO;
import nc.bs.dao.DAOException;
import nc.jdbc.framework.SQLParameter;
import nc.vo.pub.BusinessException;

/**
 * 银行账户的数据库级统一锁。
 * 原生入口仅持有进程内业务锁；共享账户的并发写入口（保存、删除、启停及关系维护）
 * 在进入原生服务前先以 SELECT ... FOR UPDATE 串行化到同一账户行上，锁随调用方 CMT 提交或回滚释放。
 * 未接入受控网关的原生入口不受此锁保护；客户端候选全部部署并逐项验收后才构成完整覆盖。
 */
public final class BankAccountDatabaseLock {
    /** dao：NC 原生数据访问入口，加入调用方事务；锁的生命周期与该事务一致。 */
    private final BaseDAO dao;

    public BankAccountDatabaseLock() { this(new BaseDAO()); }
    public BankAccountDatabaseLock(BaseDAO dao) {
        if (dao == null) { throw new IllegalArgumentException("dao is required"); }
        this.dao = dao;
    }

    /**
     * 首次使用的账户补建锁行，再以当前事务锁定该行。
     * 并发首次补建由主键约束仲裁：冲突行已存在时忽略插入错误继续锁定。
     */
    public void acquire(String accountPk) throws BusinessException {
        EsignValues.require(accountPk != null && !accountPk.trim().isEmpty() && accountPk.length() <= 20,
            "银行账户锁参数不正确。");
        try {
            if(lockRow(accountPk)) return;
            try { insertRow(accountPk); }
            catch(DAOException failure) {
                if(lockRow(accountPk)) return;
                throw failure;
            }
            EsignValues.require(lockRow(accountPk),"银行账户锁行缺失，本次操作回滚。");
        } catch (DAOException failure) {
            throw new BusinessException("银行账户并发锁获取失败，请重试：" + failure.getMessage(), failure);
        }
    }

    private boolean lockRow(String accountPk) throws DAOException {
        SQLParameter parameters=new SQLParameter();
        parameters.addParam(accountPk);
        return dao.executeQuery("SELECT pk_bankaccbas FROM qd_es_bankacc_lock WHERE pk_bankaccbas=? FOR UPDATE",
            parameters,new nc.jdbc.framework.processor.MapProcessor())!=null;
    }

    private void insertRow(String accountPk) throws DAOException {
        SQLParameter parameters=new SQLParameter();
        parameters.addParam(accountPk);
        parameters.addParam(EsignValues.utc());
        dao.executeUpdate("INSERT INTO qd_es_bankacc_lock (pk_bankaccbas,created_at) VALUES (?,?)",parameters);
    }
}
