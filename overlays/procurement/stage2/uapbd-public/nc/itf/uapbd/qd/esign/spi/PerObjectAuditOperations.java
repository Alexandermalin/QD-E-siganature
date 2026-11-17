package nc.itf.uapbd.qd.esign.spi;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;

/** Supplies object operations and identities for a native batch without changing its return payload. */
public interface PerObjectAuditOperations {
    Map<String, String> auditOperations(OperationContext context, Serializable command) throws BusinessException;

    List<BusinessObjectRef> describeResult(OperationContext context, Serializable command, Serializable result)
            throws BusinessException;
}
