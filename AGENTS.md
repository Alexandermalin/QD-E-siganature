# Project working agreements

- During development, compile the changed Java sources and the directly affected code, then run the relevant targeted tests. Use `tools/test-targeted.ps1` where its baseline and classpath fit the change.
- When changing shared VO classes, public interfaces or cross-module dependencies, include affected callers and modules in the verification. Choose the platform classpath if a narrower profile has not been validated for the change.
- Review changes to client XML, UPM, configuration and SQL with checks appropriate to those files.
- After a delivery batch is stable, run `tools/build.ps1 -Test` once, then package the same successful build with `tools/package.ps1 -SkipBuild`. Repeat the full delivery checks when subsequent changes affect the result.
- A targeted test does not replace the full delivery build or on-site NC65 verification. Describe the scope and results of the checks performed.

- Start each change in a dedicated Git worktree from the selected test baseline. Record the baseline tag and the tested commit when handing changes to the release chat.
- Check `tools/Test-RuntimeBaseline.ps1 -NcHome <path>` before building an overlay against the pinned runtime. See `docs/DEVELOPMENT_BASELINE.md` for source coverage and the release workflow.
