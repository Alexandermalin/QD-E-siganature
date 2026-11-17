# NC65 client implementation and verification

Status: 已开发但尚未在 NC65 环境验证.

## Implemented entry points

- `ElectronicSignatureActionInterceptor` implements the actual NC65
  `nc.ui.uif2.actions.ActionInterceptor`. It requires a node-specific
  `OperationContextProvider` and `AuthorizedRequestConsumer` for controlled
  operations. Availability is inspected before preparation. Both switches off
  permits original NC behavior; controlled operations without a verified final-RPC
  handoff stop. No actual node implementation is enabled by these XML examples.
  `SignatureLifecycleInstaller.install(NCAction, signing)` must run after the
  entire node/plugin interceptor chain has been assembled. It installs an outer
  delegate using the verified get/setInterceptor APIs. Native before=false,
  before exceptions, skipped after-failure callbacks and throwing after-success
  callbacks all complete the owned signing lifecycle. Cleanup is idempotent and
  a rejected nested duplicate cannot cancel an earlier active operation. The
  wrapper preserves the delegate's exact after-failure boolean and original
  exception; cleanup failures are attached and visibly reported. Controlled
  operations refuse a missing or replaced outer wrapper; BYPASS is unchanged.
  Native pubapp composites are traversed explicitly; installing signing inside
  the other UIF2 composite is rejected because its first-true failure short-circuit
  can skip original handlers. Unknown custom composites require a verified node
  adapter instead of reflective/global interception.
- `ElectronicSignatureDialog` uses native `UIDialog`, `UIPanel`, `UILabel`,
  `UIPasswordField`, `UITextArea`, `UIButton` and `UIScrollPane`. It shows the UI
  personnel label, node, operation, batch objects, password and reason. Server
  identity is authoritative. Failed authentication ends this operation; cancel,
  window-close and disposal share one cancellation attempt. Password arrays and
  the password field are cleared after submission and on close.
  The reason limit is 2000 characters and 4000 UTF-8 bytes, checked before any
  password confirmation. Identity success on a non-READY request is not reused.
- `QdPolicyAction` opens `QdPolicyDialog`: read by business unit/node/button,
  configured signature/reason/audit flags, independent group master control,
  server-returned optimistic version on save, mandatory reload after uncertain
  writes. Management reads use `getPolicyForManagement` so disabling master does
  not destroy the underlying configured flags.
- The registered log funclets embed `QdLogQueryPanel` for SIGNATURE, AUDIT, and
  SECURITY. The example `QdLogQueryAction` and related-log dialog use the same
  panel. It provides UTC-bound server queries from explicit Beijing time inputs, defaults
  to the previous 24 hours, supports 100-row pages and operator/node/bill/request/
  batch/result/reason filters where the backend supports them. Security disables
  node, bill, request, batch and reason filters. Both operator ID and code can be
  queried. Rows are read-only, and list timestamps are explicitly labelled UTC.
- Selected-row summary and related-signature/audit windows use the same request
  ID. `QdAuditDetailsDialog` retrieves authorized audit details by audit ID/month,
  with separate event/difference/snapshot tabs and shared pagination. Large-field
  previews visibly expose server truncation flags and original character counts;
  a truncated preview is never labelled a complete deletion snapshot.
- Loaded-page export sends the observed IDs to `exportLoadedCsv`; the server
  checks their current values, original filters and permissions. New rows cannot
  drift into that page through an offset re-query. The UI explicitly states that
  exports contain server current values, not a frozen rendered-value snapshot.
  Full-filter export re-queries the authorized result. A save chooser is required;
  cancelling it starts no write. Existing files
  require explicit overwrite confirmation. Full bytes are written to a sibling
  temporary file, then moved to the selected target; rejected overwrite/change
  checks leave the prior file intact. Limits and CSV encoding/sanitization remain
  server responsibilities.

`NcClientWorker` runs password-confirmation/management/query/export calls off the Swing EDT. It captures
the native caller identity and transport-token reference per task, applies the
scope only in the worker and restores its previous native state in `finally`.
Tokens do not enter DTOs, files, diagnostics or a custom global cache. This is a
native transport identity scope, separate from the explicit business request DTO.

## Native API evidence

Inspected with `javap -p -c -l` in the configured local NC Home:

- `modules/riart/client/lib/uiriart_ncbeanLevel-1.jar`: UIDialog constructors,
  `showModal`, `closeOK/closeCancel`, native field/button/container types.
- `modules/riart/client/lib/uiriart_riaUIFactoryIILevel-1.jar`: `NCAction.doAction`
  and all three `ActionInterceptor` callbacks. `afterDoActionFailed` returns true
  to retain NC's original handling; it reports only an approved failure code.
- `external/lib/fwpub.jar`: `InvocationInfoProxy` identity accessors and
  `NetStreamContext.getToken/setToken`. `ClientInvocationInfo` uses plain
  ThreadLocal, so background calls do not assume caller-thread inheritance.

## Actual local validation

- All client sources compiled against the real local NC dependencies using the
  NC Home's IBM Java 7 compiler, UTF-8, source/target 1.7.
- `nc.ui.uapbd.qd.esign.SignatureClientTests`: 51 assertions passed. These are
  offline controller/test-double checks covering duplicate blocking, signature
  handoff, failure/cancellation, unsupported and bypass policy combinations,
  original exception preservation, approved failure codes and missing dispatch,
  plus native composite short-circuits, callback exceptions, duplicate cleanup
  and missing-outer/incompatible-composite rejection. The lifecycle cases invoke
  the real local pubapp composite class with test-double business services.
- `nc.ui.uapbd.qd.esign.ManagementClientTests`: 16 assertions passed. These are
  actual local strict time conversion and temporary-file publishing checks.
  They also verify loaded-page ID selection and reject missing/duplicate IDs.
  The IBM JVM's default sandbox temp directory denied file rename; the same test
  passed with `java.io.tmpdir` set to a writable project build directory. Windows
  Preferences registry-access warnings were also emitted by this JVM.
- Both example XML files parse successfully and contain zero active bean nodes.

These results do not validate native window appearance, actual login context or
MASTER routing, remote permissions, live exports or real NC business actions.
Those remain pending test-environment acceptance. No NC Home files, databases,
production configuration or function-node registrations have been changed.
