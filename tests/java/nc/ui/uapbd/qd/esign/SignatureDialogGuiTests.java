package nc.ui.uapbd.qd.esign;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import nc.itf.uapbd.qd.esign.ISignatureService;
import nc.ui.pub.beans.UIButton;
import nc.ui.pub.beans.UILabel;
import nc.ui.pub.beans.UIPasswordField;
import nc.vo.pub.BusinessException;
import nc.vo.uapbd.qd.esign.BusinessObjectRef;
import nc.vo.uapbd.qd.esign.OperationContext;
import nc.vo.uapbd.qd.esign.PolicyDecision;
import nc.vo.uapbd.qd.esign.SignaturePreparation;
import nc.vo.uapbd.qd.esign.SignatureResult;

/** 在未显示的 NC 窗口中验证实际 SwingWorker、按钮和重试行为。 */
public final class SignatureDialogGuiTests {
    private static final String MIXED_PASSWORD = "Az09!@#$%^&\\*?";
    private static int assertions;
    private SignatureDialogGuiTests() { }

    public static void main(String[] args) {
        try {
            testFourFailuresThenSuccess();
            testFifthFailureAndCancellation();
            System.out.println("PASS SignatureDialogGuiTests assertions=" + assertions + " (local UI and service double)");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void testFourFailuresThenSuccess() throws Exception {
        final Fixture fixture = create(true);
        submit(fixture, "", 0);
        check(fixture.service.attempts == 0, "empty password does not invoke authentication");
        for (int attempt = 1; attempt <= 4; attempt++) {
            submit(fixture, "incorrect", attempt);
            check(!finished(fixture) && button(fixture).isEnabled(), "wrong password retains enabled dialog");
            check(label(fixture).getText().contains(String.valueOf(5 - attempt)), "remaining count displayed");
            check(password(fixture).getPassword().length == 0, "password field cleared after failure");
        }
        submit(fixture, MIXED_PASSWORD, 5);
        check(finished(fixture) && fixture.dialog.getOutcome().isConfirmed(), "mixed password completes signature on the fifth attempt");
        check(fixture.service.cancels == 0, "successful signature is not cancelled");
        dispose(fixture);
    }

    private static void testFifthFailureAndCancellation() throws Exception {
        final Fixture fixture = create(false);
        for (int attempt = 1; attempt <= 5; attempt++) { submit(fixture, "incorrect", attempt); }
        check(finished(fixture) && !fixture.dialog.getOutcome().isConfirmed(), "fifth error ends signature");
        check(fixture.dialog.getOutcome().getFailure() != null && fixture.service.cancels == 1,
                "lock is reported and cancellation is attempted once");
        dispose(fixture);
        check(fixture.service.cancels == 1, "dispose does not repeat cancellation");

        final Fixture cancelled = create(false);
        submit(cancelled, "incorrect", 1);
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { cancelled.dialog.closeCancel(); } });
        check(cancelled.service.cancels == 1, "user cancellation ends pending request");
        dispose(cancelled);
    }

    private static Fixture create(final boolean correctFifth) throws Exception {
        final Fixture fixture = new Fixture();
        fixture.service = new FakeService(correctFifth);
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                OperationContext context = new OperationContext("test-request", "test-batch", "group", "org", "node", "Node",
                        "SAVE", "Save", "SAVE", null, Arrays.asList(new BusinessObjectRef("object", "TEST", null, null, null)));
                PolicyDecision policy = new PolicyDecision(true, true, false, false, true, 1L);
                SignaturePreparation preparation = new SignaturePreparation(context, policy, "PENDING");
                fixture.dialog = new ElectronicSignatureDialog(null, "tester", preparation, fixture.service,
                        new RequestCancellation(fixture.service, "test-request"));
            }
        });
        return fixture;
    }

    private static void submit(final Fixture fixture, final String value, int expectedAttempts) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    UIPasswordField field = password(fixture);
                    field.setText(value);
                    check(Arrays.equals(value.toCharArray(), field.getPassword()), "password field preserves input");
                    button(fixture).doClick();
                }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        });
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline) {
            final AtomicBoolean busy = new AtomicBoolean();
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {
                    try { busy.set(field("busy").getBoolean(fixture.dialog)); }
                    catch (Exception error) { throw new RuntimeException(error); }
                }
            });
            if (!busy.get() && fixture.service.attempts == expectedAttempts) { return; }
            Thread.sleep(20L);
        }
        throw new AssertionError("Signature worker did not settle");
    }

    private static boolean finished(final Fixture fixture) throws Exception {
        final AtomicBoolean value = new AtomicBoolean();
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try { value.set(field("finished").getBoolean(fixture.dialog)); }
                catch (Exception error) { throw new RuntimeException(error); }
            }
        });
        return value.get();
    }
    private static void dispose(final Fixture fixture) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { fixture.dialog.dispose(); } });
    }
    private static Field field(String name) throws Exception {
        Field field = ElectronicSignatureDialog.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static UIButton button(Fixture fixture) throws Exception { return (UIButton) field("confirm").get(fixture.dialog); }
    private static UIPasswordField password(Fixture fixture) throws Exception { return (UIPasswordField) field("password").get(fixture.dialog); }
    private static UILabel label(Fixture fixture) throws Exception { return (UILabel) field("validation").get(fixture.dialog); }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class Fixture { FakeService service; ElectronicSignatureDialog dialog; }
    private static final class FakeService implements ISignatureService {
        final boolean correctFifth;
        volatile int attempts, cancels;
        FakeService(boolean correctFifth) { this.correctFifth = correctFifth; }
        public SignaturePreparation prepare(OperationContext context) throws BusinessException { throw new BusinessException("unexpected prepare"); }
        public SignatureResult confirm(String id, char[] password, String reason) {
            attempts++;
            if (attempts < 5) { return new SignatureResult(id, "PASSWORD_INCORRECT", false, "PENDING", Integer.valueOf(5 - attempts)); }
            if (correctFifth) {
                check(Arrays.equals(MIXED_PASSWORD.toCharArray(), password),
                        "mixed password reaches authentication unchanged");
                return new SignatureResult(id, "SUCCESS", false, "READY");
            }
            return new SignatureResult(id, "ACCOUNT_LOCKED", true, "FAILED");
        }
        public void cancel(String id, String reason) { cancels++; }
        public void reportBusinessFailure(String id, String code) { }
        public SignatureResult getResult(String id) { return null; }
    }
}
