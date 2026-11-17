package nc.bs.uapbd.qd.esign.security;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.itf.uap.rbac.IUserManageQuery;
import nc.vo.sm.UserVO;
import nc.vo.pub.BusinessException;
import nc.bs.uapbd.qd.esign.core.EsignValues;
/** 从 NC 当前服务端调用上下文和原生用户档案构造的可信身份，客户端传入的用户或集团字段不能替代本对象。 */
public final class ServerIdentity {
    /**
     * userId：NC 用户主键。
     * userCode：NC 用户编码。
     * userName：操作时记录的 NC 用户姓名。
     * groupId：NC 集团主键，服务端从当前身份核定。
     * dataSource：当前 NC 数据源名称。
     * clientIp：经字面校验的来源客户端 IP，不将主机名或 DNS 推测值记为 IP。
     * terminalName：原生上下文能提供的终端名称，未知时保持为空。
     */
    public final String userId,userCode,userName,groupId,dataSource,clientIp,terminalName;
    /** 从原生用户档案和 InvocationInfoProxy 保存当前可信身份，客户端 IP 仅接受明确字面值。 */
    private ServerIdentity(InvocationInfoProxy p,UserVO user){userId=p.getUserId();userCode=user.getUser_code();userName=user.getUser_name();groupId=p.getGroupId();dataSource=p.getUserDataSource();terminalName=p.getClientHost();clientIp=literalIp(terminalName);}
    /** 要求存在已认证用户、集团和数据源，再读取原生用户档案核对用户主键，构造可信服务端身份。 */
    public static ServerIdentity current() throws BusinessException {
        InvocationInfoProxy p=InvocationInfoProxy.getInstance();
        EsignValues.require(p.getUserId()!=null&&p.getGroupId()!=null&&p.getUserDataSource()!=null,"NC authenticated session is required");
        UserVO user=NCLocator.getInstance().lookup(IUserManageQuery.class).getUser(p.getUserId());
        EsignValues.require(user!=null&&p.getUserId().equals(user.getCuserid()),"NC current user profile is unavailable");
        return new ServerIdentity(p,user);
    }
    /** 仅接受合法的 IP 字面量；主机名或不确定地址保持未知，不进行 DNS 猜测。 */
    private static String literalIp(String host){
        if(host==null)return null;
        if(host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")){for(String part:host.split("\\.")){try{if(Integer.parseInt(part)>255)return null;}catch(NumberFormatException e){return null;}}return host;}
        if(host.indexOf(':')>=0&&host.matches("[0-9a-fA-F:]+")){try{return java.net.InetAddress.getByName(host).getHostAddress();}catch(java.net.UnknownHostException e){return null;}}
        return null; // 不把主机名或通过 DNS 猜测的地址记录为来源客户端 IP。
    }
}
