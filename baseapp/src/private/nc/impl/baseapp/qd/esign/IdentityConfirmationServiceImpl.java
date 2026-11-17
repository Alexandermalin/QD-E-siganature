package nc.impl.baseapp.qd.esign;

import java.util.Arrays;
import java.util.Map;

import nc.bcmanage.bs.ISuperAdminService;
import nc.bcmanage.vo.SuperAdminVO;
import nc.bs.dao.BaseDAO;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.framework.common.NCLocator;
import nc.bs.framework.exception.ComponentNotFoundException;
import nc.itf.baseapp.qd.esign.IIdentityConfirmationService;
import nc.itf.baseapp.qd.esign.ISignatureIdentityConfirmationService;
import nc.itf.uap.rbac.IUserManageQuery;
import nc.itf.uap.rbac.IUserExQueryService;
import nc.itf.uap.rbac.IUserExService;
import nc.jdbc.framework.SQLParameter;
import nc.jdbc.framework.processor.MapProcessor;
import nc.login.bs.ILoginStateInform;
import nc.login.bs.LoginVerifyBean;
import nc.login.bs.impl.ServerEnvironmentCenter;
import nc.login.bs.impl.SystemConfigParser;
import nc.login.vo.ILoginConstants;
import nc.login.vo.LoginFailureVO;
import nc.login.vo.LoginRequest;
import nc.login.vo.LoginResponse;
import nc.login.vo.SystemConfig;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationRequest;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationResult;
import nc.vo.pub.BusinessException;
import nc.vo.pub.lang.UFDate;
import nc.vo.pub.lang.UFDateTime;
import nc.vo.sm.UserExVO;
import nc.vo.sm.UserVO;
import nc.vo.uap.rbac.userpassword.PasswordSecurityLevelFinder;
import nc.vo.uap.rbac.userpassword.PasswordSecurityLevelVO;

/** 附加在 baseapp 的原生认证桥接，复用密码、锁定和失败计数 API；需注册于 MASTER，与原生登录服务的计数所在主机一致。 */
public class IdentityConfirmationServiceImpl implements IIdentityConfirmationService,
        ISignatureIdentityConfirmationService {

    /** 校验服务端登录上下文，执行账号可用性及密码检查，沿用原生失败计数和锁定阈值；所有出口清理凭据并恢复原数据源。 */
    @Override
    public IdentityConfirmationResult confirm(IdentityConfirmationRequest request)
            throws BusinessException {
        return confirmInternal(request, false, false);
    }

    /** 签名认证的锁定阈值由签名服务按当前请求计算，不使用登录失败计数。 */
    @Override
    public IdentityConfirmationResult confirm(IdentityConfirmationRequest request, boolean lockOnFailure)
            throws BusinessException {
        return confirmInternal(request, true, lockOnFailure);
    }

    private IdentityConfirmationResult confirmInternal(IdentityConfirmationRequest request,
            boolean signature, boolean lockOnFailure) throws BusinessException {
        char[] password = null;
        LoginRequest nativeRequest = null;
        InvocationInfoProxy invocation = null;
        String originalDataSource = null;
        try {
            validateRequest(request);
            password = request.getPassword();
            if (password == null) {
                throw new BusinessException("QD_ESIGN_AUTH_CREDENTIAL_REQUIRED");
            }

            // 认证对象只取当前服务端登录用户，不能由请求指定另一个账号。
            invocation = InvocationInfoProxy.getInstance();
            originalDataSource = invocation.getUserDataSource();
            String currentUserId = invocation.getUserId();
            if (isBlank(currentUserId) || isBlank(originalDataSource)) {
                throw new BusinessException("QD_ESIGN_AUTH_LOGIN_CONTEXT_REQUIRED");
            }
            IUserManageQuery users = NCLocator.getInstance().lookup(IUserManageQuery.class);
            UserVO user = users.getUser(currentUserId);
            if (user == null || !currentUserId.equals(user.getCuserid())
                    || user.getUser_type() == null) {
                throw new BusinessException("QD_ESIGN_AUTH_CURRENT_USER_UNAVAILABLE");
            }
            String nativeUserCode = user.getUser_code_q();
            if (isBlank(nativeUserCode)) {
                nativeUserCode = user.getUser_code();
            }
            if (isBlank(nativeUserCode)) {
                throw new BusinessException("QD_ESIGN_AUTH_CURRENT_USER_CODE_REQUIRED");
            }
            String sysId = String.valueOf(invocation.getSysid());
            LoginVerifyBean verifier = new LoginVerifyBean(sysId);
            verifier.setStaticPWDVerify(true);
            IdentityConfirmationResult result = newResult(request, user, invocation);

            // 先沿用原生账号可用性与自动锁定检查，再执行密码比对，不另造账号状态规则。
            int availability = verifier.checkUser(user, new UFDate());
            if (availability != ILoginConstants.USER_IDENTITY_LEGAL) {
                return unavailable(result, availability);
            }
            if (user.getUser_type().intValue() != 3
                    && verifier.identifyUserIsAutoLock(user) == ILoginConstants.USER_LOCKED) {
                return unavailable(result, ILoginConstants.USER_LOCKED);
            }

            // 由服务端内部构造原生登录请求，不接收客户端 SSO 标志或 LoginToken。
            nativeRequest = new LoginRequest();
            nativeRequest.setUserCode(nativeUserCode);
            nativeRequest.setBusiCenterCode(invocation.getBizCenterCode());
            nativeRequest.setLangCode(invocation.getLangCode());
            // 原生 API 要求 String 密码，此处仅用于本次调用，不额外保存或记录日志。
            nativeRequest.setUserPWD(new String(password));
            int outcome = verifier.identityVerify(nativeRequest, user, new LoginResponse());
            result.setPlatformResult(outcome);
            if (signature) {
                if (outcome == ILoginConstants.USER_IDENTITY_LEGAL) {
                    result.setStatus(IdentityConfirmationResult.SUCCESS);
                    result.setConfirmedAt(System.currentTimeMillis());
                } else if (outcome == ILoginConstants.USER_NAME_RIGHT_PWD_WRONG) {
                    if (lockOnFailure) {
                        lockSignatureUser(user, verifier);
                        result.setLocked(true);
                        result.setStatus(IdentityConfirmationResult.ACCOUNT_LOCKED);
                    } else {
                        result.setStatus(IdentityConfirmationResult.PASSWORD_INCORRECT);
                    }
                } else {
                    throw new BusinessException("QD_ESIGN_AUTH_UNEXPECTED_PLATFORM_RESULT");
                }
                return result;
            }

            ServerEnvironmentCenter counters = ServerEnvironmentCenter.getInstance();
            // 密码正确时清除原生失败计数并记录服务端确认时间；本次确认不创建新会话。
            if (outcome == ILoginConstants.USER_IDENTITY_LEGAL) {
                counters.clearAllFailure(sysId, originalDataSource, nativeUserCode);
                result.setStatus(IdentityConfirmationResult.SUCCESS);
                result.setFailureCount(0);
                result.setConfirmedAt(System.currentTimeMillis());
                return result;
            }
            if (outcome != ILoginConstants.USER_NAME_RIGHT_PWD_WRONG) {
                throw new BusinessException("QD_ESIGN_AUTH_UNEXPECTED_PLATFORM_RESULT");
            }

            // 密码错误沿用原生计数宿主登记失败，使再确认与 NC 登录锁定策略一致。
            LoginFailureVO failure = new LoginFailureVO();
            failure.setFailureType(outcome);
            failure.setClientHost(invocation.getClientHost());
            failure.setClientIP(invocation.getClientHost());
            failure.setBcCode(invocation.getBizCenterCode());
            failure.setUserID(currentUserId);
            failure.setUserCode(nativeUserCode);
            failure.setUserName(user.getUser_name());
            failure.setUserType(user.getUser_type().intValue());
            failure.setTime(System.currentTimeMillis());
            counters.registerFailure(sysId, originalDataSource, nativeUserCode, failure);
            int failureCount = counters.getFailureCount(sysId, originalDataSource, nativeUserCode);
            result.setFailureCount(failureCount);
            result.setStatus(IdentityConfirmationResult.PASSWORD_INCORRECT);

            // 与 LoginEngine.dealWithLoginFailure 保持一致：失败次数严格大于阈值才锁定，不能改成大于等于。
            if (failureCount > getNativeMaxFailure(user)) {
                verifier.lockUser(user);
                counters.clearAllFailure(sysId, originalDataSource, nativeUserCode);
                result.setLocked(true);
                result.setStatus(IdentityConfirmationResult.ACCOUNT_LOCKED);
            }
            return result;
        // 所有返回和异常出口都清零数组、移除原生请求密码，并恢复可能切换的数据源。
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (request != null) {
                request.clearPassword();
            }
            if (nativeRequest != null) {
                nativeRequest.setUserPWD("");
            }
            // 原生超级管理员检查可能切换调用数据源，退出时必须恢复进入前的数据源。
            if (invocation != null) {
                invocation.setUserDataSource(originalDataSource);
            }
        }
    }

    /** 使用 NC 原生锁定记录并验证写入，避免原生工具吞掉写入异常后误报成功。 */
    private void lockSignatureUser(UserVO user, LoginVerifyBean verifier) throws BusinessException {
        if (user.getUser_type().intValue() == 3) {
            verifier.lockUser(user);
            SuperAdminVO admin = NCLocator.getInstance().lookup(ISuperAdminService.class)
                    .getSuperAdminByCode(user.getUser_code());
            if (admin == null || !admin.getIsLocked()) {
                throw new BusinessException("账号锁定结果无法确认，请联系管理员。");
            }
            return;
        }
        // 不同签名请求可能同时锁定同一账号，先串行化并检查已有锁定记录。
        BaseDAO dao = new BaseDAO();
        SQLParameter parameters = new SQLParameter();
        parameters.addParam(user.getCuserid());
        @SuppressWarnings("unchecked")
        Map<String,Object> account = (Map<String,Object>) dao.executeQuery(
                "SELECT cuserid FROM sm_user WHERE cuserid=? FOR UPDATE", parameters, new MapProcessor());
        if (account == null) {
            throw new BusinessException("账号锁定结果无法确认，请联系管理员。");
        }
        if (NCLocator.getInstance().lookup(IUserExQueryService.class).isUserLocked(user.getCuserid())) {
            return;
        }
        UserExVO lock = new UserExVO();
        lock.setExstatuscode("2");
        lock.setExtreason("islocked");
        lock.setUser_id(user.getCuserid());
        lock.setExtime(new UFDateTime().toStdString());
        String lockId = NCLocator.getInstance().lookup(IUserExService.class).addUserEx(lock);
        if (isBlank(lockId)) {
            throw new BusinessException("账号锁定结果无法确认，请联系管理员。");
        }
        UserExVO saved = (UserExVO) dao.retrieveByPK(UserExVO.class, lockId);
        if (saved == null || !user.getCuserid().equals(saved.getUser_id())
                || !"2".equals(saved.getExstatuscode()) || !"islocked".equals(saved.getExtreason())) {
            throw new BusinessException("账号锁定结果无法确认，请联系管理员。");
        }
        try {
            NCLocator.getInstance().lookup(ILoginStateInform.class).lockPortalUser(user);
        } catch (ComponentNotFoundException unavailable) {
            // NC 原生锁定流程允许未安装门户组件。
        }
    }

    /** 优先读取用户密码安全等级的失败阈值，缺少时使用原生系统配置；无法取得配置时拒绝继续认证。 */
    private int getNativeMaxFailure(UserVO user) throws BusinessException {
        if (user.getPwdlevelcode() != null) {
            PasswordSecurityLevelVO level = PasswordSecurityLevelFinder.getPWDLV(user);
            if (level != null && level.getErrorloginThreshold() != null) {
                return level.getErrorloginThreshold().intValue();
            }
        }
        SystemConfig config = SystemConfigParser.getSystemConfig();
        if (config == null) {
            throw new BusinessException("QD_ESIGN_AUTH_NATIVE_SECURITY_CONFIG_UNAVAILABLE");
        }
        return config.getMaxLoginFailure();
    }

    /** 以服务端用户和调用上下文填充结果身份信息，不从客户端载荷采信用户身份。 */
    private IdentityConfirmationResult newResult(IdentityConfirmationRequest request,
            UserVO user, InvocationInfoProxy invocation) {
        IdentityConfirmationResult result = new IdentityConfirmationResult();
        result.setRequestId(request.getRequestId());
        result.setUserId(user.getCuserid());
        result.setUserCode(user.getUser_code());
        result.setUserName(user.getUser_name());
        result.setGroupId(invocation.getGroupId());
        return result;
    }

    /** 将原生账号不可用或锁定结果映射为明确认证状态，保留原生结果码。 */
    private IdentityConfirmationResult unavailable(IdentityConfirmationResult result, int code) {
        result.setPlatformResult(code);
        result.setLocked(code == ILoginConstants.USER_LOCKED);
        result.setStatus(result.isLocked() ? IdentityConfirmationResult.ACCOUNT_LOCKED
                : IdentityConfirmationResult.ACCOUNT_UNAVAILABLE);
        return result;
    }

    /** 检查认证请求及关联标识是否完整且在长度限制内，非法请求不进入原生密码校验。 */
    private void validateRequest(IdentityConfirmationRequest request) throws BusinessException {
        if (request == null || isBlank(request.getRequestId())
                || request.getRequestId().length() > 64) {
            throw new BusinessException("QD_ESIGN_AUTH_REQUEST_ID_REQUIRED");
        }
    }

    /** 判断文本是否缺少或去除首尾空白后为空，用于拒绝不完整请求。 */
    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
