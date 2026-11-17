package nc.ui.uapbd.qd.esign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import nc.ui.querytemplate.filter.IFilter;
import nc.ui.querytemplate.meta.IFilterMeta;
import nc.ui.querytemplate.operator.EqOperator;
import nc.ui.querytemplate.operator.Eq2Operator;
import nc.ui.querytemplate.operator.NeqOperator;
import nc.ui.querytemplate.querytree.IQueryScheme;
import nc.ui.querytemplate.value.DefaultFieldValue;
import nc.ui.querytemplate.value.DefaultFieldValueElement;
import nc.ui.querytemplate.value.RefValueObject;
import nc.vo.uif2.LoginContext;

/** 仅验证物料原生查询条件的组织提取；实际 NC 界面仍须现场联调。 */
public final class MaterialOperationScopeTests {
    private MaterialOperationScopeTests() { }

    public static void run() {
        LoginContext login = new LoginContext();
        login.setPk_org("LOGIN_ORG");
        RefValueObject chosen = new RefValueObject(); chosen.setPk("SELECTED_ORG");
        IFilter selected = filter("pk_org_assign", EqOperator.getInstance(), chosen);
        check("SELECTED_ORG".equals(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{selected}), login)),
            "explicit query organization takes precedence over login organization");
        check("SELECTED_ORG".equals(QdMaterialListModelDataManager.selectedOrganization(
            scheme(new IFilter[]{filter("pk_org_assign", Eq2Operator.getInstance(), chosen)}), login)),
            "alternate native equality operator also identifies a single organization");
        check("LOGIN_ORG".equals(QdMaterialListModelDataManager.selectedOrganization(scheme(null), login)),
            "null filters follow the native login-organization fallback");
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[0]), login) == null,
            "unrestricted query does not invent an operation organization");
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{selected, selected}), login) == null,
            "multiple organization filters are ambiguous");
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{filter("pk_org_assign", new NeqOperator(), chosen)}), login) == null,
            "not-equal filter is not a selected operation organization");
        chosen.setSubIncluded(true);
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{selected}), login) == null,
            "including subordinate organizations is ambiguous");
        chosen.setSubIncluded(false); chosen.setMultiSelectionSql("select pk_org from org_orgs");
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{selected}), login) == null,
            "multi-selection SQL cannot be treated as a single organization");
        chosen.setMultiSelectionSql(null);
        IFilter warehouse=filter("pk_org",EqOperator.getInstance(),chosen);
        check("SELECTED_ORG".equals(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{warehouse}),login,"pk_org",false)),
            "warehouse operation must use the explicitly queried pk_org");
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(null),login,"pk_org",false)==null,
            "warehouse query without filters cannot assume the login organization");
        check(QdMaterialListModelDataManager.selectedOrganization(scheme(new IFilter[]{selected}),login,"pk_org",false)==null,
            "material assignment filter cannot stand in for warehouse organization");
        System.out.println("PASS MaterialOperationScopeTests assertions=11 (query model doubles; NC UI verification pending)");
    }

    private static IQueryScheme scheme(final IFilter[] filters) {
        return proxy(IQueryScheme.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) { return "get".equals(method.getName()) ? filters : null; }
        });
    }

    private static IFilter filter(final String field, final Object operator, Object value) {
        final DefaultFieldValue values = new DefaultFieldValue();
        values.add(new DefaultFieldValueElement("", "", value));
        final IFilterMeta meta = proxy(IFilterMeta.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) { return "getFieldCode".equals(method.getName()) ? field : null; }
        });
        return proxy(IFilter.class, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("getFilterMeta".equals(method.getName())) return meta;
                if ("getOperator".equals(method.getName())) return operator;
                if ("getFieldValue".equals(method.getName())) return values;
                return null;
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
