package nc.impl.uapbd.qd.esign;

import java.util.List;
import java.util.Map;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.MapListProcessor;
import nc.vo.pub.BusinessException;

/** Resolves the installed native main-card plugin without accepting client service names. */
final class ArchiveExtensionConfig {
    private ArchiveExtensionConfig() { }

    static boolean requireKnown(String node,String expected) throws BusinessException {
        SQLParameter params=new SQLParameter();
        params.addParam(node);
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)new BaseDAO().executeQuery(
            "SELECT p.paramvalue FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid"
            +" WHERE f.funcode=? AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0 AND p.paramname LIKE 'PluginBeanConfigFilePath%'",
            params,new MapListProcessor());
        boolean configured=false;
        for(Map<String,Object> row:rows) {
            String path=EsignValues.text(row,"paramvalue");
            if(path==null || path.trim().isEmpty()) continue;
            if(path.startsWith("classpath:")) path=path.substring("classpath:".length());
            EsignValues.require(path.equals(expected),"档案包含未经验证的原生扩展配置："+node);
            configured=true;
        }
        return configured;
    }
}
