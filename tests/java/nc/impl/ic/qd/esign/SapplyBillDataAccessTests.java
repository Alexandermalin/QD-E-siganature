package nc.impl.ic.qd.esign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import nc.jdbc.framework.SQLParameter;
import nc.vo.ic.m4455.entity.SapplyBillBodyVO;
import nc.vo.ic.m4455.entity.SapplyBillHeadVO;
import nc.vo.pub.BusinessException;

/** Exercises the outbound-request header and body bindings without an NC database. */
public final class SapplyBillDataAccessTests {
    public static void run() throws Exception {
        FakeDao dao=new FakeDao();
        require(SapplyBillDataAccess.readHead(dao,"BILL")==dao.head,"Header VO must be returned");
        require(dao.headerType==SapplyBillHeadVO.class && "BILL                ".equals(dao.headerKey),
                "Short CHAR(20) header key must be passed to the DAO with right padding");
        require(SapplyBillDataAccess.readBodies(dao,"BILL")==dao.bodies,"Body row collection must be returned");
        require(dao.bodyType==SapplyBillBodyVO.class && "cgeneralhid=?".equals(dao.where)
                && dao.parameters.equals(Collections.<Object>singletonList("BILL                ")),
                "Body lookup must bind the padded header key rather than interpolate it");
        SapplyBillDataAccess.readHead(dao,"12345678901234567890");
        require("12345678901234567890".equals(dao.headerKey),"Full-length keys must be unchanged");
        int calls=dao.calls;
        for(String key:new String[]{null,""," ","BILL ","BILL' OR 1=1","123456789012345678901"}) {
            try {SapplyBillDataAccess.readHead(dao,key);throw new AssertionError("Invalid header identity accepted");}
            catch(BusinessException expected) { }
            try {SapplyBillDataAccess.readBodies(dao,key);throw new AssertionError("Invalid body identity accepted");}
            catch(BusinessException expected) { }
        }
        require(dao.calls==calls,"Invalid identities must be rejected before any DAO query");
        dao.wrongType=true;
        try {SapplyBillDataAccess.readHead(dao,"BILL");throw new AssertionError("Wrong header type accepted");}
        catch(BusinessException expected) { }
        System.out.println("PASS SapplyBillDataAccessTests (outbound CHAR keys and bound DAO queries; no NC/Oracle)");
    }
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static final class FakeDao implements SapplyBillDataAccess.Reader {
        final SapplyBillHeadVO head=new SapplyBillHeadVO();
        final Collection<?> bodies=Arrays.asList(new SapplyBillBodyVO());
        Class<?> headerType,bodyType;String headerKey,where;List<Object> parameters;boolean wrongType;int calls;
        public Object retrieveByPK(Class type,String key) {
            calls++;headerType=type;headerKey=key;return wrongType?"unexpected":head;
        }
        public Collection retrieveByClause(Class type,String clause,SQLParameter params) {
            calls++;bodyType=type;where=clause;parameters=new ArrayList<Object>(params.getParameters());return bodies;
        }
    }
}
