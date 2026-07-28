# Backend Tech Stack — Java 25

**Verified against Maven Central on 2026-07-27.** Every version below was resolved from
`maven-metadata.xml` / the Spring Boot BOM, not from memory. Re-verify before you pin.

---

## 1. Platform

| Component | Version | Note |
|---|---|---|
| **Java** | **25 (LTS)** | JDK 26 exists (Mar 2026) but is a 6-month release. 25 is the LTS — support to ~2033. Correct choice for a 20-year platform. |
| JVM distribution | Temurin 25 | Or Corretto 25. Both free, both LTS-supported. |
| **Spring Boot** | **4.1.0** | Latest release. (4.0.7 is the latest 4.0.x if you want a more settled line.) |
| Spring Framework | 7.0.8 | Managed by Boot |
| Spring Security | 7.1.0 | Managed by Boot |
| Spring Data BOM | 2026.0.0 | Managed by Boot |
| **Spring Modulith** | **2.1.0** | ⚠️ **Not** managed by the Boot 4.1 BOM — import its own BOM (verified: no `spring-modulith.version` property in `spring-boot-dependencies:4.1.0`). |
| Hibernate ORM | 7.4.1.Final | Managed by Boot |
| Hibernate Validator | 9.1.0.Final | Jakarta Validation 3.1 |
| **Jackson** | **3.1.4** | ⚠️ Jackson **3** — new `tools.jackson.*` package. See §4. |
| Flyway | 12.4.0 | Managed by Boot |
| PostgreSQL JDBC | 42.7.11 | Server: PostgreSQL 16 or 17 |
| HikariCP | 7.0.2 | |
| Lettuce (Redis) | 7.5.2.RELEASE | |
| Tomcat | 11.0.22 | Jakarta EE 11 |
| Micrometer | 1.17.0 | Tracing 1.7.0 |
| JUnit Jupiter | **6.0.3** | ⚠️ JUnit **6**. See §4. |
| Testcontainers | **2.0.5** | ⚠️ Testcontainers **2**. See §4. |
| **Build** | **Gradle 9.6.1** (Kotlin DSL) | Maven 3.9.x also fine; Maven 4 is still RC — don't. |

Not in the Boot BOM, add explicitly:
- **Spring AI 2.0.0** — only if you want the Anthropic client managed for you. A plain `RestClient` against the Messages API is honestly simpler for the two AI calls this project needs (see §6).
- **MapStruct** — latest is `1.7.0.Beta2`. A beta in a 20-year codebase is a poor trade. **Skip MapStruct**; Java 25 records + hand-written static factory methods are clearer and have zero annotation-processor risk.

---

## 2. Your machine needs upgrading

Checked locally — you're on:
- `openjdk 21.0.11` (only JDK installed)
- `Gradle 4.4.1` (the Ubuntu apt package, from 2017 — will not build this)
- no `mvn`

Fix with SDKMAN, which keeps JDK 21 available for your other projects (`cartup-advanced-search`):

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk install java 25-tem          # Temurin 25 LTS
sdk install gradle 9.6.1

# per-project pinning, so cartup keeps using 21
cd ~/Documents/Projects/Reunion
sdk env init                     # writes .sdkmanrc -> java=25-tem
```

Then commit the Gradle wrapper (`gradle wrapper --gradle-version 9.6.1`) so CI and any future
maintainer get the right build tool without installing anything. Ignore the apt `gradle` entirely.

---

## 3. Which Java 25 features actually earn their place here

Most "Java 25 features" lists are irrelevant to a CRUD service. These four are not.

### 3.1 Virtual threads — the whole reason this scales on one small box

Your workload is ~100% I/O wait: Postgres, SMS gateway, WhatsApp, S3, the Anthropic API. Platform
threads would have you sizing a pool and tuning it. Virtual threads make that a non-problem.

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

One line. Tomcat serves each request on a virtual thread, and a 4 vCPU VPS handles the
registration-day spike (a few hundred concurrent) without a tuned executor anywhere in the codebase.

**One caveat that will bite you:** HikariCP is still a fixed pool, and it should be. Virtual threads
remove the *thread* limit, not the *database connection* limit. Keep the pool small (10–20) — it is
now your intentional backpressure valve. Unbounded virtual threads all queueing on a 10-connection
pool is correct behaviour, not a bug.

### 3.2 Scoped Values (JEP 506 — final in 25) — use for the audit actor

You have `audit_log` on every admin/ambassador read of contact data (§7 of the design doc), plus
visibility filtering that depends on *who is asking*. The classic solution is a `ThreadLocal`
holding the current actor. **Don't** — with virtual threads you may have tens of thousands of them,
and `ThreadLocal` is mutable, leak-prone, and needs disciplined `remove()` in a finally block.

`ScopedValue` is immutable, automatically unbound when the scope exits, and cheap per-thread:

```java
public final class Actor {
    public static final ScopedValue<AuthenticatedActor> CURRENT = ScopedValue.newInstance();

    public static AuthenticatedActor required() {
        if (!CURRENT.isBound()) throw new IllegalStateException("no actor bound");
        return CURRENT.get();
    }
}

// Servlet filter, after JWT validation
ScopedValue.where(Actor.CURRENT, actor)
           .run(() -> chain.doFilter(request, response));
```

Then your Hibernate interceptor / audit aspect reads `Actor.required()` with no plumbing through
twelve method signatures, and it cannot leak into a pooled thread.

### 3.3 Compact Object Headers (JEP 519 — production in 25)

```
-XX:+UseCompactObjectHeaders
```

Shrinks object headers from 12–16 bytes to 8. On a heap full of small JPA entities and Strings —
exactly what a directory of 15,000 people is — expect a **10–20% heap reduction** for free. On an
8GB VPS that you're paying for personally, for twenty years, that's worth one flag.

Production-ready in 25 (it was experimental in 24). Measure it, don't assume it.

### 3.4 Key Derivation Function API (JEP 510 — final in 25)

You are storing phone numbers and home addresses of elderly people. If you encrypt PII columns
(you should, at least the contact values), you need per-purpose keys derived from one master key.
JDK 25 gives you HKDF in the standard library — no Bouncy Castle, no hand-rolled HMAC loop:

```java
KDF hkdf = KDF.getInstance("HKDF-SHA256");
SecretKey contactKey = hkdf.deriveKey("AES",
        HKDFParameterSpec.ofExtract()
                .addIKM(masterKey)
                .addSalt(tenantSalt)
                .thenExpand("contact-value-v1".getBytes(UTF_8), 32));
```

Versioned `info` strings (`contact-value-v1`) give you key rotation without re-encrypting
everything at once.

### 3.5 Records, sealed interfaces, pattern matching — for the messy domains

The import and merge pipelines have genuinely sum-typed outcomes. Model them as such and let the
compiler enforce exhaustiveness, instead of an enum plus a nullable field:

```java
public sealed interface ImportOutcome {
    record Created(UUID personId) implements ImportOutcome {}
    record MatchedExisting(UUID personId, double score) implements ImportOutcome {}
    record NeedsReview(UUID candidateId, List<String> reasons) implements ImportOutcome {}
    record Rejected(String reason) implements ImportOutcome {}
}

String label = switch (outcome) {
    case Created c            -> "new";
    case MatchedExisting m    -> "merged (%.2f)".formatted(m.score());
    case NeedsReview n        -> "review: " + String.join(", ", n.reasons());
    case Rejected r           -> "rejected: " + r.reason();
};   // add a case to the interface and this fails to compile — exactly what you want
```

Also use records for every DTO and every value object (`PhoneNumber`, `BatchYear`, `Money`).

### 3.6 Deliberately NOT using

| Feature | Why not |
|---|---|
| **Structured Concurrency** (JEP 505) | Still **preview** in 25 — 5th preview, API has changed every release. Do not put `--enable-preview` in a production service you'll maintain for 20 years. Revisit when it finalizes. |
| **Stable Values** (JEP 502) | Preview. Same reason. |
| Module import declarations (JEP 511) | Final, but saves a few import lines. Not worth being unusual. |
| Compact source files / instance `main` (JEP 512) | Great for one-off ops scripts (`jbang`-style). Not for the service. |
| JPMS modules (`module-info.java`) | Spring Modulith gives you enforced boundaries without fighting the ecosystem. |
| ZGC / Shenandoah | G1 (the default) is right for a 2–4GB heap. ZGC pays off at large heaps with strict pause targets — you have neither. Don't tune a GC you haven't measured. |

---

## 4. Breaking changes that will cost you an afternoon each

Spring Boot 4 is a major version. Budget for these — they are the reason a fresh project is *easier*
than a migration, and you're starting fresh, so most cost you nothing but awareness.

1. **Jackson 3** (`3.1.4`). Package moved `com.fasterxml.jackson.*` → `tools.jackson.*`.
   `ObjectMapper` is now immutable, built via builder. Any StackOverflow answer you copy will be
   Jackson 2. Boot 4 still ships the Jackson 2 BOM (`2.21.4`) for transitive compatibility — don't
   mix them in your own code.
2. **JUnit 6** (`6.0.3`). Requires Java 17+, drops the JUnit 4 vintage engine by default.
3. **Testcontainers 2** (`2.0.5`). Module coordinates and some APIs changed from 1.x.
4. **Hibernate 7** — Jakarta Persistence 3.2. `@Where` is replaced by `@SQLRestriction`;
   `Criteria` API changes; stricter on some lazy-loading patterns.
5. **Spring Security 7** — the fully-lambda `SecurityFilterChain` DSL; pre-lambda config is gone.
6. **Flyway 12** — check `flyway.` property names, some moved.
7. **Boot 4 module split** — `spring-boot` was broken into finer modules and some starters gained
   new names (`spring-boot-starter-webmvc` alongside `spring-boot-starter-web`; both resolve at
   4.1.0). Use the new names in new code.
8. **JSpecify null-safety** across Spring 7 APIs. If you enable strict null checking in your IDE you
   will see real warnings. Good — leave them on.
9. `sun.misc.Unsafe` memory-access methods now warn loudly on JDK 25. Only affects old libraries;
   nothing in this stack should trip it.

---

## 5. Build file (Gradle Kotlin DSL)

```kotlin
// build.gradle.kts
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.sammalani"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

extra["springModulithVersion"] = "2.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")   // email/SMS templates

    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa") // event publication registry
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")  // verify latest for Boot 4

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        // Modulith is NOT in the Boot 4.1 BOM — import it explicitly
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

tasks.withType<Test> { useJUnitPlatform() }
```

`-Werror` from day one. It costs nothing now and is impossible to add later.

---

## 6. The AI dependency — keep it thin

You need exactly two AI capabilities (§2.4 of the design doc): unstructured text → structured
profile draft, and duplicate-candidate scoring. Both are single request/response calls.

Recommendation: **skip Spring AI initially.** Write one `AiExtractionClient` interface in the `ai`
module with a `RestClient` implementation calling the Anthropic Messages API directly. Reasons:

- Two prompts don't justify a framework, and Spring AI's abstractions are aimed at RAG/agent
  workloads you don't have.
- You want a hard seam here anyway, so the rest of the codebase never imports a vendor type — the
  module boundary is the valuable part, not the SDK.
- Structured output is a tool-use call returning JSON that you map to a record. That's ~80 lines.

Call it from a `@Scheduled` outbox consumer, not inline in a request, so a slow or failing model
never blocks an ambassador mid-phone-call. Cache by input hash in Redis — ambassadors will paste
the same WhatsApp blob more than once.

If you later add semantic search over memories/stories, revisit Spring AI + `pgvector` then.

---

## 7. Runtime / deployment

**Container base image** — build with the JDK, run on the JRE:

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="\
  -XX:+UseCompactObjectHeaders \
  -XX:MaxRAMPercentage=70 \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/dumps"
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

Use `-XX:MaxRAMPercentage`, never a fixed `-Xmx`, so the container respects whatever the VPS gives it.

**AOT cache (JEP 514/515)** — optional, worth it if redeploys annoy you. JDK 25 made this one step:

```bash
# training run against a warmed-up instance
java -XX:AOTCacheOutput=app.aot -jar app.jar
# production
java -XX:AOTCache=app.aot -jar app.jar
```

Typically cuts Spring Boot startup meaningfully. Regenerate the cache on every deploy — a stale
cache is silently ignored, not fatal.

**Native image (GraalVM)?** No. It would cut memory and startup, but it costs you long build times,
reflection configuration, and a class of runtime surprises — in exchange for savings that don't
matter on a service that restarts weekly. Revisit never, probably.

---

## 8. Local dev setup

```bash
# docker-compose.yml services: postgres:17, redis:7, mailpit
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
```

Use **Testcontainers with `@ServiceConnection`** for integration tests so tests never depend on
compose being up:

```java
@SpringBootTest
@Testcontainers
class RegistrationFlowTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17-alpine");
}
```

And one test that keeps the architecture honest — this is the whole point of Modulith:

```java
@Test
void modulesRespectBoundaries() {
    ApplicationModules.of(AlumniApplication.class).verify();
}
```

Wire it into CI. The day someone (including you, at 2am in March 2027) reaches from `events` into
`directory`'s repository, the build fails.

---

## 9. Summary of what changed from the original design

| | Was | Now |
|---|---|---|
| Java | 21 | **25 LTS** |
| Spring Boot | 3.3 | **4.1.0** |
| Spring Framework | 6.x | 7.0.8 |
| Hibernate | 6.x | 7.4.1 |
| Jackson | 2.x | **3.1.4** (new package) |
| JUnit | 5 | **6.0.3** |
| Testcontainers | 1.x | **2.0.5** |
| Flyway | 10.x | 12.4.0 |
| Modulith | 1.x | **2.1.0** (separate BOM) |
| Build | — | Gradle 9.6.1 Kotlin DSL |
| Mapping | MapStruct | plain records (MapStruct is beta-only) |
| AI | Spring AI | thin `RestClient` behind an interface |

Architecture is unchanged: modular monolith, Postgres, Redis, transactional outbox, PWA frontend.
Java 25 makes it cheaper to run and simpler to write. It does not change any of the decisions in
`00-SYSTEM-DESIGN.md`, and none of them depended on the Java version.
