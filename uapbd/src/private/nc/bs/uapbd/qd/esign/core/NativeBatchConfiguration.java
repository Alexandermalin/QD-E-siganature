package nc.bs.uapbd.qd.esign.core;

import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import nc.bs.uapbd.qd.esign.audit.SnapshotSupport;
import nc.md.persist.framework.MDPersistenceService;
import nc.vo.bd.pub.batchupdate.BatchUpdateAttrVO;
import nc.vo.bd.pub.batchupdate.BatchUpdateTabVO;
import nc.vo.pub.BusinessException;
import nc.ui.bd.supplier.config.SupBaseInfoBatchUpdateTab;
import nc.ui.bd.pub.batchupdate.config.IBatchUpdateTab;
import nc.ui.bd.supplier.finance.SupFinanceBatchUpdateTab;
import nc.ui.bd.supplier.stock.SupStockBatchUpdateTab;

/** Reads the native fast-batch tab configuration from the server, not from the client. */
public final class NativeBatchConfiguration {
    private NativeBatchConfiguration() { }
    public interface Reader { BatchUpdateTabVO tab(String pk) throws BusinessException; }
    public static final class Verified {
        private final String table,fingerprint;private final Set<String> fields;private final String[] selected;
        private Verified(String table,Set<String> fields,String[] selected,String fingerprint) {
            this.table=table;this.fields=Collections.unmodifiableSet(fields);this.selected=selected.clone();this.fingerprint=fingerprint;
        }
        String table() { return table; }
        Set<String> fields() { return fields; }
        String fingerprint() { return fingerprint; }
        boolean matches(String[] value) { return Arrays.equals(selected,value); }
    }
    /** Only dynamic main tabs using the verified native service classes are recognized. */
    public static Verified read(String domain,String tabPk,String selectedAttribute,String[] selected,Reader reader) throws BusinessException {
        String md,table,service;
        if("BD_CUSTOMER".equals(domain)) {
            md="e4f48eaf-5567-4383-a370-a59cb3e8a451";table="bd_customer";
            service="nc.ui.bd.cust.baseinfo.service.CustBaseInfoBatchUpdateService";
        } else if("BD_SUPPLIER".equals(domain)) {
            md="720dcc7c-ff19-48f4-b9c5-b90906682f45";table="bd_supplier";
            service="nc.ui.bd.supplier.baseinfo.model.SupBaseInfoBatchUpdateService";
        } else if("BD_MATERIAL".equals(domain)) {
            md="c7dc0ccd-8872-4eee-8882-160e8f49dfad";table="bd_material";
            service="nc.bs.bd.material.batchupdate.MaterialBatchUpdateService";
        } else throw new BusinessException("当前批改类型的原生配置尚未核实。");
        return readDynamic(domain,tabPk,selectedAttribute,selected,reader,md,table,service,true,null,null);
    }
    /** Dynamic tab records are selected by root MD ID; individual page fields use the page MD ID. */
    public static Verified readOrganization(NativeOrgBatchPage page,String tabPk,String tabCode,String selectedAttribute,
            String[] selected,Reader reader) throws BusinessException {
        EsignValues.require(page!=null && page.tabCode.equals(tabCode),"组织页签编码与已核实的原生页签不一致。");
        return readDynamic(page.domain,tabPk,selectedAttribute,selected,reader,page.rootMetadataId(),page.table,page.service,
            false,page.tabCode,page.metadataId);
    }
    private static Verified readDynamic(String domain,String tabPk,String selectedAttribute,String[] selected,Reader reader,
            String md,String table,String service,boolean main,String expectedCode,String pageMetadata) throws BusinessException {
        EsignValues.require(validPk(tabPk) && validName(selectedAttribute) && selected!=null && selected.length>0
            && selected.length<=100 && reader!=null,"快速批改缺少原生页签或选择字段。");
        BatchUpdateTabVO tab=reader.tab(tabPk);
        EsignValues.require(tab!=null && tabPk.equals(tab.getPk_batchupdatetab()) && md.equals(tab.getMdid())
            && active(tab.getDr()) && tab.getIsmaintab()!=null && tab.getIsmaintab().booleanValue()==main && tab.getAttrs()!=null
            && (expectedCode==null || expectedCode.equals(tab.getCode())),"快速批改页签不存在、已变更或类型不匹配。");
        Map<String,BatchUpdateAttrVO> configured=new HashMap<String,BatchUpdateAttrVO>();Set<String> ids=new HashSet<String>();
        for(BatchUpdateAttrVO attr:tab.getAttrs()) {
            if(attr==null || !active(attr.getDr())) continue;
            EsignValues.require(tabPk.equals(attr.getPk_batchupdatetab()) && validPk(attr.getPk_batchupdateattr())
                && ids.add(attr.getPk_batchupdateattr()) && validName(attr.getCode()) && configured.put(attr.getCode(),attr)==null,
                "快速批改原生字段配置重复或不完整。");
        }
        Set<String> choices=new HashSet<String>(),writeFields=new LinkedHashSet<String>();StringBuilder evidence=new StringBuilder();
        evidence.append(pack("nc-batch-config-v1",domain,tabPk,md,tab.getCode(),tab.getNodekey(),selectedAttribute,time(tab.getTs())));
        if(!main) evidence.append(pack("organization",pageMetadata));
        for(String name:selected) {
            BatchUpdateAttrVO attr=configured.get(name);
            // NC chooses one service using selectedAttribute; other selected fields may name a different service.
            EsignValues.require(validName(name) && choices.add(name) && attr!=null
                && (!(main || name.equals(selectedAttribute)) || service.equals(attr.getService()))
                && (attr.getValidator()==null || attr.getValidator().trim().isEmpty()),
                "快速批改字段、原生服务或自定义校验尚未核实。");
            writeFields.add(name);
            String related=attr.getRelatedattr();
            if(related!=null) for(String field:related.split(",",-1)) {
                if(field.length()==0) continue; // NC's StringUtils.split ignores empty tokens.
                EsignValues.require(validName(field),"快速批改的原生联动字段无效。");writeFields.add(field);
            }
            evidence.append(pack(name,attr.getPk_batchupdateattr(),attr.getService(),related,attr.getValidator(),attr.getEditlistener(),
                attr.getIssingleorg()==null?null:attr.getIssingleorg().toString(),time(attr.getTs())));
        }
        EsignValues.require(choices.contains(selectedAttribute) && writeFields.size()<=100,"快速批改选中字段或联动字段范围不一致。");
        return new Verified(table,writeFields,selected,SnapshotSupport.sha256(evidence.toString()));
    }
    public static Reader database() {
        return new Reader() { public BatchUpdateTabVO tab(String pk) throws BusinessException {
            Object value=MDPersistenceService.lookupPersistenceQueryService().queryBillOfVOByPK(BatchUpdateTabVO.class,pk,false);
            EsignValues.require(value==null || value.getClass()==BatchUpdateTabVO.class,"原生批改页签返回了未知类型。");
            return (BatchUpdateTabVO)value;
        }};
    }
    /** NC also ships an explicit supplier main tab outside the dynamic tab table. */
    public static Verified readBuiltInSupplier(String tabCode,String selectedAttribute,String[] selected) throws BusinessException {
        SupBaseInfoBatchUpdateTab tab=new SupBaseInfoBatchUpdateTab();
        EsignValues.require("baseinfo".equals(tabCode) && tab.getID()==null && tab.isMainTab()
            && "baseinfo".equals(tab.getCode()) && validName(selectedAttribute) && selected!=null && selected.length>0
            && selected.length<=100,"供应商快速批改的内置页签无效。");
        String[] configured=tab.getAttrs(),single=tab.getSingleOrgAttrs();
        EsignValues.require(configured!=null && single!=null && tab.getBatchUpdateService(selectedAttribute)!=null
            && "nc.ui.bd.supplier.baseinfo.model.SupBaseInfoBatchUpdateService".equals(tab.getBatchUpdateService(selectedAttribute).getClass().getName()),
            "供应商快速批改内置服务已变化。");
        Set<String> allowed=new HashSet<String>(Arrays.asList(configured)),choices=new HashSet<String>(),fields=new LinkedHashSet<String>();
        StringBuilder evidence=new StringBuilder(pack("nc-batch-builtin-v1",tab.getClass().getName(),tabCode,selectedAttribute));
        for(String name:selected) {
            EsignValues.require(validName(name) && choices.add(name) && allowed.contains(name),"供应商快速批改字段不在内置配置中。");
            fields.add(name);String[] related=tab.getOtherAttrsByAttr(name);
            if(related!=null) for(String value:related) { EsignValues.require(validName(value),"供应商批改联动字段无效。");fields.add(value); }
            evidence.append(pack(name,Arrays.asList(single).contains(name)?"1":"0"));
        }
        EsignValues.require(choices.contains(selectedAttribute) && fields.size()<=100,"供应商快速批改选择不一致。");
        return new Verified("bd_supplier",fields,selected,SnapshotSupport.sha256(evidence.toString()));
    }
    /** Finance and stock are NC-supplied supplier tabs without dynamic tab-table records. */
    public static Verified readBuiltInSupplierOrganization(NativeOrgBatchPage page,String selectedAttribute,String[] selected)
            throws BusinessException {
        EsignValues.require(page==NativeOrgBatchPage.SUPPLIER_FINANCE || page==NativeOrgBatchPage.SUPPLIER_STOCK,
            "供应商组织批改内置页签尚未核实。");
        IBatchUpdateTab tab=page==NativeOrgBatchPage.SUPPLIER_FINANCE?new SupFinanceBatchUpdateTab():new SupStockBatchUpdateTab();
        EsignValues.require(tab.getID()==null && !tab.isMainTab() && page.tabCode.equals(tab.getCode())
            && validName(selectedAttribute) && selected!=null && selected.length>0 && selected.length<=100,
            "供应商组织批改内置页签无效。");
        String[] configured=tab.getAttrs(),single=tab.getSingleOrgAttrs();
        EsignValues.require(configured!=null && single!=null,"供应商组织批改字段配置不完整。");
        Set<String> allowed=new HashSet<String>(Arrays.asList(configured)),choices=new HashSet<String>(),fields=new LinkedHashSet<String>();
        StringBuilder evidence=new StringBuilder(pack("nc-batch-org-builtin-v1",page.domain,page.metadataId,page.table,
            tab.getClass().getName(),tab.getCode(),selectedAttribute));
        for(String name:selected) {
            EsignValues.require(validName(name) && choices.add(name) && allowed.contains(name),"供应商组织批改字段不在原生配置中。");
            Object nativeService=tab.getBatchUpdateService(name);
            EsignValues.require(nativeService!=null && page.service.equals(nativeService.getClass().getName())
                && tab.getValidatorByAttr(name)==null,"供应商组织批改服务或自定义校验尚未核实。");
            fields.add(name);String[] related=tab.getOtherAttrsByAttr(name);
            if(related!=null) for(String value:related) { EsignValues.require(validName(value),"供应商批改联动字段无效。");fields.add(value); }
            Object listener=tab.getListenerByAttr(name);
            evidence.append(pack(name,Arrays.asList(single).contains(name)?"1":"0",listener==null?null:listener.getClass().getName()));
        }
        EsignValues.require(choices.contains(selectedAttribute) && fields.size()<=100,"供应商组织批改选择不一致。");
        return new Verified(page.table,fields,selected,SnapshotSupport.sha256(evidence.toString()));
    }
    private static boolean active(Integer dr) { return dr==null || dr.intValue()==0; }
    private static boolean validName(String value) { return value!=null && value.matches("[A-Za-z][A-Za-z0-9_]{0,63}"); }
    private static boolean validPk(String value) { return value!=null && value.matches("[A-Za-z0-9_-]{1,20}"); }
    private static String time(Object value) { return value==null?null:value.toString(); }
    private static String pack(String... values) {
        StringBuilder result=new StringBuilder();for(String value:values) result.append(value==null?-1:value.length()).append(':').append(value==null?"":value);
        return result.toString();
    }
}
