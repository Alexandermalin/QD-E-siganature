package nc.bs.uapbd.qd.esign.core;

import nc.vo.uapbd.qd.esign.MaterialOrgPage;

/** Fixed physical relations used to resolve an archive owner; no client-supplied SQL identifiers. */
public enum BaseArchiveOwnerType {
    MATERIAL("BD_MATERIAL", "bd_material", "pk_material", null, null, "bd_marorg", "pk_marorg"),
    CUSTOMER("BD_CUSTOMER", "bd_customer", "pk_customer", null, null, "bd_custorg", "pk_custorg"),
    CUSTOMER_FINANCE("BD_CUSTOMER_FINANCE", CUSTOMER, "bd_custfinance", "pk_custfinance"),
    CUSTOMER_SALE("BD_CUSTOMER_SALE", CUSTOMER, "bd_custsale", "pk_custsale"),
    CUSTOMER_CREDIT("BD_CUSTOMER_CREDIT", CUSTOMER, "bd_custcreditctl", "pk_custcreditctl"),
    CUSTOMER_BANK("BD_CUSTOMER_BANK", CUSTOMER, "bd_custbank", "pk_custbank", "pk_cust", 1),
    SUPPLIER("BD_SUPPLIER", "bd_supplier", "pk_supplier", null, null, "bd_suporg", "pk_suporg"),
    SUPPLIER_FINANCE("BD_SUPPLIER_FINANCE", SUPPLIER, "bd_supfinance", "pk_supfinance"),
    SUPPLIER_STOCK("BD_SUPPLIER_STOCK", SUPPLIER, "bd_supstock", "pk_supstock"),
    SUPPLIER_BANK("BD_SUPPLIER_BANK", SUPPLIER, "bd_custbank", "pk_custbank", "pk_cust", 3),
    CUSTOMER_CLASS("BD_CUSTCLASS", "bd_custclass", "pk_custclass", null, null, null, null),
    SUPPLIER_CLASS("BD_SUPPLIERCLASS", "bd_supplierclass", "pk_supplierclass", null, null, null, null),
    MATERIAL_CLASS("BD_MARBASCLASS", "bd_marbasclass", "pk_marbasclass", null, null, null, null),
    WAREHOUSE("BD_STORDOC", "bd_stordoc", "pk_stordoc", null, null, null, null),
    RACK("BD_RACK", "bd_rack", "pk_rack", null, null, null, null);

    public final String domain, rootTable, rootKey, childTable, childKey, childRootKey, assignmentTable, assignmentKey;
    /** Bank-account relations have no group/org columns; accclass distinguishes customer and supplier. */
    public final Integer bankAccountClass;
    public final BaseArchiveOwnerType rootType;

    BaseArchiveOwnerType(String domain, String rootTable, String rootKey, String childTable, String childKey,
            String assignmentTable, String assignmentKey) {
        this.domain = domain;
        this.rootTable = rootTable;
        this.rootKey = rootKey;
        this.childTable = childTable;
        this.childKey = childKey;
        this.childRootKey = rootKey;
        this.bankAccountClass = null;
        this.assignmentTable = assignmentTable;
        this.assignmentKey = assignmentKey;
        this.rootType = this;
    }

    BaseArchiveOwnerType(String domain, BaseArchiveOwnerType root, String childTable, String childKey) {
        this(domain,root,childTable,childKey,root.rootKey,null);
    }

    BaseArchiveOwnerType(String domain, BaseArchiveOwnerType root, String childTable, String childKey,
            String childRootKey, Integer bankAccountClass) {
        this.domain = domain;
        this.rootTable = root.rootTable;
        this.rootKey = root.rootKey;
        this.childTable = childTable;
        this.childKey = childKey;
        this.childRootKey = childRootKey;
        this.bankAccountClass = bankAccountClass;
        this.assignmentTable = root.assignmentTable;
        this.assignmentKey = root.assignmentKey;
        this.rootType = root;
    }

    public static BaseArchiveOwnerType forDomain(String domain) {
        if (MaterialOrgPage.forDomain(domain) != null) return MATERIAL;
        for (BaseArchiveOwnerType type : values()) if (type.domain.equals(domain)) return type;
        return null;
    }

    public boolean isRoot(String objectType) { return domain.equals(objectType) && rootType == this; }
}
