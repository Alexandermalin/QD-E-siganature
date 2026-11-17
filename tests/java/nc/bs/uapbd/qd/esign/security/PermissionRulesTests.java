package nc.bs.uapbd.qd.esign.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.vo.pub.BusinessException;
import nc.vo.uap.rbac.FuncPermissionState;
import nc.vo.uap.rbac.FuncSubInfo;
import nc.vo.uap.rbac.profile.ButnInfo;
import nc.vo.uap.rbac.profile.FuncNodeDetailPermInfo;
import nc.vo.uap.rbac.profile.FuncNodeInfo;
import nc.vo.uap.rbac.profile.PageInfo;
import nc.vo.uap.rbac.profile.SubjectPermInfo;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;

/** 使用原生权限 VO 测试数据核对授权主体与组织范围，不调用真实账号、权限配置服务或组织类型查询。 */
public final class PermissionRulesTests {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;
    /** 执行当前离线测试组或测试操作，断言失败和未预期异常向外传播。 */
    public static int run() throws Exception {
        assertions=0;
        SubjectPermInfo grantsA=subject("R-A","NODE",new String[]{"ORG-A"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        SubjectPermInfo deniesB=subject("R-B","NODE",new String[]{"ORG-B"},"SAVE",FuncPermissionState.REGISTERD_NOPERMISSION,true);
        SubjectPermInfo[] separate={grantsA,deniesB};
        List<String> orgs=EsignPermission.organizations(separate,"NODE","SAVE",true);
        check(orgs.equals(Arrays.asList("ORG-A")),"A different subject's organizations cannot borrow a button grant");
        check(!orgs.contains("ORG-B"),"Cross-subject organization escalation rejected");
        page(grantsA,"NODE","fi_info",FuncPermissionState.REGISTERD_HASPERMISSION);
        page(deniesB,"NODE","fi_info",FuncPermissionState.REGISTERD_HASPERMISSION);
        check(EsignPermission.pageOrganization(separate,"NODE","SAVE","fi_info","ORG-A"),"Finance page and button grant on one subject");
        check(!EsignPermission.pageOrganization(separate,"NODE","SAVE","fi_info","ORG-B"),"Page organization cannot borrow another subject's button grant");
        check(!EsignPermission.pageOrganization(separate,"NODE","SAVE","pu_info","ORG-A"),"Finance page authorization does not grant the purchase page");
        SubjectPermInfo operationOnly=subject("OP","NODE",new String[]{"OP-ORG"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        SubjectPermInfo targetOnly=subject("TARGET","NODE",new String[]{"TARGET-ORG"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        page(operationOnly,"NODE","fi_info",FuncPermissionState.REGISTERD_NOPERMISSION);
        page(targetOnly,"NODE","fi_info",FuncPermissionState.REGISTERD_HASPERMISSION);
        check(!EsignPermission.pageOrganization(new SubjectPermInfo[]{operationOnly,targetOnly},"NODE","SAVE","fi_info","OP-ORG","TARGET-ORG"),
            "Operation and target organizations cannot borrow page rights across subjects");
        SubjectPermInfo both=subject("BOTH","NODE",new String[]{"OP-ORG","TARGET-ORG"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        page(both,"NODE","fi_info",FuncPermissionState.REGISTERD_HASPERMISSION);
        check(EsignPermission.pageOrganization(new SubjectPermInfo[]{both},"NODE","SAVE","fi_info","OP-ORG","TARGET-ORG"),
            "One subject's button, operation organization and target page must be usable together");
        SubjectPermInfo foreign=subject("FOREIGN","NODE",new String[]{"FOREIGN-ORG"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        page(foreign,"NODE","fi_info",FuncPermissionState.REGISTERD_HASPERMISSION);
        Set<String> pageTargets=EsignPermission.pageOrganizations(new SubjectPermInfo[]{both,foreign},"NODE","SAVE","fi_info","OP-ORG");
        check(pageTargets.contains("TARGET-ORG") && !pageTargets.contains("FOREIGN-ORG"),
            "Bulk target lookup cannot merge a subject that lacks the operation organization");
        page(grantsA,"NODE","fi_info",FuncPermissionState.REGISTERD_NOPERMISSION);
        check(!EsignPermission.pageOrganization(separate,"NODE","SAVE","fi_info","ORG-A"),"Explicit page denial rejected even if native organization list contains the target");
        check(EsignPermission.explicit(separate,"NODE","SAVE"),"Explicit group action recognized");
        check(!EsignPermission.explicit(separate,"NODE","DELETE"),"Unregistered management action rejected");
        check(EsignPermission.organizations(separate,"OTHER","SAVE",true).isEmpty(),"Missing function rejected");
        check(EsignPermission.organizations(new SubjectPermInfo[]{deniesB},"NODE","SAVE",false).isEmpty(),"Native explicit denial preserved");
        SubjectPermInfo noButtonControl=subject("R-C","NODE",new String[]{"ORG-C"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,false);
        check(EsignPermission.organizations(new SubjectPermInfo[]{noButtonControl},"NODE","SAVE",false).contains("ORG-C"),"Original native uncontrolled-button behavior preserved");
        check(EsignPermission.organizations(new SubjectPermInfo[]{noButtonControl},"NODE","SAVE",true).isEmpty(),"New management functions require explicit registered control");
        SubjectPermInfo rules=subject("RULES","NODE",new String[0],"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        Map<String,Set<String>> byRule=new HashMap<String,Set<String>>(); byRule.put("NATIVE-RULE",new HashSet<String>(Arrays.asList("ORG-R")));
        rules.setPkOrgsByRule(byRule);
        check(EsignPermission.organizations(new SubjectPermInfo[]{rules},"NODE","SAVE",true).equals(Arrays.asList("ORG-R")),"Native rule organizations remain scoped to their subject");
        SubjectPermInfo grantsB=subject("R-B2","NODE",new String[]{"ORG-B","ORG-A"},"SAVE",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        orgs=EsignPermission.organizations(new SubjectPermInfo[]{grantsA,grantsB},"NODE","SAVE",true);
        check(orgs.equals(Arrays.asList("ORG-A","ORG-B")),"Only fully authorized subject scopes are merged without duplicates");
        check(EsignPermission.organizations(null,"NODE","SAVE",true).isEmpty(),"Missing profile subjects fail closed");
        check(!EsignPermission.explicit(null,"NODE","SAVE"),"Missing explicit profile fails closed");
        SubjectPermInfo groupOnly=subject("GROUP","NODE",new String[0],"GROUP_SECURITY",FuncPermissionState.REGISTERD_HASPERMISSION,true);
        check(EsignPermission.explicit(new SubjectPermInfo[]{groupOnly},"NODE","GROUP_SECURITY"),"Explicit group-level action does not invent an organization requirement");
        check(EsignPermission.organizations(new SubjectPermInfo[]{groupOnly},"NODE","GROUP_SECURITY",true).isEmpty(),"Group-level action does not grant all business-unit access");
        try { EsignPermission.organizations(separate,"NODE",null,true); throw new AssertionError("Missing button should be rejected"); }
        catch(BusinessException expected){assertions++;}
        check(new FuncSubInfo().getButtonPermissionOrgs("SAVE")==null,"Local NC65 button-org API is a null stub, not an authorization fallback");
        final java.util.List<String> checked=new java.util.ArrayList<String>();
        List<BusinessObjectRef> branch=Arrays.asList(new BusinessObjectRef("r","BD_CUSTCLASS","ROOT",null,null),
            new BusinessObjectRef("c","BD_CUSTCLASS","CHILD",null,"r"));
        EsignPermission.requireArchiveData(branch,new EsignPermission.ArchiveDataCheck(){
            public boolean allows(String pk){checked.add(pk);return true;}});
        check(checked.equals(Arrays.asList("ROOT","CHILD")),"Native data permission is checked for every affected archive");
        try { EsignPermission.requireArchiveData(branch,new EsignPermission.ArchiveDataCheck(){
                public boolean allows(String pk){return !"CHILD".equals(pk);}});
            throw new AssertionError("Unpermitted descendant was accepted"); }
        catch(BusinessException expected){assertions++;}
        materialClassificationReference();
        return assertions;
    }
    private static void materialClassificationReference() throws BusinessException {
        final String classification="1001A910000000004MLI";
        final List<String> checked=new java.util.ArrayList<String>();
        EsignPermission.ArchiveDataCheck allowed=new EsignPermission.ArchiveDataCheck() {
            public boolean allows(String pk) {
                checked.add(pk);
                return classification.equals(pk);
            }
        };
        EsignPermission.requireMaterialBatchClassification(classification,allowed);
        check(checked.equals(Arrays.asList(classification)),"The native check must receive the exact signed classification");
        for(Object invalid:new Object[]{null,Integer.valueOf(1),"abc'","not permitted"," "}) {
            try {
                EsignPermission.requireMaterialBatchClassification(invalid,allowed);
                throw new AssertionError("Invalid material classification accepted");
            } catch(BusinessException expected) { assertions++; }
        }
        check(checked.equals(Arrays.asList(classification)),"Invalid references must never reach the permission query");
        try {
            EsignPermission.requireMaterialBatchClassification("OTHERCLASS",allowed);
            throw new AssertionError("Restricted material classification accepted");
        } catch(BusinessException expected) { assertions++; }
        check(checked.equals(Arrays.asList(classification,"OTHERCLASS")),"Each request must check its own classification");
        final BusinessException unavailable=new BusinessException("Reference permission service unavailable");
        try {
            EsignPermission.requireMaterialBatchClassification(classification,new EsignPermission.ArchiveDataCheck() {
                public boolean allows(String pk) throws BusinessException { throw unavailable; }
            });
            throw new AssertionError("Unavailable material classification permission was accepted");
        } catch(BusinessException expected) {
            check(expected==unavailable,"Native permission error should abort the request");
        }
    }
    /** 命令行入口，执行本文件的离线检查或原有工程示例。 */
    public static void main(String[] args) throws Exception {
        System.out.println("PASS permission subject-scope assertions="+run()+" (offline VO fixtures; NC profile service/org-type filter not executed)");
    }
    /** 构造指定节点、按钮和组织范围的原生授权主体测试数据。 */
    private static SubjectPermInfo subject(String id,String node,String[] orgs,String button,FuncPermissionState state,boolean controlled) {
        FuncNodeInfo function=new FuncNodeInfo();function.setCode(node);
        ButnInfo grant=new ButnInfo();grant.setCode(button);grant.setPermissionState(state);grant.setParentNeedButnPower(controlled);
        FuncNodeDetailPermInfo detail=new FuncNodeDetailPermInfo();detail.setFuncNodeInfo(function);detail.setFuncode(node);detail.setButnPermInfos(new ButnInfo[]{grant});
        SubjectPermInfo subject=new SubjectPermInfo();subject.setSubjectid(id);subject.setPkorgs(orgs);
        subject.setFuncNodeInfos(new FuncNodeInfo[]{function});subject.setFuncNodeDetailPermInfo(node,detail);
        return subject;
    }
    private static void page(SubjectPermInfo subject,String node,String code,FuncPermissionState state) {
        PageInfo page=new PageInfo();page.setCode(code);page.setPermissionState(state);
        subject.getFuncNodeDetailPermInfo(node).setPagePermInfos(new PageInfo[]{page});
    }
    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);assertions++;}
}
