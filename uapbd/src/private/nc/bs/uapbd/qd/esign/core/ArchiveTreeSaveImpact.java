package nc.bs.uapbd.qd.esign.core;

import java.util.Objects;
import nc.vo.pub.BusinessException;

/** Decides whether the native tree save can change descendants as well as the selected row. */
public final class ArchiveTreeSaveImpact {
    private ArchiveTreeSaveImpact() { }

    public static boolean changesBranch(String domain,String persistedCode,String submittedCode,
            String persistedParent,String submittedParent) throws BusinessException {
        EsignValues.require(ArchiveTreeScope.supports(domain),"无法确定树形档案类型。");
        boolean codeTree="BD_MARBASCLASS".equals(domain) || "BD_RACK".equals(domain);
        if(codeTree) {
            EsignValues.require(persistedCode!=null && submittedCode!=null && !submittedCode.trim().isEmpty(),
                "树形档案缺少有效编码。");
            // BDCodeTreeBaseService trims the submitted code before comparing it with the stored code.
            if(!persistedCode.equals(submittedCode.trim())) return true;
        }
        // MDTreePersistenceUtil invokes InnerCodeUtil when the parent id changes, including null vs '~'.
        return !Objects.equals(persistedParent,submittedParent);
    }

    public static String parentField(String domain) throws BusinessException {
        EsignValues.require(ArchiveTreeScope.supports(domain),"无法确定树形档案类型。");
        return "BD_MARBASCLASS".equals(domain) || "BD_RACK".equals(domain)?"pk_parent":"parent_id";
    }
}
