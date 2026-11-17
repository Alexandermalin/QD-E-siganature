package nc.impl.uapbd.qd.esign;

/** Only the physical child identities captured by the delete adapter may pass the metadata guard. */
public final class MaterialMainDeleteRouteGuardTests {
    private MaterialMainDeleteRouteGuardTests() { }
    public static void run() {
        require(MaterialMainDeleteRouteGuard.knownChild("bd_material","bd_materialconvert","pk_materialconvert"),"Measure child rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_material","bd_materialtaxtype","pk_materialtaxtype"),"Tax child rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_material","bd_materialcost","pk_materialcost"),"Root cost page rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_material","bd_materialsale","pk_materialsale"),"Root sales page rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_material","bd_materialpfcc","pk_mateprofcost"),"Root profit-cost page rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_materialsale","bd_materialbindle","pk_materialbindle"),"Sales child rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_materialstock","bd_materialwarh","pk_materialwarh"),"Stock child rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_materialplan","bd_materialrepl","pk_materialrepl"),"Plan child rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_materialcost","bd_materialcostmod","pk_materialcostmod"),"Cost child rejected");
        require(MaterialMainDeleteRouteGuard.knownChild("bd_materialpfcc","bd_materialpfccsub","pk_mateprofcost_b"),"Profit-center child rejected");
        require(!MaterialMainDeleteRouteGuard.knownChild("bd_materialfi","bd_materialbindle","pk_materialbindle"),"Foreign child accepted");
        require(!MaterialMainDeleteRouteGuard.knownChild("bd_materialsale","bd_materialbindle","pk_other"),"Foreign key accepted");
        require(!MaterialMainDeleteRouteGuard.knownChild("bd_materialsale","bd_custom","pk_custom"),"Unknown custom child accepted");
        System.out.println("PASS MaterialMainDeleteRouteGuardTests (known parent-child physical identities; no NC metadata)");
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
