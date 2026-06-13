# Project Audit — Phase 0–4 Review

**Date:** 2026-06-13  
**Scope:** Full review of Service Marketplace codebase, Markdown docs, HTML learning docs, tests, phase goals, and portfolio readiness.  
**Status:** Read-only audit; no production code changes.

---

## 1. Kết luận tổng quan

| Khu vực | Trạng thái | Đánh giá | Confidence |
|---|---:|---|---:|
| Backend Java/Spring learning vehicle | 🟢 Tốt | Có domain model, REST, auth, booking, payment, docs học khá đầy đủ | Cao |
| Test suite hiện tại | 🟢 Pass | `./mvnw test` pass **284 tests** | Cao |
| Production correctness | 🟡 Chưa chắc | Có vài lỗi transaction/auth/webhook/double-booking cần sửa trước khi nâng điểm | Cao |
| HTML learning docs | 🟡 Nội dung tốt, navigation lỗi | Có docs VI/EN Phase 0–4, nhưng link Phase 3/4 và index bị stale/broken | Cao |
| Portfolio readiness | 🟡 70–75% | Backend ổn, nhưng README/diagrams/CI/coverage/frontend còn thiếu | Cao |
| Phase score hiện tại | 🟡 Hơi inflated | Phase 4 không nên giữ 9.15 nếu audit theo code thật | Cao |

---

## 2. Verification evidence

| Check | Result |
|---|---:|
| Full Maven test | ✅ Pass |
| Command | `./mvnw test` |
| Test result | `Tests run: 284, Failures: 0, Errors: 0, Skipped: 0` |
| Build result | `BUILD SUCCESS` |
| HTML files checked | 67 |
| Internal HTML links checked | 600 |
| Broken internal HTML links | 27 |
| Production code changed | Không |
| Files created/updated by review | `tasks/todo.md`, `docs/session-notes/session-018.md`, `docs/project-audit-2026-06-13.md` |

---

## 3. Revised phase scorecard sau audit

> Đây là **điểm đề xuất sau audit**, chưa sửa vào `CLAUDE.md`. Điểm lịch sử nên coi là “optimistic” cho đến khi các gap được fix.

| Phase | Historical | Revised | Delta | Verdict | Lý do chính |
|---|---:|---:|---:|---|---|
| Phase 0 — Foundation | 8.25 | **8.75** | +0.50 | 🟢 Pass | Foundation ổn, docs đầy đủ hơn điểm cũ |
| Phase 1 — Domain Model | 9.50 | **9.21** | -0.29 | 🟢 Strong | Domain tốt, nhưng test DB chưa đúng PostgreSQL/TestContainers |
| Phase 2 — API/Security | 9.05 | **8.32** | -0.73 | 🟡 Good, có gap | Role enforcement và vài docs/implementation mismatch |
| Phase 3 — Business Logic | 9.00 | **8.27** | -0.73 | 🟡 Good, chưa production-safe | Double-booking DB protection chưa đủ, retry/event gap |
| Phase 4 — Payment | 9.15 | **7.64** | -1.51 | 🟠 Cần sửa trước khi gọi excellent | Webhook transaction/idempotency/auth gap khá nghiêm trọng |
| **Average Phase 0–4** | **8.99** | **8.44** | **-0.55** | 🟡 Strong learning, not polished production | Điểm vẫn tốt, nhưng không nên quảng bá là 9+ toàn diện |

---

## 4. Rubric chi tiết theo phase

| Phase | Learning Docs 30% | Code Quality 30% | Tests 20% | Mastery 20% | Total |
|---|---:|---:|---:|---:|---:|
| Phase 0 | 9.0 | 8.5 | 8.5 | 9.0 | **8.75** |
| Phase 1 | 9.4 | 9.3 | 8.8 | 9.2 | **9.21** |
| Phase 2 | 8.8 | 8.0 | 8.0 | 8.4 | **8.32** |
| Phase 3 | 8.7 | 7.8 | 8.2 | 8.4 | **8.27** |
| Phase 4 | 8.2 | 7.0 | 7.4 | 8.0 | **7.64** |

---

## 5. Critical findings cần sửa

| Priority | Finding | Evidence | Impact | Fix direction |
|---:|---|---|---|---|
| P0 | Webhook idempotency không atomic như docs nói | `StripeWebhookHandler.java:88-117` | Stripe event có thể bị mark processed dù domain update fail | Không swallow exception sau event log; chỉ mark processed sau domain update thành công hoặc dùng status column |
| P0 | `PaymentController.getPaymentByOrder()` không check ownership | `PaymentController.java:136-142` | User đăng nhập có thể query payment của order khác nếu biết `orderId` | Thêm service method `getPaymentByOrderId(userId, orderId)` |
| P0 | `@Transactional protected` self-invocation không chạy như kỳ vọng | `PaymentService.java:73-126`, `RefundService.java:64-158` | DB operation không atomic như comment mô tả | Tách transactional method sang bean khác hoặc annotate public boundary đúng cách |
| P0 | Double-booking DB constraint không chặn overlap | `V4__create_bookings.sql:24-26` | DB chỉ chặn same start time, không chặn 09:00–10:00 vs 09:30–10:30 | PostgreSQL exclusion constraint/range type hoặc transactional locking strategy |
| P1 | Vendor service logic còn assume `userId == vendorId` | `VendorServiceController.java:58-63`, `VendorServiceManagement.java:66-69` | Có thể fail/sai ownership khi user id và vendor id lệch | Lookup vendor bằng `VendorRepository.findByUserId()` |
| P1 | Role-based access claim chưa đúng | `SecurityConfig.java:24-28`; source không có `@PreAuthorize` | API dựa vào service ownership, chưa có method-level role guard | Enable method security + add `@PreAuthorize` hoặc document rõ strategy hiện tại |
| P1 | `ServiceSpecification` implemented nhưng chưa wired | `ServiceSpecification.java:38-47`, `ServiceController.java:44-93` | Phase 3/4 claim search filters hơi inflated | Add `/api/services/search` hoặc support query params trên `/api/services` |
| P2 | TestContainers có base class nhưng chưa dùng | `BaseIntegrationTest.java:53-56`, `BaseDataJpaTest.java:46-49` | Test pass trên H2, chưa validate PostgreSQL/Flyway thật | Add tests extending base classes |
| P2 | Test profile dùng H2 + `ddl-auto=create-drop`, Flyway disabled | `src/test/resources/application-test.yml:17-26` | Không bắt được migration/jsonb/Postgres-specific bug | Chuyển critical integration tests sang PostgreSQL TestContainers |
| P2 | Refund DB enum mismatch future risk | `RefundStatus.java:22-26`, `V5__create_orders_and_payments.sql:44-51` | Entity có `PROCESSING`, DB check constraint không có | Update migration/schema or avoid persisting PROCESSING |

---

## 6. HTML docs audit

| Area | Status | Evidence | Action |
|---|---:|---|---|
| Phase 0 docs | 🟢 Complete | VI 5 docs, EN 5 docs | Giữ, chỉ polish nếu cần |
| Phase 1 docs | 🟢 Complete | VI 5 docs, EN 5 docs | Giữ |
| Phase 2 docs | 🟢 Complete | VI 6 docs, EN 6 docs | Recheck stale claims |
| Phase 3 docs | 🟡 Content complete, links broken | Phase 3 language switcher uses wrong relative paths | Fix `../en`/`../vi` → `../../en`/`../../vi` |
| Phase 4 docs | 🟡 Content complete, index stale | `docs/html/index.html:121-126` still says Phase 4 `0/4` | Update landing page + phase cards |
| HTML link health | 🔴 27 broken links | Link checker: 27/600 internal links broken | Batch fix paths |
| Backup/noise files | 🟡 Present | `.backup`, `.DS_Store` files under docs/html | Remove if not needed |
| README doc links | 🟡 Some targets missing | `README.md:114-122` references API/C4/state/sequence dirs not present | Either create docs or mark Phase 7 pending |

---

## 7. Portfolio readiness table

| Portfolio requirement | Current state | Grade | Comment |
|---|---|---:|---|
| Java/Spring backend | Implemented | 🟢 A- | Good learning/demo value |
| Domain-rich design | Implemented | 🟢 A | Phase 1 is strongest |
| REST API + validation | Mostly implemented | 🟡 B+ | Needs role/method security cleanup |
| Booking/concurrency story | Partially implemented | 🟡 B | App overlap check exists, DB protection incomplete |
| Stripe/payment story | Implemented but risky | 🟠 C+ | Needs webhook/idempotency transaction fix |
| Tests | Passing | 🟡 B | Count good, realism gap due H2 |
| PostgreSQL/Flyway confidence | Partial | 🟡 B- | Migrations exist, tests don’t validate them |
| Redis | Dependency/config exists | 🟠 Pending | Phase 5 not implemented |
| Frontend | Empty | 🔴 Pending | `frontend/` has no files |
| CI/CD | Missing | 🔴 Pending | No `.github/workflows` found |
| Architecture diagrams | Partial | 🟡 B- | ERD exists; C4/sequence/state missing |
| README polish | Basic | 🟡 B- | Good intro, but references missing docs |
| HTML learning docs | Rich but broken links | 🟡 B+ | Content strong; navigation needs cleanup |

---

## 8. “5 phase implement ổn không?”

| Phase | Ổn để học? | Ổn để show portfolio? | Ổn để gọi production-ready? | Verdict |
|---|---:|---:|---:|---|
| Phase 0 | ✅ | ✅ | ⚠️ | Ổn |
| Phase 1 | ✅ | ✅ | ⚠️ | Mạnh nhất |
| Phase 2 | ✅ | ⚠️ | ❌ | Cần security cleanup |
| Phase 3 | ✅ | ⚠️ | ❌ | Cần concurrency hardening |
| Phase 4 | ✅ | ⚠️ | ❌ | Cần payment correctness fix |

**Kết luận:** Phase 0–4 có thể xem là **learning-complete**, nhưng chưa nên xem là **portfolio-polished** hoặc **production-ready**.

---

## 9. Thứ tự giao GLM coder xử lý

| Order | Task for GLM | Why first | Review criteria |
|---:|---|---|---|
| 1 | Fix payment webhook idempotency transaction | P0 correctness | Failed domain update must not mark event processed |
| 2 | Fix payment-by-order authorization leak | P0 security | User A cannot fetch User B payment by order ID |
| 3 | Fix vendor `userId` vs `vendorId` assumption | P1 auth/domain correctness | Vendor APIs always lookup vendor profile by user ID |
| 4 | Wire `ServiceSpecification` into real search API | Phase claim correctness | Query params/search endpoint covered by tests |
| 5 | Add PostgreSQL/TestContainers tests | Test confidence | At least migration/jsonb/spec/payment-webhook tests run on PostgreSQL |
| 6 | Fix booking overlap DB protection strategy | Core booking correctness | Concurrent overlapping booking cannot both commit |
| 7 | Repair HTML docs links/index | Learning docs quality | Link checker returns 0 broken internal links |
| 8 | Update README/architecture docs | Portfolio polish | README no longer links missing docs |

---

## 10. Suggested operating model

| Role | Work |
|---|---|
| GLM coder | Implement focused fixes one task at a time |
| Main review model | Review diff, challenge assumptions, check tests, re-score phase |
| Hien | Decide priority: “fix correctness first” vs “docs polish first” |

Recommendation: **fix correctness first**, especially Phase 4 payment/webhook and authorization. Docs polish after that will be cleaner because docs can explain the final correct implementation.

---

## 11. Files changed in this review

| File | Purpose |
|---|---|
| `tasks/todo.md` | Full review plan + audit notes |
| `docs/session-notes/session-018.md` | Mandatory session handoff note |
| `docs/project-audit-2026-06-13.md` | This audit report with 11 evaluation tables |

No production code was changed.

---

## Next-session checklist

1. Start by reading this file and `docs/session-notes/session-018.md`.
2. Use `tasks/todo.md` as the audit tracker.
3. If GLM implements fixes, request small focused diffs and tests per task.
4. Re-run `./mvnw test` after each meaningful fix.
5. Re-run HTML link checker after docs navigation fixes.
