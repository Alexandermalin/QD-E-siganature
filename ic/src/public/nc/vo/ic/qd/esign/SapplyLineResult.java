package nc.vo.ic.qd.esign;

import java.io.Serializable;
import nc.vo.ic.m4455.entity.SapplyBillVO;

/** Native result and persisted identity returned only after the row action succeeds. */
public final class SapplyLineResult implements Serializable {
    private static final long serialVersionUID=1L;
    private final String billPk,billCode;
    private final SapplyBillVO[] nativeResult;
    public SapplyLineResult(String billPk,String billCode,SapplyBillVO[] nativeResult) {
        this.billPk=billPk;this.billCode=billCode;this.nativeResult=nativeResult==null?null:nativeResult.clone();
    }
    public String getBillPk(){return billPk;}
    public String getBillCode(){return billCode;}
    public SapplyBillVO[] getNativeResult(){return nativeResult==null?null:nativeResult.clone();}
}
