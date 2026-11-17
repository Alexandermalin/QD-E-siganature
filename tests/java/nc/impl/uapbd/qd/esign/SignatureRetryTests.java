package nc.impl.uapbd.qd.esign;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nc.bs.framework.common.InvocationInfoProxy;
import nc.bs.uapbd.qd.esign.persistence.QdEsignStore;
import nc.bs.uapbd.qd.esign.security.ServerIdentity;
import nc.itf.baseapp.qd.esign.ISignatureIdentityConfirmationService;
import nc.jdbc.framework.SQLParameter;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationRequest;
import nc.vo.baseapp.qd.esign.auth.IdentityConfirmationResult;
import nc.vo.pub.BusinessException;
import nc.vo.sm.UserVO;
import nc.vo.uapbd.qd.esign.SignatureResult;

/** 使用仓储和认证替身验证签名重试状态及逐对象日志。 */
public final class SignatureRetryTests {
    private static int assertions;
    private SignatureRetryTests() { }

    public static void run() throws Exception {
        testSuccessAfterFourFailures();
        testFifthFailureLocks();
        testSeparateRequestsAndAuthenticationFailure();
        testAlreadyLockedAndIdentityMismatch();
        testResultSerialization();
        System.out.println("PASS SignatureRetryTests assertions=" + assertions + " (offline service boundaries)");
    }

    private static void testAlreadyLockedAndIdentityMismatch() throws Exception {
        Store store = new Store();
        ServerIdentity identity = identity();
        Authentication auth = new Authentication();
        auth.prelocked = true;
        Map<String,Object> request = store.request("already-locked");
        SignatureResult result = confirm(store, request, identity, auth, false);
        check(result.isLocked() && "FAILED".equals(request.get("business_state")),
                "an already locked account cannot execute");
        check(store.countSignaturePasswordFailures("already-locked") == 0,
                "an existing account lock does not count as a wrong password");

        auth.prelocked = false; auth.wrongUser = true;
        Map<String,Object> mismatch = store.request("identity-mismatch");
        try {
            confirm(store, mismatch, identity, auth, false);
            throw new AssertionError("Mismatched authentication identity accepted");
        } catch (BusinessException expected) {
            check("PENDING".equals(mismatch.get("business_state"))
                    && store.countSignaturePasswordFailures("identity-mismatch") == 0,
                    "another user's result cannot authorize or count against this request");
        }
    }

    private static void testSuccessAfterFourFailures() throws Exception {
        Store store = new Store();
        Map<String,Object> request = store.request("first");
        Authentication auth = new Authentication();
        ServerIdentity identity = identity();
        for (int attempt = 1; attempt <= 4; attempt++) {
            SignatureResult result = confirm(store, request, identity, auth, false);
            check("PASSWORD_INCORRECT".equals(result.getStatus()), "wrong password returned");
            check("PENDING".equals(request.get("signature_state")) && "PENDING".equals(request.get("business_state")),
                    "wrong password leaves request pending");
            check(result.getRemainingAttempts().intValue() == 5 - attempt, "remaining attempts");
            check(store.countSignaturePasswordFailures("first") == attempt, "two object rows count once");
            check(!auth.lockRequested, "first four failures do not request lock");
            check(auth.lastRequest.getPassword() == null, "authentication request cleared");
        }
        SignatureResult success = confirm(store, request, identity, auth, true);
        check(auth.lockRequested, "fifth attempt requests locking only on failure");
        check(success.isReadyForExecution() && "READY".equals(request.get("business_state")),
                "correct fifth attempt permits execution");
        check(!auth.locked && store.logs.size() == 10, "success does not lock and all attempts are logged");
    }

    private static void testFifthFailureLocks() throws Exception {
        Store store = new Store();
        Map<String,Object> request = store.request("locked");
        Authentication auth = new Authentication();
        ServerIdentity identity = identity();
        for (int attempt = 1; attempt <= 4; attempt++) { confirm(store, request, identity, auth, false); }
        SignatureResult result = confirm(store, request, identity, auth, false);
        check(auth.locked && result.isLocked(), "fifth wrong password locks current user");
        check("ACCOUNT_LOCKED".equals(request.get("signature_state")) && "FAILED".equals(request.get("business_state")),
                "locked request cannot execute");
        check(store.logs.size() == 10 && "Y".equals(store.logs.get(9).get("lock_result")),
                "lock result recorded for each signed object");
    }

    private static void testSeparateRequestsAndAuthenticationFailure() throws Exception {
        Store store = new Store();
        ServerIdentity identity = identity();
        Authentication auth = new Authentication();
        Map<String,Object> first = store.request("cancelled");
        for (int attempt = 0; attempt < 4; attempt++) { confirm(store, first, identity, auth, false); }
        Map<String,Object> reopened = store.request("reopened");
        SignatureResult fresh = confirm(store, reopened, identity, auth, false);
        check(fresh.getRemainingAttempts().intValue() == 4 && !auth.locked, "new request starts a new count");
        for (int attempt = 0; attempt < 3; attempt++) { confirm(store, reopened, identity, auth, false); }
        auth.failLock = true;
        try {
            confirm(store, reopened, identity, auth, false);
            throw new AssertionError("Lock failure accepted");
        } catch (BusinessException expected) {
            check(!auth.locked && store.countSignaturePasswordFailures("reopened") == 4,
                    "lock exception cannot be reported as locked or logged as successful");
            check("PENDING".equals(reopened.get("business_state")), "failed call does not authorize business");
            check(auth.lastRequest.getPassword() == null, "credentials cleared after exception");
        }
    }

    private static void testResultSerialization() throws Exception {
        SignatureResult oldConstructor = new SignatureResult("old", "SUCCESS", false, "READY");
        check(oldConstructor.getRemainingAttempts() == null, "legacy constructor has unknown count");
        SignatureResult result = new SignatureResult("new", "PASSWORD_INCORRECT", false, "PENDING", Integer.valueOf(3));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bytes);
        out.writeObject(result); out.close();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        SignatureResult copy = (SignatureResult) in.readObject(); in.close();
        check(copy.getRemainingAttempts().intValue() == 3 && "PENDING".equals(copy.getBusinessState()),
                "remaining count survives serialization");
    }

    private static SignatureResult confirm(Store store, Map<String,Object> request, ServerIdentity identity,
            Authentication auth, boolean correct) throws BusinessException {
        auth.correct = correct;
        return SignatureServiceImpl.confirmPassword(store, request, identity, auth, new char[] {'x'}, "reason");
    }

    private static ServerIdentity identity() throws Exception {
        InvocationInfoProxy invocation = InvocationInfoProxy.getInstance();
        String previousUser = invocation.getUserId(), previousGroup = invocation.getGroupId();
        try {
            invocation.setUserId("user"); invocation.setGroupId("group");
            Constructor<ServerIdentity> constructor = ServerIdentity.class.getDeclaredConstructor(InvocationInfoProxy.class, UserVO.class);
            constructor.setAccessible(true);
            return constructor.newInstance(invocation, new UserVO() {
                private static final long serialVersionUID = 1L;
                public String getUser_code() { return "tester"; }
                public String getUser_name() { return "Tester"; }
            });
        } finally {
            invocation.setUserId(previousUser); invocation.setGroupId(previousGroup);
        }
    }

    private static final class Authentication implements ISignatureIdentityConfirmationService {
        boolean correct, locked, lockRequested, failLock, prelocked, wrongUser;
        IdentityConfirmationRequest lastRequest;
        public IdentityConfirmationResult confirm(IdentityConfirmationRequest request, boolean lockOnFailure) throws BusinessException {
            lastRequest = request; lockRequested = lockOnFailure;
            if (failLock && lockOnFailure && !correct) { throw new BusinessException("lock failed"); }
            IdentityConfirmationResult result = new IdentityConfirmationResult();
            result.setRequestId(request.getRequestId()); result.setUserId(wrongUser ? "another" : "user"); result.setGroupId("group");
            result.setUserName("Tester");
            if (prelocked) { result.setLocked(true); result.setStatus(IdentityConfirmationResult.ACCOUNT_LOCKED); }
            else if (correct) { result.setStatus(IdentityConfirmationResult.SUCCESS); }
            else if (lockOnFailure) { locked = true; result.setLocked(true); result.setStatus(IdentityConfirmationResult.ACCOUNT_LOCKED); }
            else { result.setStatus(IdentityConfirmationResult.PASSWORD_INCORRECT); }
            return result;
        }
    }

    private static final class Store extends QdEsignStore {
        final Map<String,Map<String,Object>> requests = new LinkedHashMap<String,Map<String,Object>>();
        final List<Map<String,Object>> logs = new ArrayList<Map<String,Object>>();

        Map<String,Object> request(String id) {
            Map<String,Object> row = row("request_id", id, "batch_id", id, "signature_state", "PENDING",
                    "business_state", "PENDING", "policy_mode", "LEGACY");
            requests.put(id, row); return row;
        }
        public Map<String,Object> one(String sql, SQLParameter parameters) {
            if (!sql.contains("COUNT(DISTINCT attempt_id)") || !sql.contains("result_code='PASSWORD_INCORRECT'")) {
                throw new AssertionError("Unexpected query: " + sql);
            }
            String id = String.valueOf(parameters.get(0));
            Set<Object> attempts = new HashSet<Object>();
            for (Map<String,Object> log : logs) {
                if (id.equals(log.get("request_id")) && "PASSWORD_INCORRECT".equals(log.get("result_code"))) {
                    attempts.add(log.get("attempt_id"));
                }
            }
            return row("failure_count", Integer.valueOf(attempts.size()));
        }
        public List<Map<String,Object>> loadObjects(String id) {
            return Arrays.asList(row("object_id", "one"), row("object_id", "two"));
        }
        public void insert(String table, Map<String,Object> values) {
            if (!"qd_es_signlog".equals(table)) { throw new AssertionError("Unexpected insert"); }
            logs.add(new LinkedHashMap<String,Object>(values));
        }
        public void updateRequest(String id, Map<String,Object> changes) { requests.get(id).putAll(changes); }
    }

    private static Map<String,Object> row(Object... values) {
        Map<String,Object> row = new LinkedHashMap<String,Object>();
        for (int index = 0; index < values.length; index += 2) { row.put((String) values[index], values[index + 1]); }
        return row;
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }
}
