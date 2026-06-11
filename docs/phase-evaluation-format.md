# Phase Evaluation Format

This document defines the scoring system for evaluating each learning phase. Use this format to maintain consistency across all phase evaluations.

## Score Calculation

```
┌─────────────────┬────────┬────────┬──────────┐
│    Criteria     │ Weight │ Score  │ Weighted │
├─────────────────┼────────┼────────┼──────────┤
│ Learning Docs   │ 30%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ Code Quality    │ 30%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ Test Coverage   │ 20%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ Concept Mastery │ 20%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ TOTAL           │ 100%   │        │ X.XX/10  │
└─────────────────┴────────┴────────┴──────────┘
```

## Criteria Definitions

### 1. Learning Docs (30%)

**What to check:**
- Coverage: All planned topics covered?
- Diagrams: CSS flow charts, box diagrams, architecture diagrams
- Code Snippets: Actual code from project with syntax highlighting
- "Tại sao" Sections: Explains WHY decisions made, not just WHAT
- Tables: Comparison tables for quick reference
- Callouts: Note/Warning/Tip boxes for important caveats
- Navigation: Navbar with numbered links, VI/EN language switcher

**Scoring Guide:**
| Score | Description |
|-------|-------------|
| 9-10 | All elements present, comprehensive, production-quality |
| 7-8 | Most elements present, minor gaps (e.g., missing sequence diagrams) |
| 5-6 | Basic coverage, missing diagrams or "why" sections |
| Below 5 | Incomplete, major gaps |

---

### 2. Code Quality (30%)

**What to check:**
- Architecture: Layered architecture followed (interfaces → application → domain → infrastructure)
- Patterns: DTOs, Value Objects, proper naming conventions
- Injection: Constructor injection (@RequiredArgsConstructor), no field injection
- Documentation: Javadoc explaining WHY, not just what
- Error Handling: Global exception handler, consistent error format
- Security: Stateless JWT, proper filter chain, BCrypt passwords

**Scoring Guide:**
| Score | Description |
|-------|-------------|
| 9-10 | Production-ready, follows all conventions, no dead code |
| 7-8 | Minor issues (e.g., N+1 queries, incomplete features) documented for next phase |
| 5-6 | Works but needs refactoring, some architectural violations |
| Below 5 | Major architectural violations, security issues |

---

### 3. Test Coverage (20%)

**What to check:**
- Unit Tests: Domain logic tested
- Integration Tests: Repository/Controller tests where appropriate
- Passing: All tests passing
- Coverage: Target 80%+ for service layer

**Scoring Guide:**
| Score | Description |
|-------|-------------|
| 9-10 | Comprehensive tests, all passing, 80%+ coverage |
| 7-8 | Good coverage, deferred tests documented with plan |
| 5-6 | Basic tests only, significant gaps |
| Below 5 | No tests or failing tests |

---

### 4. Concept Mastery (20%)

**What to check:**
- Docs explain WHY: Not just code dumps
- Hien can explain: Answer questions about concepts
- Progressive depth: Basic → Implementation → Edge cases
- Code references: Links to actual project files

**Scoring Guide:**
| Score | Description |
|-------|-------------|
| 9-10 | Clear explanations, Hien can teach back concepts |
| 7-8 | Good coverage, minor gaps in understanding |
| 5-6 | Basic understanding, needs reinforcement |
| Below 5 | Concepts not understood, just copy-paste |

---

## Historical Scores

| Phase | Learning Docs | Code Quality | Test Coverage | Concept Mastery | Total | Status |
|-------|----------------|--------------|---------------|-----------------|-------|--------|
| Phase 0 | 8.5 | 8.0 | 8.0 | 9.0 | **8.25** | ✅ Complete |
| Phase 1 | 9.5 | 9.5 | 9.5 | 9.5 | **9.5** | ✅ Complete |
| Phase 2 | 9.5 | 9.0 | 7.5 | 9.0 | **8.85** | ✅ Complete |
| Phase 3 | — | — | — | — | — | ⏳ Pending |
| Phase 4 | — | — | — | — | — | ⏳ Pending |
| Phase 5 | — | — | — | — | — | ⏳ Pending |
| Phase 6 | — | — | — | — | — | ⏳ Pending |
| Phase 7 | — | — | — | — | — | ⏳ Pending |

---

## Verification Questions

Use these questions to verify Hien understands the concepts for each phase:

### Phase 0 (Foundation)
- "Spring IoC là gì? Tại sao dùng Dependency Injection?"
- "Docker Compose giúp gì trong development?"

### Phase 1 (Domain Model)
- "Value Object khác Entity thế nào?"
- "Tại sao Vendor has-a User, không phải extends User?"
- "BookingStatus state machine hoạt động ra sao?"

### Phase 2 (API Layer)
- "Tại sao dùng DTO thay vì return Entity trực tiếp?"
- "JWT payload có bị encrypt không? Tại sao không được put sensitive data?"
- "Access token ngắn (15min) còn refresh token dài (7 days) - tại sao?"
- "@NotBlank khác @NotNull thế nào? Khi nào dùng cái nào?"
- "MapStruct generate code khi nào? Tại sao tốt hơn reflection?"
- "@RestControllerAdvice bắt exception từ đâu? Controller cần try-catch không?"

### Phase 3 (Business Logic) - Planned
- "Optimistic locking hoạt động ra sao? Khi nào throw ObjectOptimisticLockingFailureException?"
- "Time slot conflict detection kiểm tra thế nào?"
- "@Transactional isolation levels ảnh hưởng gì?"

---

## Evaluation Template

For each phase, create an evaluation HTML file following this structure:

```markdown
## 1. Learning Docs: X/10
- Strengths (bullet list)
- Areas for Improvement (bullet list)
- Detailed Breakdown (table)

## 2. Code Quality: X/10
- Strengths
- Areas for Improvement (with Phase N fix plan)
- Code Statistics

## 3. Test Coverage: X/10
- Current State
- Assessment
- Next Phase Test Plan

## 4. Concept Mastery: X/10
- Expected Concepts (from learning-roadmap.md)
- Mastery Verification Questions

## 5. vs Expectations
- Compare with learning-roadmap.md goals

## 6. Recommendations
- Immediate actions
- Next phase priorities

## 7. Conclusion
- Summary callout with score
```

---

## Related

- [Learning Roadmap](../html/roadmap.html) — Progress table and phase details
- [CLAUDE.md](../../CLAUDE.md) — Project rules and documentation standards
