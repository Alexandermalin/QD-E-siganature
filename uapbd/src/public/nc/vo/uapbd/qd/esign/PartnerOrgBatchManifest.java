package nc.vo.uapbd.qd.esign;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Detects incomplete partitioning or transport of a server-enumerated selection; not a signature. */
public final class PartnerOrgBatchManifest {
    private static final Charset UTF8=Charset.forName("UTF-8");
    private PartnerOrgBatchManifest() { }

    public static String fingerprint(String group,String org,String node,String tab,String selection,
            List<String> roots,List<String> targets,List<PartnerOrgBatchPlan.Page> pages) {
        if(group==null || org==null || node==null || tab==null || selection==null || roots==null || targets==null || pages==null)
            throw new IllegalArgumentException("Organization batch manifest is incomplete");
        List<String> orderedRoots=new ArrayList<String>(roots),orderedTargets=new ArrayList<String>(targets);
        for(String value:orderedRoots)if(value==null)throw new IllegalArgumentException("Missing manifest root");
        for(String value:orderedTargets)if(value==null)throw new IllegalArgumentException("Missing manifest target");
        Collections.sort(orderedRoots);Collections.sort(orderedTargets);
        List<PartnerOrgBatchPlan.Page> orderedPages=new ArrayList<PartnerOrgBatchPlan.Page>(pages);
        for(PartnerOrgBatchPlan.Page page:orderedPages)if(page==null || page.getRoot()==null || page.getTarget()==null || page.getId()==null)
            throw new IllegalArgumentException("Missing manifest page identity");
        Collections.sort(orderedPages,new Comparator<PartnerOrgBatchPlan.Page>() {
            public int compare(PartnerOrgBatchPlan.Page left,PartnerOrgBatchPlan.Page right) {
                int value=left.getRoot().compareTo(right.getRoot());
                if(value==0)value=left.getTarget().compareTo(right.getTarget());
                return value==0?left.getId().compareTo(right.getId()):value;
            }
        });
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
        part(digest,"org-batch-manifest-v1");part(digest,group);part(digest,org);part(digest,node);part(digest,tab);part(digest,selection);
        part(digest,Integer.toString(orderedRoots.size()));for(String value:orderedRoots)part(digest,value);
        part(digest,Integer.toString(orderedTargets.size()));for(String value:orderedTargets)part(digest,value);
        part(digest,Integer.toString(orderedPages.size()));for(PartnerOrgBatchPlan.Page page:orderedPages) {
            part(digest,page.getRoot());part(digest,page.getTarget());part(digest,page.getId());
        }
        byte[] hash=digest.digest();StringBuilder value=new StringBuilder(hash.length*2);
        for(byte item:hash) { int n=item&0xff;value.append(Character.forDigit(n>>>4,16)).append(Character.forDigit(n&15,16)); }
        return value.toString();
    }
    private static void part(MessageDigest digest,String value) {
        byte[] bytes=value.getBytes(UTF8);digest.update(Integer.toString(bytes.length).getBytes(UTF8));
        digest.update((byte)':');digest.update(bytes);
    }
}
