package nc.vo.uapbd.qd.esign;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nc.vo.pub.SuperVO;

/** Native customer or supplier main-card save and its extension editor payload. */
public final class PartnerMainSaveCommand implements Serializable {
    private static final long serialVersionUID=1L;
    private final SuperVO value;
    private final Map<String,Object> extensions;
    private final boolean skipTaxMatch;

    public PartnerMainSaveCommand(SuperVO value,Map<String,Object> extensions,boolean skipTaxMatch) {
        this.value=value;
        this.extensions=extensions==null?Collections.<String,Object>emptyMap():
            Collections.unmodifiableMap(new LinkedHashMap<String,Object>(extensions));
        this.skipTaxMatch=skipTaxMatch;
    }
    public SuperVO getValue() { return value; }
    public Map<String,Object> getExtensions() { return extensions; }
    public boolean isSkipTaxMatch() { return skipTaxMatch; }
}
