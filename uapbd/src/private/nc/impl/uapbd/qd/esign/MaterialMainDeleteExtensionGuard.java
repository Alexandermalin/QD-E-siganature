package nc.impl.uapbd.qd.esign;

import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import nc.bs.dao.BaseDAO;
import nc.bs.uapbd.qd.esign.core.EsignValues;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.MapListProcessor;
import nc.vo.pub.BusinessException;

/** Fail closed unless the server can verify the exact, extension-free client configuration. */
final class MaterialMainDeleteExtensionGuard {
    private static final String PREFIX="nc/ui/uapbd/qd/esign/";
    private MaterialMainDeleteExtensionGuard() { }

    static void requireSafe(String node) throws BusinessException {
        String candidate=expectedCandidate(node);
        SQLParameter parameters=new SQLParameter();parameters.addParam(node);
        BaseDAO dao=new BaseDAO();
        // Prevent changes to registered paths/plugins for the duration of the delete transaction.
        dao.executeUpdate("LOCK TABLE sm_paramregister IN SHARE MODE NOWAIT");
        @SuppressWarnings("unchecked") List<Map<String,Object>> rows=(List<Map<String,Object>>)dao.executeQuery(
            "SELECT p.paramname,p.paramvalue FROM sm_funcregister f JOIN sm_paramregister p ON p.parentid=f.cfunid"
            + " WHERE f.funcode=? AND NVL(f.dr,0)=0 AND NVL(p.dr,0)=0"
            + " AND (p.paramname='BeanConfigFilePath' OR p.paramname LIKE 'PluginBeanConfigFilePath%')",
            parameters,new MapListProcessor());
        requireParameters(candidate,rows);
        ClassLoader loader=Thread.currentThread().getContextClassLoader();
        EsignValues.require(loader!=null,"无法核验物料删除的客户端资源加载器。");
        verifyResources(node,loader);
    }

    static void verifyResources(String node,ClassLoader loader) throws BusinessException {
        expectedCandidate(node);
        EsignValues.require(loader!=null,"无法核验物料删除的客户端资源加载器。");
        verify(loader,PREFIX+("10140MAG".equals(node)?"QdMaterialGroupDeleteCandidateConfig.xml":"QdMaterialOrgDeleteCandidateConfig.xml"),
            "10140MAG".equals(node)?"b7e4eadf9396067e6e17cc4ee3de4ad9d83a1061d0ad92597bf32ae402adf073":"d2b7864688d49e643b96e14b59b2d95cb8a2f589f59ea2e0c05c9e6d5d636c73");
        verify(loader,PREFIX+("10140MAG".equals(node)?"QdMaterialGroupConfig.xml":"QdMaterialOrgConfig.xml"),
            "10140MAG".equals(node)?"dfcc2fc23e64513ef0eb93e0c715abf4a14df42c974e2adeb2d4a0e4a75081ca":"a98179943fb9c17a1812617dbce1bf760090aa95ea5c8f87485df788d776525f");
        verify(loader,PREFIX+"QdMaterialDeleteActionsCandidate.xml","43db01e58048ab1509d7618acfd740324cf0521673b9379376bf73cc9b70bb99");
        verify(loader,PREFIX+"QdMaterialFastAssignConfig.xml","1d55839b1c805b3935c95aa475fbb48ac6f03a0ffdd872c1039ecd313250dc49");
        verify(loader,"nc/ui/bd/material/config/Material_Config_"+("10140MAG".equals(node)?"Group":"Org")+".xml",
            "10140MAG".equals(node)?"43f3d3512151d1034dc6f1de3cf246f6473f5d7ece1f436d810a93bba125513a":"75fc3f692658a89c34f43e20283c0c3650810d36baeed71990285b08ce07436b");
        // This verified native XML declares an empty baseExtendContext.
        verify(loader,"nc/ui/bd/material/config/Material_Config_Base.xml","0943a9290af3b91a508c9ba51c5653b7882defe25eeead109038a992e86641a0");
    }

    static String expectedCandidate(String node) throws BusinessException {
        EsignValues.require("10140MAG".equals(node) || "10140MAO".equals(node),"物料删除节点不正确。");
        return PREFIX+("10140MAG".equals(node)?"QdMaterialGroupDeleteCandidateConfig.xml":"QdMaterialOrgDeleteCandidateConfig.xml");
    }

    static void requireParameters(String expected,List<Map<String,Object>> rows) throws BusinessException {
        int found=0;
        EsignValues.require(rows!=null,"无法核验物料客户端扩展配置。");
        for(Map<String,Object> row:rows) {
            String name=EsignValues.text(row,"paramname"),value=EsignValues.text(row,"paramvalue");
            if("BeanConfigFilePath".equals(name)) {
                found++;
                if(value!=null && value.startsWith("classpath:")) value=value.substring("classpath:".length());
                EsignValues.require(expected.equals(value),"物料节点的客户端配置未通过扩展对象核验。");
            } else EsignValues.require(value==null || value.trim().isEmpty(),"物料节点包含未经核验的客户端插件配置。");
        }
        EsignValues.require(found==1,"物料节点客户端配置缺失或存在多个版本。");
    }

    private static void verify(ClassLoader loader,String path,String expected) throws BusinessException {
        try {
            Enumeration<URL> urls=loader.getResources(path);int count=0;
            while(urls.hasMoreElements()) {
                URL url=urls.nextElement();MessageDigest digest=MessageDigest.getInstance("SHA-256");
                InputStream input=url.openStream();
                try { byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1) digest.update(bytes,0,n); }
                finally { input.close(); }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest()) hash.append(String.format(java.util.Locale.ENGLISH,"%02x",b&255));
                EsignValues.require(expected.equals(hash.toString()),"物料客户端配置已变化，须重新核验扩展对象："+path);
                count++;
            }
            EsignValues.require(count>0,"无法读取物料客户端配置以核验扩展对象："+path);
        } catch(BusinessException failure) { throw failure; }
        catch(Exception failure) { throw new BusinessException("物料客户端扩展配置核验失败："+path,failure); }
    }
}
