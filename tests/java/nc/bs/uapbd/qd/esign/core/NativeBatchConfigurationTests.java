package nc.bs.uapbd.qd.esign.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import nc.vo.bd.pub.batchupdate.BatchUpdateAttrVO;
import nc.vo.bd.pub.batchupdate.BatchUpdateTabVO;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFBoolean;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.uapbd.qd.esign.PartnerBatchDraft;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.md.model.IBean;

/** NC dynamic tab selection, related fields and native service configuration are bound to the authorization. */
public final class NativeBatchConfigurationTests {
    private NativeBatchConfigurationTests() { }
    public static void run() throws Exception {
        Fixture f=new Fixture();String[] selected={"name","taxcode"};
        NativeBatchConfiguration.Verified config=f.read(selected,"name");
        require(config.fields().equals(new HashSet<String>(Arrays.asList("name","shortname","taxcode")))
            && config.matches(selected) && !config.matches(new String[]{"taxcode","name"}),"Native related fields or selection were lost");
        NativeBatchMetadataFieldsTests.Fixture metadata=new NativeBatchMetadataFieldsTests.Fixture();
        metadata.addRelated("shortname");metadata.addRelated("taxcode");
        Map<String,Object> values=new HashMap<String,Object>();values.put("name","n");values.put("shortname","s");values.put("taxcode","t");
        NativeBatchChangeSet before=NativeBatchChangeSet.create(selected,values,
            NativeBatchMetadataFields.verify(metadata.bean,"bd_customer",selected,config));
        f.name.setTs(new UFDateTime(2000L));NativeBatchConfiguration.Verified newer=f.read(selected,"name");
        NativeBatchChangeSet after=NativeBatchChangeSet.create(selected,values,
            NativeBatchMetadataFields.verify(metadata.bean,"bd_customer",selected,newer));
        require(!before.getFingerprint().equals(after.getFingerprint()),"Changed native configuration did not invalidate authorization");
        try { NativeBatchMetadataFields.verify(metadata.bean,"bd_customer",new String[]{"name"},config);
            throw new AssertionError("A different native field selection reused the configuration"); }
        catch(BusinessException expected) { }
        reject(f,selected,"missing");
        f.name.setService("untrusted.service");reject(f,selected,"name");f.name.setService(Fixture.SERVICE);
        f.name.setValidator("unverified.validator");reject(f,selected,"name");f.name.setValidator(null);
        f.name.setRelatedattr("bad field");reject(f,selected,"name");f.name.setRelatedattr("shortname");
        f.tab.setMdid("another-metadata");reject(f,selected,"name");f.tab.setMdid(Fixture.MD);
        f.tab.setIsmaintab(UFBoolean.FALSE);reject(f,selected,"name");f.tab.setIsmaintab(UFBoolean.TRUE);
        f.tax.setDr(Integer.valueOf(1));reject(f,selected,"name");f.tax.setDr(Integer.valueOf(0));
        f.tab.setAttrs(new BatchUpdateAttrVO[]{f.name,f.name});reject(f,new String[]{"name"},"name");
        NativeBatchConfiguration.Verified builtIn=NativeBatchConfiguration.readBuiltInSupplier("baseinfo","trade",new String[]{"trade","pk_areacl"});
        require(builtIn.fields().equals(new HashSet<String>(Arrays.asList("trade","pk_areacl"))) && "bd_supplier".equals(builtIn.table()),
            "Native built-in supplier main tab was not recognized");
        try { NativeBatchConfiguration.readBuiltInSupplier("baseinfo","trade",new String[]{"trade","injected"});
            throw new AssertionError("An unconfigured built-in supplier field was accepted"); }
        catch(BusinessException expected) { }
        final Fixture material=new Fixture();
        material.tab.setMdid("c7dc0ccd-8872-4eee-8882-160e8f49dfad");
        material.name.setService("nc.bs.bd.material.batchupdate.MaterialBatchUpdateService");
        material.tax.setService("nc.bs.bd.material.batchupdate.MaterialBatchUpdateService");
        NativeBatchConfiguration.Reader materialReader=new NativeBatchConfiguration.Reader() {
            public BatchUpdateTabVO tab(String pk) { return material.tab; }
        };
        NativeBatchConfiguration.Verified materialConfig=NativeBatchConfiguration.read("BD_MATERIAL","TAB","name",selected,materialReader);
        require("bd_material".equals(materialConfig.table()),"Material batch did not bind its native main table");
        material.name.setService("untrusted.material.service");
        try { NativeBatchConfiguration.read("BD_MATERIAL","TAB","name",selected,materialReader);
            throw new AssertionError("Untrusted material batch service accepted"); }
        catch(BusinessException expected) { }
        organizationTabs();
        System.out.println("PASS NativeBatchConfigurationTests (server-read native tab and related fields; no NC or Oracle)");
    }
    private static void organizationTabs() throws Exception {
        final Fixture f=new Fixture();final NativeOrgBatchPage page=NativeOrgBatchPage.CUSTOMER_FINANCE;
        final String[] selected={"name","taxcode"};
        f.tab.setCode(page.tabCode);f.tab.setIsmaintab(UFBoolean.FALSE);
        f.name.setService(page.service);f.tax.setService(page.service);
        NativeBatchConfiguration.Reader reader=new NativeBatchConfiguration.Reader() { public BatchUpdateTabVO tab(String pk) { return f.tab; } };
        NativeBatchConfiguration.Verified config=NativeBatchConfiguration.readOrganization(page,"TAB",page.tabCode,"name",selected,reader);
        require(page.table.equals(config.table()) && config.fields().contains("shortname"),"Organization tab lost native fields");
        NativeBatchMetadataFieldsTests.Fixture metadata=new NativeBatchMetadataFieldsTests.Fixture();metadata.table.put("getName",page.table);
        metadata.addRelated("shortname");metadata.addRelated("taxcode");
        Map<String,Object> values=new HashMap<String,Object>();values.put("name","n");values.put("shortname","s");values.put("taxcode","t");
        NativeBatchChangeSet before=NativeBatchChangeSet.create(selected,values,
            NativeBatchMetadataFields.verify(metadata.bean,page.table,selected,config));
        f.tax.setService("another.configured.service");
        NativeBatchConfiguration.Verified alternate=NativeBatchConfiguration.readOrganization(page,"TAB",page.tabCode,"name",selected,reader);
        require(!before.getFingerprint().equals(NativeBatchChangeSet.create(selected,values,
            NativeBatchMetadataFields.verify(metadata.bean,page.table,selected,alternate)).getFingerprint()),
            "Non-dispatch field service metadata was not bound to the signed configuration");
        f.tax.setService(page.service);
        final IBean verifiedBean=metadata.bean;
        PartnerBatchDraft draft=new PartnerBatchDraft("TAB",page.tabCode,"name",selected,values,new String[]{"T2","T1"});
        PartnerBatchPayload.Organization prepared=PartnerBatchPayload.resolveOrganization("BD_CUSTOMER",draft,reader,
            new PartnerBatchPayload.BeanReader() { public IBean bean(String id) {
                require(page.metadataId.equals(id),"Organization payload used root metadata for page fields");return verifiedBean;
            }});
        require(prepared.getPage()==page && prepared.getTargets().equals(Arrays.asList("T1","T2"))
            && before.getFingerprint().equals(prepared.getChanges().getFingerprint()),"Organization payload lost its targets or verified change values");
        List<BusinessObjectRef> roots=Arrays.asList(new BusinessObjectRef("a","BD_CUSTOMER","A","A",null),
            new BusinessObjectRef("b","BD_CUSTOMER","B","B",null));
        final List<NativeOrgBatchSelection.Row> rows=new ArrayList<NativeOrgBatchSelection.Row>();
        rows.add(new NativeOrgBatchSelection.Row("P1","A","T1","G"));
        rows.add(new NativeOrgBatchSelection.Row("P2","A","T2","G"));
        final boolean[] locked={false};
        NativeOrgBatchSelection.Reader pageReader=new NativeOrgBatchSelection.Reader() {
            public List<NativeOrgBatchSelection.Row> read(NativeOrgBatchPage p,String[] rootIds,String[] targets,boolean lock) {
                locked[0]=lock;return new ArrayList<NativeOrgBatchSelection.Row>(rows);
            }
        };
        final boolean[] evidenceLocked={false};
        PartnerOrgBatchScope.SnapshotReader snapshotReader=new PartnerOrgBatchScope.SnapshotReader() {
            public NativeOrgBatchSnapshots.Capture capture(NativeOrgBatchSelection.Impact impact,boolean lock) throws BusinessException {
                evidenceLocked[0]=lock;return NativeOrgBatchSnapshotTests.captureForImpact(impact,lock);
            }
        };
        PartnerOrgBatchScope.Prepared scope=PartnerOrgBatchScope.prepare("G","ORG","BD_CUSTOMER",roots,prepared,pageReader,snapshotReader);
        require(!locked[0] && scope.getImpact().getRows().size()==2,"Signing did not capture the actual organization rows");
        PartnerBatchDraft planned=new PartnerBatchDraft("TAB",page.tabCode,"name",selected,values,new String[]{"T2","T1"},
            new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page[]{new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page("P1","A","T1"),
                new nc.vo.uapbd.qd.esign.PartnerOrgBatchPlan.Page("P2","A","T2")});
        PartnerBatchDraft mainDraft=new PartnerBatchDraft("TAB","baseinfo","name",selected,values);
        List<BusinessObjectRef> mainRefs=NativeBatchRequestScope.bind("G","ORG","BD_CUSTOMER",roots,before);
        require(PartnerOrgBatchRouteGuard.isOrganization(scope.getObjects(),planned)
            && !PartnerOrgBatchRouteGuard.isOrganization(mainRefs,mainDraft),"Organization and main-tab requests were confused");
        rejectRoute(scope.getObjects(),draft);rejectRoute(scope.getObjects(),mainDraft);rejectRoute(mainRefs,planned);
        List<BusinessObjectRef> mixed=new ArrayList<BusinessObjectRef>(scope.getObjects());mixed.set(1,mainRefs.get(1));
        rejectRoute(mixed,planned);
        targetScope(page,selected,values,reader,verifiedBean,roots,pageReader,snapshotReader);
        nc.impl.uapbd.qd.esign.NativeOrgBatchNativeServiceTests.run();
        nc.impl.uapbd.qd.esign.NativeOrgBatchNativeServiceTests.verifyUnverified(scope);
        try { scope.requireLockedVerification();throw new AssertionError("Unsigned preparation was accepted for native execution"); }
        catch(BusinessException expected) { }
        try { NativeOrgBatchReconciliation.reconcile(scope,"0123456789abcdef0123456789abcdef","G","ORG","BD_CUSTOMER",null,null,null,null,
                java.util.Collections.<String,String>emptyMap());
            throw new AssertionError("Unlocked organization scope was accepted for reconciliation"); }
        catch(BusinessException expected) { }
        try { PartnerOrgBatchScope.verify("G","ORG","BD_CUSTOMER",scope.getObjects(),prepared,pageReader,snapshotReader,true,null);
            throw new AssertionError("An organization scope without a lock ticket was accepted"); } catch(BusinessException expected) { }
        final NativeOrgBatchTableLockTests.Fixture lockFixture=new NativeOrgBatchTableLockTests.Fixture();
        NativeOrgBatchTableLock.Ticket ticket=lockFixture.ticket();
        PartnerOrgBatchScope.SnapshotReader changedMetadata=new PartnerOrgBatchScope.SnapshotReader() {
            public NativeOrgBatchSnapshots.Capture capture(NativeOrgBatchSelection.Impact impact,boolean lock) throws BusinessException {
                return NativeOrgBatchSnapshotTests.captureForImpact(impact,lock,false);
            }
        };
        PartnerOrgBatchScope.Prepared differentGraph=PartnerOrgBatchScope.prepare("G","ORG","BD_CUSTOMER",roots,prepared,pageReader,changedMetadata);
        try { PartnerOrgBatchScope.verify("G","ORG","BD_CUSTOMER",differentGraph.getObjects(),prepared,pageReader,changedMetadata,true,ticket);
            throw new AssertionError("A signed page graph that differed from the locked tables was accepted");
        } catch(BusinessException expected) { }
        PartnerOrgBatchScope.Prepared lockedScope=PartnerOrgBatchScope.verify("G","ORG","BD_CUSTOMER",scope.getObjects(),prepared,pageReader,snapshotReader,true,ticket);
        lockedScope.requireLockedVerification();
        String requestId="0123456789abcdef0123456789abcdef",otherRequest="ffffffffffffffffffffffffffffffff";
        try { lockedScope.requireRequest(requestId);throw new AssertionError("Unbound locked scope was accepted for a request"); }
        catch(BusinessException expected) { }
        nc.vo.uapbd.qd.esign.OperationContext executionContext=new nc.vo.uapbd.qd.esign.OperationContext(requestId,requestId,
            "G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,lockedScope.getObjects(),
            nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL);
        PartnerOrgBatchScope.Prepared requestScope=lockedScope.bindRequest(executionContext);requestScope.requireRequest(requestId);
        requestScope.requireExecutionContext(executionContext);
        try { requestScope.requireRequest(otherRequest);throw new AssertionError("Locked scope was reused by another request"); }
        catch(BusinessException expected) { }
        nc.vo.uapbd.qd.esign.OperationContext otherContext=new nc.vo.uapbd.qd.esign.OperationContext(otherRequest,otherRequest,
            "G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,lockedScope.getObjects(),
            nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL);
        try { requestScope.bindRequest(otherContext);throw new AssertionError("Locked scope was rebound to another request"); }
        catch(BusinessException expected) { }
        nc.vo.uapbd.qd.esign.OperationContext otherNode=new nc.vo.uapbd.qd.esign.OperationContext(requestId,requestId,
            "G","ORG","10140CUO","客户","BatchUpdate","批改","UPDATE",null,lockedScope.getObjects(),
            nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL);
        try { requestScope.requireExecutionContext(otherNode);throw new AssertionError("Locked scope was reused for another node"); }
        catch(BusinessException expected) { }
        List<BusinessObjectRef> changedRefs=new ArrayList<BusinessObjectRef>(lockedScope.getObjects());BusinessObjectRef first=changedRefs.get(0);
        changedRefs.set(0,new BusinessObjectRef(first.getClientObjectId(),first.getObjectType(),first.getObjectPk(),"OTHER-CODE",
            first.getParentClientObjectId(),first.getDesiredOwnerOrg(),first.getPolicyOwnerOrg(),first.getTargetOrg()));
        nc.vo.uapbd.qd.esign.OperationContext changedContext=new nc.vo.uapbd.qd.esign.OperationContext(requestId,requestId,
            "G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,changedRefs,
            nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL);
        try { requestScope.requireExecutionContext(changedContext);throw new AssertionError("Locked scope accepted a changed archive code"); }
        catch(BusinessException expected) { }
        nc.impl.uapbd.qd.esign.NativeOrgBatchNativeServiceTests.verifyExecution(lockedScope);
        Object transaction=lockFixture.key;lockFixture.key=new Object();
        try { lockedScope.requireLockedVerification();throw new AssertionError("The scope survived a transaction change"); }
        catch(BusinessException expected) { } finally { lockFixture.key=transaction; }
        try { nc.impl.uapbd.qd.esign.NativeOrgBatchNativeServiceTests.verifyTransactionChange(lockedScope,new Runnable() {
                public void run() { lockFixture.key=new Object(); }
            });
        } finally { lockFixture.key=transaction; }
        final PartnerOrgBatchScope.Prepared foreignThreadScope=lockedScope;final boolean[] denied={false};
        Thread foreign=new Thread(new Runnable() { public void run() {
            try { foreignThreadScope.requireLockedVerification(); } catch(BusinessException expected) { denied[0]=true; }
        }});
        foreign.start();foreign.join();require(denied[0],"A different thread reused the native page locks");
        require(locked[0] && evidenceLocked[0],"Execution-side scope verification did not lock page and associated evidence");
        rows.remove(1);
        try { PartnerOrgBatchScope.verify("G","ORG","BD_CUSTOMER",scope.getObjects(),prepared,pageReader,snapshotReader,true,ticket);
            throw new AssertionError("A changed organization row set reused signed authorization"); } catch(BusinessException expected) { }
        rows.clear();
        PartnerOrgBatchScope.Prepared empty=PartnerOrgBatchScope.prepare("G","ORG","BD_CUSTOMER",roots,prepared,pageReader,snapshotReader);
        PartnerOrgBatchScope.Prepared emptyLocked=PartnerOrgBatchScope.verify("G","ORG","BD_CUSTOMER",empty.getObjects(),prepared,pageReader,snapshotReader,true,ticket);
        nc.impl.uapbd.qd.esign.NativeOrgBatchNativeServiceTests.verifyEmpty(emptyLocked);
        nc.vo.uapbd.qd.esign.OperationContext emptyContext=new nc.vo.uapbd.qd.esign.OperationContext(requestId,requestId,
            "G","ORG","10140CUG","客户","BatchUpdate","批改","UPDATE",null,emptyLocked.getObjects(),
            nc.vo.uapbd.qd.esign.OperationContext.OWNER_POLICY_PROTOCOL);
        nc.impl.uapbd.qd.esign.NativeOrgBatchExecutionTests.run(requestScope,executionContext,emptyLocked.bindRequest(emptyContext),emptyContext);
        try { PartnerBatchPayload.resolveOrganization("BD_SUPPLIER",draft,reader,
                new PartnerBatchPayload.BeanReader() { public IBean bean(String id) { return verifiedBean; }});
            throw new AssertionError("Another archive domain accepted the customer page"); } catch(BusinessException expected) { }
        f.name.setTs(new UFDateTime(3000L));
        NativeBatchConfiguration.Verified changed=NativeBatchConfiguration.readOrganization(page,"TAB",page.tabCode,"name",selected,reader);
        require(!before.getFingerprint().equals(NativeBatchChangeSet.create(selected,values,
            NativeBatchMetadataFields.verify(metadata.bean,page.table,selected,changed)).getFingerprint()),"Changed page configuration did not invalidate the signed values");
        f.tab.setMdid(page.metadataId);rejectOrganization(page,f,selected);f.tab.setMdid(page.rootMetadataId());
        f.tab.setCode("sale_info");rejectOrganization(page,f,selected);f.tab.setCode(page.tabCode);
        f.tab.setIsmaintab(UFBoolean.TRUE);rejectOrganization(page,f,selected);f.tab.setIsmaintab(UFBoolean.FALSE);
        f.name.setService("untrusted.service");rejectOrganization(page,f,selected);f.name.setService(page.service);
        f.name.setValidator("unverified.validator");rejectOrganization(page,f,selected);f.name.setValidator(null);
        NativeBatchConfiguration.Verified finance=NativeBatchConfiguration.readBuiltInSupplierOrganization(
            NativeOrgBatchPage.SUPPLIER_FINANCE,"pk_respdept",new String[]{"pk_respdept","cooperateflag"});
        NativeBatchConfiguration.Verified stock=NativeBatchConfiguration.readBuiltInSupplierOrganization(
            NativeOrgBatchPage.SUPPLIER_STOCK,"respdept",new String[]{"respdept","respperson"});
        require("bd_supfinance".equals(finance.table()) && "bd_supstock".equals(stock.table()),"Native supplier organization tabs not recognized");
        try { NativeBatchConfiguration.readBuiltInSupplierOrganization(NativeOrgBatchPage.SUPPLIER_STOCK,"respdept",new String[]{"respdept","injected"});
            throw new AssertionError("Unconfigured supplier organization field accepted"); } catch(BusinessException expected) { }
        try { NativeBatchConfiguration.readBuiltInSupplierOrganization(page,"name",new String[]{"name"});
            throw new AssertionError("Wrong built-in organization page accepted"); } catch(BusinessException expected) { }
    }
    private static void rejectRoute(List<BusinessObjectRef> refs,java.io.Serializable command) throws Exception {
        try { PartnerOrgBatchRouteGuard.isOrganization(refs,command);throw new AssertionError("Mismatched batch route was accepted"); }
        catch(BusinessException expected) { }
    }
    private static void targetScope(NativeOrgBatchPage page,String[] selected,Map<String,Object> values,
            NativeBatchConfiguration.Reader reader,final IBean verifiedBean,List<BusinessObjectRef> roots,
            NativeOrgBatchSelection.Reader pageReader,PartnerOrgBatchScope.SnapshotReader snapshots) throws Exception {
        String request="0123456789abcdef0123456789abcdef";
        PartnerBatchDraft withEmptyTarget=new PartnerBatchDraft("TAB",page.tabCode,"name",selected,values,new String[]{"T3","T2","T1"});
        PartnerBatchPayload.Organization payload=PartnerBatchPayload.resolveOrganization("BD_CUSTOMER",withEmptyTarget,reader,
            new PartnerBatchPayload.BeanReader() { public IBean bean(String id) { return verifiedBean; } });
        PartnerOrgBatchScope.Prepared prepared=PartnerOrgBatchScope.prepare("G","ORG","BD_CUSTOMER",roots,payload,pageReader,snapshots);
        NativeOrgBatchTargetScope.Evidence evidence=NativeOrgBatchTargetScope.capture(request,"G","ORG",prepared);
        require(evidence.getTargets().equals(Arrays.asList("T1","T2","T3")) && evidence.getPage()==page,
            "Preparation lost a target with no matching page");
        List<BusinessObjectRef> reordered=new ArrayList<BusinessObjectRef>(prepared.getObjects());java.util.Collections.reverse(reordered);
        NativeOrgBatchTargetScope.Evidence restored=NativeOrgBatchTargetScope.restore(request,"G","ORG",page,reordered,
            Arrays.asList("T3","T1","T2"),evidence.getDigest());
        require(restored.getTargets().equals(evidence.getTargets()),"Stored target order changed the signed scope");
        try { restored.getTargets().clear();throw new AssertionError("Prepared targets are mutable"); } catch(UnsupportedOperationException expected) { }
        rejectTargetScope(request,"G","ORG",page,prepared.getObjects(),Arrays.asList("T1","T2"),evidence.getDigest());
        rejectTargetScope(request,"G","ORG",page,prepared.getObjects(),Arrays.asList("T1","T2","T3","T4"),evidence.getDigest());
        rejectTargetScope(request,"G","ORG",page,prepared.getObjects().subList(0,1),evidence.getTargets(),evidence.getDigest());
        rejectTargetScope(request,"G","OTHER",page,prepared.getObjects(),evidence.getTargets(),evidence.getDigest());
        rejectTargetScope("1123456789abcdef0123456789abcdef","G","ORG",page,prepared.getObjects(),evidence.getTargets(),evidence.getDigest());
        rejectTargetScope(request,"G","ORG",page,prepared.getObjects(),Arrays.asList("T1","T1","T3"),evidence.getDigest());
    }
    private static void rejectTargetScope(String request,String group,String org,NativeOrgBatchPage page,List<BusinessObjectRef> objects,
            List<String> targets,String digest) throws Exception {
        try { NativeOrgBatchTargetScope.restore(request,group,org,page,objects,targets,digest);
            throw new AssertionError("Incomplete prepared target scope accepted"); } catch(BusinessException expected) { }
    }
    private static void rejectOrganization(final NativeOrgBatchPage page,final Fixture fixture,String[] selected) throws Exception {
        try { NativeBatchConfiguration.readOrganization(page,"TAB",page.tabCode,"name",selected,new NativeBatchConfiguration.Reader() {
                public BatchUpdateTabVO tab(String pk) { return fixture.tab; }
            });throw new AssertionError("Unverified organization tab accepted"); } catch(BusinessException expected) { }
    }
    private static final class Fixture {
        static final String MD="e4f48eaf-5567-4383-a370-a59cb3e8a451";
        static final String SERVICE="nc.ui.bd.cust.baseinfo.service.CustBaseInfoBatchUpdateService";
        final BatchUpdateTabVO tab=new BatchUpdateTabVO();final BatchUpdateAttrVO name=attr("N","name"),tax=attr("T","taxcode");
        Fixture() { tab.setPk_batchupdatetab("TAB");tab.setMdid(MD);tab.setCode("baseinfo");tab.setIsmaintab(UFBoolean.TRUE);
            name.setRelatedattr("shortname");tab.setAttrs(new BatchUpdateAttrVO[]{name,tax}); }
        NativeBatchConfiguration.Verified read(String[] selected,String main) throws BusinessException {
            return NativeBatchConfiguration.read("BD_CUSTOMER","TAB",main,selected,new NativeBatchConfiguration.Reader() {
                public BatchUpdateTabVO tab(String pk) { return tab; }
            });
        }
        private static BatchUpdateAttrVO attr(String pk,String code) {
            BatchUpdateAttrVO value=new BatchUpdateAttrVO();value.setPk_batchupdatetab("TAB");value.setPk_batchupdateattr(pk);
            value.setCode(code);value.setService(SERVICE);return value;
        }
    }
    private static void reject(Fixture f,String[] selected,String main) throws Exception {
        try { f.read(selected,main);throw new AssertionError("Unverified native batch configuration accepted"); }
        catch(BusinessException expected) { }
    }
    private static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
