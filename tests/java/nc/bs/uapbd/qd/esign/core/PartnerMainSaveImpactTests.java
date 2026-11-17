package nc.bs.uapbd.qd.esign.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nc.vo.pub.BusinessException;

/** Models NC's unfiltered innercode-prefix updates and customer/supplier shared-master side effects. */
public final class PartnerMainSaveImpactTests {
    private PartnerMainSaveImpactTests() { }
    public static void run() throws Exception {
        Data data=new Data();
        PartnerMainSaveImpact.Selection ordinary=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P1",true,data,false);
        require(ordinary.getArchives().size()==2 && contains(ordinary,"BD_CUSTOMER","C") && contains(ordinary,"BD_SUPPLIER","C"),
            "A paired ordinary save did not include both physical main archives");
        PartnerMainSaveImpact.Selection claimedUnpaired=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P1",false,data,false);
        require(claimedUnpaired.hasCounterpart() && contains(claimedUnpaired,"BD_SUPPLIER","C"),
            "A client flag hid the physically linked supplier from authorization");
        Data withoutCounterpart=new Data();withoutCounterpart.archives.remove("BD_SUPPLIER/C");
        PartnerMainSaveImpact.Selection unpaired=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P1",false,withoutCounterpart,false);
        require(!unpaired.hasCounterpart() && unpaired.getArchives().size()==1,"An absent counterpart was fabricated");
        withoutCounterpart.archive("BD_SUPPLIER","C","G","G",null);
        rejectChanged(unpaired,PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P1",false,withoutCounterpart,false));
        Data crossed=new Data();crossed.archive("BD_SUPPLIER","C","OTHER","G",null);rejectSelect(crossed,"P1",false);
        PartnerMainSaveImpact.Selection moved=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",true,data,false);
        require(moved.getArchives().size()==5 && contains(moved,"BD_CUSTOMER","D") && contains(moved,"BD_CUSTOMER","P1")
            && contains(moved,"BD_CUSTOMER","P2") && contains(moved,"BD_SUPPLIER","C"),
            "Moving a partner omitted descendants, disappearing old parent, created new parent, or counterpart");
        PartnerMainSaveImpact.verifyUnchanged(moved,PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",true,data,false));
        data.archive("BD_CUSTOMER","E","G","B","D");data.structure("BD_CUSTOMER","E","D","1111222233334444","0");
        rejectChanged(moved,PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",true,data,false));
        data=new Data();data.archive("BD_CUSTOMER","D","OTHER","B","C");rejectSelect(data,"P2",true);
        data=new Data();rejectSelect(data,"D",false);
        data=new Data();data.structure("BD_CUSTOMER","C","P1","1111%222","0");rejectSelect(data,"P2",false);
        data=new Data();data.structures.remove("BD_CUSTOMER/P2");data.archive("BD_CUSTOMER","P2","G","B",null);
        PartnerMainSaveImpact.Selection before=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",false,data,false);
        data.archive("BD_CUSTOMER","P2","G","A",null);
        rejectChanged(before,PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",false,data,false));
        data=new Data();data.archive("BD_CUSTOMER","SIBLING","G","B","P1");data.structure("BD_CUSTOMER","SIBLING","P1","11119999","0");
        PartnerMainSaveImpact.Selection sibling=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",false,data,false);
        require(!contains(sibling,"BD_CUSTOMER","P1"),"Old parent with another child was incorrectly considered deleted");
        data.structures.put("BD_CUSTOMER/P2",new PartnerMainSaveImpact.Structure("P2",null,"8888","0"));
        PartnerMainSaveImpact.Selection existingParent=PartnerMainSaveImpact.select("G","BD_CUSTOMER","C","P2",false,data,false);
        require(!contains(existingParent,"BD_CUSTOMER","P2"),"Existing new-parent structure was incorrectly considered inserted");
        System.out.println("PASS PartnerMainSaveImpactTests (native linked and prefix scope; no NC or Oracle)");
    }
    private static void rejectSelect(Data data,String parent,boolean counterpart) throws Exception {
        try { PartnerMainSaveImpact.select("G","BD_CUSTOMER","C",parent,counterpart,data,false);throw new AssertionError("Unsafe partner save scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static void rejectChanged(PartnerMainSaveImpact.Selection before,PartnerMainSaveImpact.Selection after) throws Exception {
        try { PartnerMainSaveImpact.verifyUnchanged(before,after);throw new AssertionError("Changed partner save scope accepted"); }
        catch(BusinessException expected) { }
    }
    private static boolean contains(PartnerMainSaveImpact.Selection selection,String domain,String id) {
        for(PartnerMainSaveImpact.Archive item:selection.getArchives()) if(domain.equals(item.domain) && id.equals(item.id)) return true;return false;
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    static final class Data implements PartnerMainSaveImpact.Reader {
        final Map<String,PartnerMainSaveImpact.Archive> archives=new HashMap<String,PartnerMainSaveImpact.Archive>();
        final Map<String,PartnerMainSaveImpact.Structure> structures=new HashMap<String,PartnerMainSaveImpact.Structure>();
        Data() {
            archive("BD_CUSTOMER","C","G","A","P1");archive("BD_CUSTOMER","D","G","B","C");
            archive("BD_CUSTOMER","P1","G","A",null);archive("BD_CUSTOMER","P2","G","B",null);
            archive("BD_SUPPLIER","C","G","G",null);
            structure("BD_CUSTOMER","P1",null,"1111","0");structure("BD_CUSTOMER","C","P1","11112222","0");
            structure("BD_CUSTOMER","D","C","111122223333","0");
        }
        void archive(String domain,String id,String group,String owner,String parent) {
            archives.put(domain+"/"+id,new PartnerMainSaveImpact.Archive(domain,id,group,owner,id+"_CODE",parent));
        }
        void structure(String domain,String id,String parent,String code,String dr) {
            structures.put(domain+"/"+id,new PartnerMainSaveImpact.Structure(id,parent,code,dr));
        }
        public PartnerMainSaveImpact.Archive archive(String group,String domain,String id,boolean lock) { return archives.get(domain+"/"+id); }
        public PartnerMainSaveImpact.Structure structure(String domain,String id,boolean lock) { return structures.get(domain+"/"+id); }
        public List<PartnerMainSaveImpact.Structure> children(String domain,String parent,boolean lock) {
            List<PartnerMainSaveImpact.Structure> result=new ArrayList<PartnerMainSaveImpact.Structure>();
            for(Map.Entry<String,PartnerMainSaveImpact.Structure> row:structures.entrySet()) if(row.getKey().startsWith(domain+"/")
                && parent.equals(row.getValue().parent)) result.add(row.getValue());return result;
        }
        public List<PartnerMainSaveImpact.Structure> prefix(String domain,String prefix,boolean lock) {
            List<PartnerMainSaveImpact.Structure> result=new ArrayList<PartnerMainSaveImpact.Structure>();
            for(Map.Entry<String,PartnerMainSaveImpact.Structure> row:structures.entrySet()) if(row.getKey().startsWith(domain+"/")
                && row.getValue().innercode!=null && row.getValue().innercode.startsWith(prefix)) result.add(row.getValue());return result;
        }
    }
}
