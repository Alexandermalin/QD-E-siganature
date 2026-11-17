package nc.impl.ic.qd.esign;

import java.util.Collection;
import nc.bs.dao.BaseDAO;
import nc.jdbc.framework.SQLParameter;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.pub.BusinessException;

/** Uses the reviewed CHAR(20) source-bill identity for outbound-request DAO lookups. */
final class SapplyBillDataAccess {
    interface Reader {
        Object retrieveByPK(Class type,String key) throws BusinessException;
        Collection<?> retrieveByClause(Class type,String clause,SQLParameter parameters) throws BusinessException;
    }
    private SapplyBillDataAccess() { }

    static SapplyBillHeadVO readHead(String billPk) throws BusinessException {
        return readHead(reader(),billPk);
    }
    static Collection<?> readBodies(String billPk) throws BusinessException {
        return readBodies(reader(),billPk);
    }
    static SapplyBillHeadVO readHead(Reader reader,String billPk) throws BusinessException {
        if(reader==null)throw new BusinessException("出库申请数据读取器不可用。");
        Object result=reader.retrieveByPK(SapplyBillHeadVO.class,paddedKey(billPk));
        if(result!=null && !(result instanceof SapplyBillHeadVO))
            throw new BusinessException("出库申请表头类型无效。");
        return (SapplyBillHeadVO)result;
    }
    static Collection<?> readBodies(Reader reader,String billPk) throws BusinessException {
        if(reader==null)throw new BusinessException("出库申请数据读取器不可用。");
        SQLParameter parameters=new SQLParameter();parameters.addParam(paddedKey(billPk));
        return reader.retrieveByClause(SapplyBillBodyVO.class,"cgeneralhid=?",parameters);
    }
    private static String paddedKey(String key) throws BusinessException {
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,20}"))
            throw new BusinessException("出库申请单主键无效。");
        StringBuilder value=new StringBuilder(key);
        while(value.length()<20)value.append(' ');
        return value.toString();
    }
    private static Reader reader() {
        final BaseDAO dao=new BaseDAO();
        return new Reader() {
            public Object retrieveByPK(Class type,String key) throws BusinessException {
                return dao.retrieveByPK(type,key);
            }
            public Collection<?> retrieveByClause(Class type,String clause,SQLParameter parameters) throws BusinessException {
                return dao.retrieveByClause(type,clause,parameters);
            }
        };
    }
}
