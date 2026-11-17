# NC65 current-user confirmation bridge

Status: 已开发但尚未在 NC65 环境验证.

## Interface and deployment boundary

`nc.itf.baseapp.qd.esign.IIdentityConfirmationService.confirm(IdentityConfirmationRequest)`
returns `IdentityConfirmationResult`, or throws `BusinessException` for malformed
requests, missing current-user identity and native environment/service failures.
Expected wrong-password, unavailable-account and locked-account outcomes are DTO
results. `confirmedAt` is server epoch milliseconds on success, zero otherwise.
`failureCount=-1` means that this result did not inspect or increment the counter.

The request carries a request ID and an owned `char[]` credential. It has no
user-ID selector. Both caller and server must clear their own credential copies;
the bridge clears its request and temporary copies in `finally`. The native API
requires a short-lived Java String, which cannot be explicitly zeroed. Neither
request nor result is a signature log or proof of business success. The signature
orchestration service must call this bridge server-side, validate the result and
persist the actual result; never accept a client-supplied result as evidence.

Package public types into the additive baseapp public output, and the implementation
into additive baseapp private output. `META-INF/qd_esign_auth.upm` adds the protected
service on MASTER with `tx=NONE`, matching the platform login service. It does not
replace any native component. No NC Home deployment has been performed.

## Verified native contracts

All locations below are relative to the configured local NC Home. These claims
were checked using `javap -p -c -l`; they are not NC runtime test results.

- `external/lib/fwpub.jar`: `InvocationInfoProxy.getUserId/getSysid/getUserDataSource/getGroupId`
  supplies the authenticated invocation identity. No client user identity is trusted.
- `modules/baseapp/lib/pubbaseapp_apprbacLevel-1.jar`:
  `IUserManageQuery.getUser(String)` loads that user; `PasswordSecurityLevelFinder.getPWDLV`
  supplies the configured `errorloginThreshold`.
- `modules/baseapp/lib/pubbaseapp_apploginLevel-1.jar`: `LoginVerifyBean.checkUser`
  checks account validity (native source 222-257); `identifyUserIsAutoLock` checks
  original locking/unlocking rules (458-479); static-password `identityVerify`
  uses `RbacUserPwdUtil.checkUserPassword` (198-202) without creating a session.
- `modules/baseapp/lib/pubbaseapp_appsfbaseLevel-1.jar`:
  `ServerEnvironmentCenter.registerFailure/getFailureCount/clearAllFailure`
  reuses original failure counters (282-302). Those are native process-memory
  counters, not durable signature/audit records.
- `modules/baseapp/META-INF/lib/baseapp_apploginLevel-1.jar`:
  `SystemConfigParser.getSystemConfig` supplies the native fallback threshold and
  reads `ierp/sf/systemconfig.xml`. Baseapp placement is intentional because this
  class is in the platform's private implementation output.
- `LoginEngine.dealWithLoginFailure` (363-383) locks when count is strictly
  **greater than** the threshold, then clears the native failures. The bridge
  preserves that comparison. It calls neither `LoginEngine.verify` nor `login`.

## Local checks and pending NC checks

Compiled with the configured NC Home's `ufjdk/bin/javac.exe`, IBM Java 7
(`1.7.0-internal`), `-encoding UTF-8 -source 1.7 -target 1.7`.
The local contract test checks defensive credential ownership, Java serialization,
secret-free request text, clearing on rejected inputs and explicit result status.
It makes no authentication, locking, database or server calls.

Actual local run: `IdentityConfirmationContractTest` passed **15 assertions**, JVM
exit code `0`. The IBM JVM emitted Windows Preferences registry-access warnings;
these did not fail compilation or the local checks. They are host restrictions,
not evidence about NC runtime authentication.

NC integration still must verify protected lookup, MASTER routing, private class
visibility, original password modes, failed-count/lock/unlock boundaries,
multi-node concurrency and interaction with the owning signature transaction.
Super-administrator accounts without a normal authenticated business-user record
are rejected rather than manufacturing a UserVO or altering the invocation identity.
Native password verification internally maps some crypto errors to false; the
bridge preserves that platform behavior and does not hide its own exceptions.

No SQL, production configuration changes or existing product-source edits belong
to this bridge. The local service adds no password storage and no standalone
failure-count policy. Authentication failure audit persistence is the owning
signature service's responsibility.
