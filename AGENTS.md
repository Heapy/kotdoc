# kotdoc development

- Preserve the full scope recorded in docs/PORTING.md. Never claim a whole upstream
  port is complete based on a partial passing suite.
- Implement conversion behavior in Kotlin. Upstream programs may serve as test
  oracles but must not be production conversion dependencies.
- Read the relevant pinned upstream implementation and tests before porting.
- Keep upstream expectations independent of the Kotlin implementation. Preserve
  fixtures verbatim and retain license/copyright notices for translated code.
- Update the porting ledger with concrete evidence and remaining gaps.
- Run relevant tests before commits. Public commits and pushes are authorized.
- When discovery is semantic, begin with jbcontext search. If it does not index
  this checkout, use the known upstream paths and report that limitation.
