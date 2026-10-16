# Learning acceptance probe sources

These tools are acceptance evidence sources. They are outside application runtime
and CI. The authorized production source is
`8dcf8e7d44c6048318ad1e306e1425c8fd40fb70`. Exact deployed image and source guards
remain mandatory; building or compiling these files is not a live PASS result.

Run from this directory or the original ignored task directory. Keep this
published directory at `deploy/compact/reports/probes` so the repository-relative
paths resolve correctly. Requires Python with the repository acceptance
requirements, JDK21+ javac and Docker CLI/Compose. Use only a previously restored
independent recovery clone with an explicit `tianji-recovery-*` project and offset
1000, its current manifests/private configuration and the authorized fixture.
The primary deployment is rejected. Never publish private configuration or logs.

Before mutating probes, verify original backup restoration, incremental migration,
all seven exact images and the full role browser suite: 42 passed, 0 failed,
30 expected role skips. Its report must postdate current application startup.
Run interaction before settlement; settlement deliberately restarts commerce in
that isolated clone, invalidating the browser timestamp for another mutating probe.
The read-only pagination probe can run first, after its exact image/source and
MySQL ownership guards. It compares all page20/page100 results and empty tails
against actual isolated database history of more than 100 courses.

```powershell
python deploy/compact/reports/probes/test_live_settlement_guards.py
python deploy/compact/reports/probes/verify_live_learning_pagination.py --home <recovery-home> --project <recovery-project>
python deploy/compact/reports/probes/live-interaction-probe.py --home <recovery-home> --project <recovery-project> --offset 1000
python deploy/compact/reports/probes/run_live_settlement.py --home <recovery-home> --project <recovery-project>
```

Settlement extracts the actual deployed education JAR, verifies its digest and
compiles the Java helper. A fixed injected Shanghai clock exercises an unused
future month. Only its reward rule is synthetic. Actual production award/settle
code creates ledger/snapshot/request/outbox; real internal Exam/User HTTP,
application workers, RabbitMQ, promotion stock/ticket and learning ACK are
required. An intentional clone-commerce outage must leave a durable queued event
and PENDING request without issuing a ticket; finally always restores that service.
Replay must preserve frozen ranks and debit stock once. The exact earned ticket
is then explicitly selected (one candidate, within six-coupon limit), confirmed,
used in a real idempotent order and paid through the local mockPay simulator.
The persisted ten-cent discount, provider/trade facts, exact ticket usage and new
learning entitlement must match. This exercises no real payment vendor and
changes no host clock. Cross-schema UUID receipts use byte-exact binary casts
because historical tables can use different MySQL collations.

The helper's optional `observe` action performs SELECT observations without
award, settlement, reward publication or other business replay. Its JDBC URL
explicitly sets `readOnlyPropagatesToServer=true`; Spring sets the transaction
read-only. Before reading the snapshot it requires JDBC `isReadOnly=true`,
`autoCommit=false` and MySQL `@@session.transaction_read_only=1`. This protocol
was verified against the actual deployed Connector/J 9.4.0 using only database
and session-variable queries. It avoids an extra Spring enforcement statement
that the already read-only connection would reject. The private one-off
continuation script is deliberately excluded from this general source bundle.

The helper retains six calendar/grace and two board-page assertions. Future
readiness can contain zero exam events: record the actual counts without claiming
this proves blocking on an existing exam backlog. Global maintain() restart
compensation is covered separately by real-DB reliability tests with mock
Exam/User dependencies. This is not wall-clock waiting through natural month end.
`FixtureDtoContractProbe.java` additionally provides 16 offline Bean Validation
checks against actual packaged coupon, issuance, order and payment DTOs. Those
checks validate fixture input boundaries, not live business acceptance.

`LIVE_REWARD_UI.md`, `run_live_reward_ui.py` and `live_reward_ui.mjs` document the
read-only real-browser earned-reward and already-paid order checks; purchasing
is performed by the HTTP settlement probe.
A failed probe report must stay FAILED. If an observational check fails after
business completion, preserve it and report a separately verified continuation;
never rewrite that failure or repeat purchase merely to label it successful.
This source bundle excludes the private one-off diagnostic continuation script.

All settlement/interaction/private reports, extracted JARs/classes/dependencies
and work directories always stay under ignored
`deploy/compact/.local/learning-upgrade-20261015`, even when these sources run from
this published directory. Do not copy output directories, .env/config YAML,
accounts/fixture files, private logs, SQL dumps or binary artifacts into reports.
Only reviewed source files and this manifest belong in the source publication.
