package nc.test.baseapp.qd.esign.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;

import nc.impl.baseapp.qd.esign.IdentityConfirmationServiceImpl;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationRequest;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationResult;
import nc.vo.pub.BusinessException;

/** 原生认证桥接的本地契约测试，检查凭据副本、RPC 序列化及非法输入清理，不连接 NC 认证服务或数据库。 */
public final class IdentityConfirmationContractTest {
    /** assertions：已执行的离线断言计数。 */
    private static int assertions;

    /** 工具类不提供实例，使用静态方法完成本类职责。 */
    private IdentityConfirmationContractTest() { }

    /** 命令行入口，执行本文件的离线检查或原有工程示例。 */
    public static void main(String[] args) throws Exception {
        testCredentialOwnership();
        testRpcRoundTrip();
        testRejectedInputIsCleared();
        testResultHasNoImplicitSuccess();
        System.out.println("IdentityConfirmationContractTest: " + assertions
                + " assertions passed; no NC authentication or database invoked.");
    }

    /** 验证传入、内部保存及取回的密码数组互不共享，清理一方不会破坏另一方的所有权。 */
    private static void testCredentialOwnership() {
        char[] source = "local-test-credential".toCharArray();
        IdentityConfirmationRequest request = new IdentityConfirmationRequest();
        request.setPassword(source);
        Arrays.fill(source, '\0');
        char[] received = request.getPassword();
        check(received[0] == 'l', "setter retains an owned copy");
        Arrays.fill(received, '\0');
        received = request.getPassword();
        check(received[0] == 'l', "getter protects the owned copy");
        check(request.toString().indexOf("local-test-credential") < 0,
                "toString excludes credentials");
        Arrays.fill(received, '\0');
        request.clearPassword();
        check(request.getPassword() == null, "clear removes credentials");
        request.setPassword(source);
        request.setPassword(null);
        check(request.getPassword() == null, "null setter clears old credentials");
    }

    /** 验证认证 DTO 经序列化往返后仍保持字段契约和密码副本清理规则。 */
    private static void testRpcRoundTrip() throws Exception {
        IdentityConfirmationRequest request = new IdentityConfirmationRequest();
        request.setRequestId("contract-round-trip");
        char[] supplied = "local-test-credential".toCharArray();
        request.setPassword(supplied);
        Arrays.fill(supplied, '\0');
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(buffer);
        output.writeObject(request);
        output.close();
        byte[] serialized = buffer.toByteArray();
        ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized));
        IdentityConfirmationRequest copy = (IdentityConfirmationRequest) input.readObject();
        input.close();
        check("contract-round-trip".equals(copy.getRequestId()), "RPC DTO preserves request ID");
        request.clearPassword();
        char[] received = copy.getPassword();
        check(received != null && received.length == 21, "RPC DTO retains independent credentials");
        Arrays.fill(received, '\0');
        copy.clearPassword();
        Arrays.fill(serialized, (byte) 0);
    }

    /** 验证非法请求在拒绝路径同样清理内部凭据，不因早期异常留下密码。 */
    private static void testRejectedInputIsCleared() throws Exception {
        IdentityConfirmationServiceImpl service = new IdentityConfirmationServiceImpl();
        IdentityConfirmationRequest request = new IdentityConfirmationRequest();
        char[] supplied = "local-test-credential".toCharArray();
        request.setPassword(supplied);
        Arrays.fill(supplied, '\0');
        expectRejection(service, request, "QD_ESIGN_AUTH_REQUEST_ID_REQUIRED");
        check(request.getPassword() == null, "malformed request credentials are cleared");
        request.setRequestId("credential-required");
        expectRejection(service, request, "QD_ESIGN_AUTH_CREDENTIAL_REQUIRED");
        expectRejection(service, null, "QD_ESIGN_AUTH_REQUEST_ID_REQUIRED");
    }

    /** 验证未初始化或未知结果不被当作认证成功。 */
    private static void testResultHasNoImplicitSuccess() {
        IdentityConfirmationResult result = new IdentityConfirmationResult();
        check(!result.isSuccess(), "empty result is not successful");
        result.setStatus(IdentityConfirmationResult.PASSWORD_INCORRECT);
        check(!result.isSuccess(), "password failure is not successful");
        result.setStatus(IdentityConfirmationResult.ACCOUNT_LOCKED);
        check(!result.isSuccess(), "locked account is not successful");
        result.setStatus(IdentityConfirmationResult.SUCCESS);
        check(result.isSuccess(), "only explicit success is successful");
    }

    /** 检查非法认证请求必须被拒绝，并核对拒绝码及凭据清理情况。 */
    private static void expectRejection(IdentityConfirmationServiceImpl service,
            IdentityConfirmationRequest request, String code) throws Exception {
        try {
            service.confirm(request);
            throw new AssertionError("Expected rejection: " + code);
        } catch (BusinessException expected) {
            check(code.equals(expected.getMessage()), "explicit malformed-input error code");
        }
    }

    /** 累计断言并在条件不满足时抛出 AssertionError，使测试失败可被构建发现。 */
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) {
            throw new AssertionError(description);
        }
    }
}
