package nc.impl.pu.qd.esign;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import nc.itf.pu.m21.IOrderClose;
import nc.vo.pu.m21.entity.OrderHeaderVO;
import nc.vo.pu.m21.entity.OrderItemVO;
import nc.vo.pu.m21.entity.OrderVO;
import nc.vo.pu.m21.entity.OrderVOMeta;
import nc.vo.pu.qd.esign.ProcurementStatusCommand;
import nc.vo.pub.AggregatedValueObject;
import nc.vo.pub.BusinessException;
import nc.vo.pub.IAttributeMeta;
import nc.vo.pub.IVOMeta;
import nc.vo.pub.IVOMetaStatisticInfo;
import nc.vo.pub.JavaType;
import nc.vo.pub.SuperVO;
import nc.vo.pubapp.pattern.model.meta.entity.bill.BillMetaFactory;
import nc.vo.pubapp.pattern.model.meta.entity.bill.IBillMeta;
import nc.vo.uapbd.qd.esign.OperationContext;

public final class ProcurementStatusOperationAdapterTest {
    public static void main(String[] args) throws Exception {
        routesExactlyTheStage3Actions();
        bindsSelectedOrderRowsToTheNativeCall();
    }

    private static void bindsSelectedOrderRowsToTheNativeCall() throws Exception {
        final String group = "GROUP";
        final String org = "ORG";
        final String pk = "ORDER-PK";
        final IVOMeta headerMeta = metadata("pk_order");
        final IVOMeta rowMeta = metadata("pk_order_b");
        Map<Class<?>, IBillMeta> metadata = billMetadata();
        IBillMeta originalMetadata = metadata.put(OrderVOMeta.class, proxy(IBillMeta.class, new InvocationHandler() {
            public Object invoke(Object target, Method method, Object[] args) {
                if ("getChildren".equals(method.getName())) return new IVOMeta[] {rowMeta};
                if ("getVOMeta".equals(method.getName())) return rowMeta;
                throw new AssertionError(method.getName());
            }
        }));
        try {
            final OrderHeaderVO persisted = new OrderHeaderVO();
            attach(persisted, headerMeta);
            persisted.setPk_order(pk);
            persisted.setPk_group(group);
            persisted.setPk_org(org);
            persisted.setVbillcode("PERSISTED-NO");
            OrderHeaderVO light = new OrderHeaderVO();
            attach(light, headerMeta);
            light.setPk_order(pk);
            final OrderItemVO persistedOne = row(rowMeta, "R1", pk);
            final OrderItemVO persistedTwo = row(rowMeta, "R2", pk);
            OrderItemVO lightOne = row(rowMeta, "R1", null);
            OrderItemVO lightTwo = row(rowMeta, "R2", null);
            OrderVO original = order(persisted, persistedOne, persistedTwo);
            OrderVO[] submitted = {order(light, lightOne)};
            final OrderVO[] returned = {order(light, lightOne)};
            final int[] calls = {0};
            final boolean[] changesState = {true};
            final IOrderClose service = new IOrderClose() {
                public OrderVO[] close(OrderVO[] bills, int mode, boolean whole) {
                    check(!whole && mode == 1 && bills[0].getBVO().length == 1
                            && "R1".equals(bills[0].getBVO()[0].getPk_order_b()), "Wrong native row close arguments");
                    calls[0]++;
                    if (changesState[0]) persistedOne.setFisactive(1);
                    return returned;
                }

                public OrderVO[] open(OrderVO[] bills, int mode, boolean whole) {
                    throw new AssertionError("Unexpected native open");
                }
            };
            ProcurementStatusOperationAdapter adapter = new ProcurementStatusOperationAdapter(
                    new ProcurementStatusOperationAdapter.ServiceLookup() {
                        public Object find(Class<?> type) {
                            check(type == IOrderClose.class, "Wrong order close service");
                            return service;
                        }
                    }, new ProcurementStatusOperationAdapter.RecordLookup() {
                        public Collection<?> find(Class<? extends SuperVO> type, String key, String value) {
                            check("pk_order".equals(key) && pk.equals(value), "Expected one bill or its child table");
                            if (type == OrderHeaderVO.class) return Collections.singletonList(persisted);
                            if (type == OrderItemVO.class) {
                                return pk.equals(persistedOne.getPk_order())
                                        ? Arrays.asList(persistedOne, persistedTwo) : Collections.singletonList(persistedTwo);
                            }
                            return Collections.emptyList();
                        }
                    });
            ProcurementStatusCommand command = orderClose(submitted, 1, false);
            OperationContext draft = command.draft("40040400", "LineClose", group, new AggregatedValueObject[] {original});
            check("LINE_CLOSE".equals(draft.getOperationType()) && "PU_PURCHASE_ORDER".equals(draft.getObjects().get(0).getObjectType()),
                    "Wrong authorization scope for a selected order row");
            check(draft.getObjects().get(0).getClientObjectId().matches("[0-9a-f]{64}"), "Use the full selected-row digest");

            OrderHeaderVO another = new OrderHeaderVO();
            attach(another, headerMeta);
            another.setPk_order("ORDER-PK-2");
            another.setPk_group(group);
            another.setPk_org("ORG-2");
            OrderHeaderVO unused = new OrderHeaderVO();
            attach(unused, headerMeta);
            unused.setPk_order("ORDER-PK-3");
            unused.setPk_group(group);
            unused.setPk_org("ORG-3");
            OrderVO anotherBill = order(another);
            ProcurementStatusCommand mixed = orderClose(new OrderVO[] {order(light), anotherBill}, 0, true);
            AggregatedValueObject[] source = {original, anotherBill, order(unused)};
            List<OperationContext> scopes = mixed.drafts("40040400", "BillClose", group, source);
            check(scopes.size() == 2 && org.equals(scopes.get(0).getOrgId()) && "ORG-2".equals(scopes.get(1).getOrgId()),
                    "Only organizations in the native selection belong to its policy scope");
            try {
                mixed.draft("40040400", "BillClose", group, source);
                throw new AssertionError("A signed operation has one business organization");
            } catch (BusinessException expected) {
                // A native multi-org selection cannot become one signed request.
            }
            reject(adapter, draft, orderClose(new OrderVO[] {order(light, lightTwo)}, 1, false));
            reject(adapter, draft, orderClose(submitted, 0, true));
            lightOne.setAttributeValue("fisactive", Integer.valueOf(999));
            reject(adapter, draft, command);
            lightOne.setAttributeValue("fisactive", null);
            persistedOne.setPk_order("OTHER-ORDER");
            reject(adapter, draft, command);
            persistedOne.setPk_order(pk);
            check(calls[0] == 0, "Rejected arguments reached the native service");
            check(adapter.execute(draft, command) == returned, "Raw native result must reach the client");
            check("PERSISTED-NO".equals(adapter.describeResult(draft, returned).get(0).getBillCode()), "Read persisted bill code");
            changesState[0] = false;
            persistedOne.setFisactive(0);
            reject(adapter, draft, orderClose(new OrderVO[] {order(light, row(rowMeta, "R1", null))}, 1, false));
            check(calls[0] == 2, "Verify the target persisted state after native close");
        } finally {
            if (originalMetadata == null) metadata.remove(OrderVOMeta.class);
            else metadata.put(OrderVOMeta.class, originalMetadata);
        }
    }

    private static void reject(ProcurementStatusOperationAdapter adapter, OperationContext context,
            ProcurementStatusCommand command) throws Exception {
        try {
            adapter.execute(context, command);
            throw new AssertionError("Expected procurement status command rejection");
        } catch (BusinessException expected) {
            // Business rejections must not silently succeed.
        }
    }

    private static ProcurementStatusCommand orderClose(OrderVO[] input, int mode, boolean whole) {
        return new ProcurementStatusCommand("nc.itf.pu.m21.IOrderClose", "close", new Object[] {input, mode, whole});
    }

    private static OrderVO order(OrderHeaderVO header, OrderItemVO... rows) {
        OrderVO order = new OrderVO();
        order.setParent(header);
        order.setChildren(OrderItemVO.class, rows);
        return order;
    }

    private static OrderItemVO row(IVOMeta metadata, String pk, String parent) throws Exception {
        OrderItemVO row = new OrderItemVO();
        attach(row, metadata);
        row.setPk_order_b(pk);
        if (parent != null) row.setPk_order(parent);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, IBillMeta> billMetadata() throws Exception {
        Field field = BillMetaFactory.class.getDeclaredField("classIndex");
        field.setAccessible(true);
        return (Map<Class<?>, IBillMeta>) field.get(BillMetaFactory.getInstance());
    }

    private static void attach(SuperVO value, IVOMeta meta) throws Exception {
        Field field = SuperVO.class.getDeclaredField("vometa");
        field.setAccessible(true);
        field.set(value, meta);
    }

    private static IVOMeta metadata(final String key) {
        final IAttributeMeta attribute = proxy(IAttributeMeta.class, new InvocationHandler() {
            public Object invoke(Object target, Method method, Object[] args) {
                if ("isSerializable".equals(method.getName())) return true;
                if ("isStatic".equals(method.getName())) return false;
                if ("getJavaType".equals(method.getName())) return JavaType.Object;
                if ("getName".equals(method.getName())) return key;
                throw new AssertionError(method.getName());
            }
        });
        final IVOMetaStatisticInfo statistics = proxy(IVOMetaStatisticInfo.class, new InvocationHandler() {
            public Object invoke(Object target, Method method, Object[] args) {
                if ("getStaticAttributes".equals(method.getName())) return new IAttributeMeta[0];
                throw new AssertionError(method.getName());
            }
        });
        return proxy(IVOMeta.class, new InvocationHandler() {
            public Object invoke(Object target, Method method, Object[] args) {
                if ("getStatisticInfo".equals(method.getName())) return statistics;
                if ("getPrimaryAttribute".equals(method.getName()) || "getAttribute".equals(method.getName())) return attribute;
                if ("equals".equals(method.getName())) return target == args[0];
                if ("getEntityName".equals(method.getName())) return key;
                throw new AssertionError(method.getName());
            }
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static void routesExactlyTheStage3Actions() throws Exception {
        Class<?> command;
        try {
            command = Class.forName("nc.vo.pu.qd.esign.ProcurementStatusCommand");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("The client and server need the same Stage 3 action table", missing);
        }
        Method service = command.getMethod("serviceFor", String.class, String.class);
        Method method = command.getMethod("methodFor", String.class, String.class);
        String[][] routes = {
            {"40040000", "nc.itf.pu.m422x.IStoreReqAppClose", "BillClose", "billClose", "BillOpen", "billOpen",
                "LineClose", "rowClose", "LineOpen", "rowOpen"},
            {"40040200", "nc.itf.pu.m20.IPraybillMaintain", "BillClose", "closeBill", "BillOpen", "openBill",
                "LineClose", "closeBillRow", "LineOpen", "openBillRow"},
            {"40040400", "nc.itf.pu.m21.IOrderClose", "BillClose", "close", "BillOpen", "open",
                "LineClose", "close", "LineOpen", "open"},
            {"40040400", "nc.itf.pu.m21.IOrderMaintain", "Freeze", "freezeOrder", "UnFreeze", "unfreezeOrder"},
            {"40041000", "nc.itf.pu.m25.IInvoiceMaintain", "Freeze", "freezeByLightVOs", "UnFreeze", "unFreezeByLightVOs"}
        };
        for (String[] route : routes) {
            for (int i = 2; i < route.length; i += 2) {
                check(route[1].equals(service.invoke(null, route[0], route[i])), "Wrong service for " + route[i]);
                check(route[i + 1].equals(method.invoke(null, route[0], route[i])), "Wrong method for " + route[i]);
            }
        }
        try {
            method.invoke(null, "40041000", "BillClose");
            throw new AssertionError("Invoice must reject an out-of-scope button");
        } catch (InvocationTargetException expected) {
            check(expected.getCause() instanceof BusinessException, "Expected a business exception");
        }
    }

    private static void check(boolean success, String message) {
        if (!success) {
            throw new AssertionError(message);
        }
    }
}
