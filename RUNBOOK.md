# Runbook — Distributed Document Search Service

> **Service:** `distributed-document-search` | **Port:** `8080` | **Version:** 1.0

---

## Table of Contents

1. [Quick Reference](#1-quick-reference)
2. [Prerequisites](#2-prerequisites)
3. [Local Development Setup](#3-local-development-setup)
4. [Running the Application](#4-running-the-application)
5. [Running Tests](#5-running-tests)
6. [Docker Setup](#6-docker-setup)
7. [API Usage Guide](#7-api-usage-guide)
8. [Configuration Reference](#8-configuration-reference)
9. [Health Monitoring](#9-health-monitoring)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Quick Reference

| Task | Command |
|------|---------|
| Start app | `mvn spring-boot:run` |
| Run all tests | `mvn test` |
| Build JAR | `mvn clean package -DskipTests` |
| Run JAR | `java -jar target/DistributedDocumentSearch-1.0-SNAPSHOT.jar` |
| Docker build + run | `docker-compose up --build` |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Health check | `curl http://localhost:8080/health` |
| OpenAPI spec | http://localhost:8080/v3/api-docs |

---

## 2. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java (JDK) | 17+ | [Adoptium](https://adoptium.net) or `brew install openjdk@17` |
| Maven | 3.6.3+ | `brew install maven` or [maven.apache.org](https://maven.apache.org) |
| Docker | 20.10+ | [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| Docker Compose | 2.x+ | Included with Docker Desktop |
| Git | Any | `brew install git` |

### Verify your environment

```bash
java -version        # Should print: openjdk 17.x.x
mvn -version         # Should print: Apache Maven 3.6.x or higher
docker --version     # Should print: Docker version 20.x.x
```

---

## 3. Local Development Setup

### Clone the repository

```bash
git clone https://github.com/arpitvaish/DistributedDocumentSearch.git
cd DistributedDocumentSearch
```

### Download dependencies

```bash
mvn dependency:go-offline
```

This downloads all Maven dependencies to your local `~/.m2` cache. Subsequent builds work offline.

### Project structure

```
DistributedDocumentSearch/
├── src/
│   ├── main/
│   │   ├── java/org/example/
│   │   │   ├── Main.java                        ← Spring Boot entry point
│   │   │   ├── config/                          ← Tenant context, cache, Swagger
│   │   │   ├── controller/                      ← REST controllers
│   │   │   ├── exception/                       ← Exception classes + global handler
│   │   │   ├── model/                           ← Request/response models
│   │   │   └── service/                         ← Business logic + Lucene + rate limit
│   │   └── resources/
│   │       └── application.yml                  ← Main config
│   └── test/
│       ├── java/org/example/                    ← 122 tests across all layers
│       └── resources/
│           └── application.yml                  ← Test overrides (high rate limit)
├── Dockerfile                                   ← Multi-stage Docker build
├── docker-compose.yml                           ← Single-service compose
├── DESIGN.md                                    ← Architecture & design decisions
├── RUNBOOK.md                                   ← This file
└── pom.xml                                      ← Maven dependencies
```

---

## 4. Running the Application

### Option A: Maven (recommended for development)

```bash
mvn spring-boot:run
```

Expected output:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
Started Main in 0.8 seconds (process running for 0.9)
```

The app starts in under 1 second.

### Option B: JAR

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/DistributedDocumentSearch-1.0-SNAPSHOT.jar
```

### Option C: Custom port or config

```bash
java -jar target/DistributedDocumentSearch-1.0-SNAPSHOT.jar \
  --server.port=9090 \
  --app.rate-limit.requests-per-second=200.0
```

### Verify the app is running

```bash
curl http://localhost:8080/health
```

Expected:
```json
{
  "status": "UP",
  "details": {
    "indexEngine": "UP (Apache Lucene 9.10.0 — embedded)",
    "cache": "UP (Caffeine — in-memory, TTL 5m, max 1000 entries)",
    "rateLimiter": "UP (Guava token bucket)",
    "tenantsIndexed": 0,
    "totalDocuments": 0,
    "activeTenantLimiters": 0
  },
  "timestamp": "2026-05-04T10:30:00"
}
```

---

## 5. Running Tests

### Run all tests

```bash
mvn test
```

Expected result: **122 tests, 0 failures**

```
Tests run: 122, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Run a specific test class

```bash
# Unit tests only
mvn test -Dtest=LuceneSearchServiceTest
mvn test -Dtest=DocumentServiceTest
mvn test -Dtest=RateLimiterServiceTest
mvn test -Dtest=TenantInterceptorTest
mvn test -Dtest=DocumentParserServiceTest

# Controller tests
mvn test -Dtest=DocumentControllerTest
mvn test -Dtest=SearchControllerTest
mvn test -Dtest=HealthControllerTest

# Integration tests
mvn test -Dtest=DocumentSearchIntegrationTest
mvn test -Dtest=FileUploadSearchIntegrationTest
```

### Run tests with verbose output

```bash
mvn test -Dsurefire.useFile=false
```

### Test coverage breakdown

| Test Class | Tests | What it covers |
|---|---|---|
| `LuceneSearchServiceTest` | 19 | Indexing, search, deletion, pagination, tenant isolation, field queries, wildcards |
| `DocumentServiceTest` | 24 | CRUD orchestration, UUID generation, cache read/write, rate limiting, validation |
| `RateLimiterServiceTest` | 7 | Token bucket, per-tenant isolation, rate enforcement, stats |
| `TenantInterceptorTest` | 9 | Header validation, ThreadLocal lifecycle, blank/missing header rejection |
| `DocumentParserServiceTest` | 8 | Text/HTML/RTF extraction, MIME detection, empty file, unsupported type, title derivation |
| `DocumentControllerTest` | 21 | HTTP status codes, request validation, tenant isolation, upload endpoint |
| `SearchControllerTest` | 12 | Query syntax, pagination, tenant scoping, edge cases |
| `HealthControllerTest` | 6 | Status reporting, no-auth access, live document counts |
| `DocumentSearchIntegrationTest` | 7 | Full CRUD flow, multi-tenant isolation, caching, relevance scoring |
| `FileUploadSearchIntegrationTest` | 9 | File upload + search end-to-end, HTML tag stripping, title override, ranking, tenant isolation, error cases |

---

## 6. Docker Setup

### Build and start with Docker Compose

```bash
docker-compose up --build
```

This will:
1. Compile the source using `maven:3.9.6-eclipse-temurin-17`
2. Build a lean runtime image using `eclipse-temurin:17-jre-alpine`
3. Start the container on port `8080`
4. Run a health check every 30 seconds

### Verify the container is healthy

```bash
docker ps
# STATUS column should show: Up X seconds (healthy)
```

### Tail logs

```bash
docker-compose logs -f
```

### Stop the service

```bash
docker-compose down
```

### Build image separately

```bash
docker build -t document-search:latest .
docker run -p 8080:8080 document-search:latest
```

### Override configuration at runtime

```bash
docker run -p 8080:8080 \
  -e APP_RATE_LIMIT_REQUESTS_PER_SECOND=500.0 \
  document-search:latest
```

---

## 7. API Usage Guide

### Step 1 — Open Swagger UI

Navigate to: **http://localhost:8080/swagger-ui/index.html**

Click **Authorize** (top right) and enter your tenant ID (e.g. `tenant-a`). This sets the `X-Tenant-ID` header for all requests in the UI.

---

### 7.1 Index a Document

```bash
curl -X POST http://localhost:8080/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "title": "Annual Financial Report 2024",
    "content": "Q4 revenue exceeded expectations. Total revenue grew 23% YoY.",
    "metadata": {
      "author": "Alice Johnson",
      "category": "finance",
      "year": "2024"
    }
  }'
```

Response `201 Created`:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "tenant-a",
  "title": "Annual Financial Report 2024",
  "content": "Q4 revenue exceeded expectations...",
  "metadata": { "author": "Alice Johnson", "category": "finance", "year": "2024" },
  "createdAt": "2026-05-04T10:30:00",
  "updatedAt": "2026-05-04T10:30:00"
}
```

**Save the `id` field — you'll need it for retrieval and deletion.**

---

### 7.2 Search Documents

```bash
curl "http://localhost:8080/search?q=financial+report&page=0&size=10" \
  -H "X-Tenant-ID: tenant-a"
```

Response `200 OK`:
```json
{
  "hits": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Annual Financial Report 2024",
      "contentSnippet": "Q4 revenue exceeded expectations. Total revenue grew 23% YoY.",
      "score": 1.4523,
      "createdAt": "2026-05-04T10:30:00",
      "metadata": { "author": "Alice Johnson" }
    }
  ],
  "total": 1,
  "query": "financial report",
  "tenantId": "tenant-a",
  "tookMs": 18,
  "page": 0,
  "pageSize": 10
}
```

#### Advanced query examples

```bash
# Field-specific
curl "http://localhost:8080/search?q=title:annual" -H "X-Tenant-ID: tenant-a"

# Boolean AND
curl "http://localhost:8080/search?q=finance+AND+revenue" -H "X-Tenant-ID: tenant-a"

# Wildcard
curl "http://localhost:8080/search?q=micro*" -H "X-Tenant-ID: tenant-a"

# Fuzzy
curl "http://localhost:8080/search?q=finanse~" -H "X-Tenant-ID: tenant-a"

# Phrase
curl "http://localhost:8080/search?q=%22annual+report%22" -H "X-Tenant-ID: tenant-a"

# Pagination
curl "http://localhost:8080/search?q=report&page=1&size=5" -H "X-Tenant-ID: tenant-a"
```

---

### 7.3 Retrieve a Document by ID

```bash
curl http://localhost:8080/documents/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-Tenant-ID: tenant-a"
```

Response `200 OK`: full `Document` JSON (same as index response).

---

### 7.4 Delete a Document

```bash
curl -X DELETE http://localhost:8080/documents/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-Tenant-ID: tenant-a"
```

Response `204 No Content` (empty body).

---

### 7.5 Health Check

```bash
curl http://localhost:8080/health
```

No `X-Tenant-ID` header required.

---

### 7.6 Upload a File

Upload a file and have its text extracted and indexed automatically. Supports TXT, HTML, RTF, PDF, DOCX, XLSX, PPTX, ODT.

```bash
# Upload a plain text file (title derived from filename: "kafka overview")
curl -X POST http://localhost:8080/documents/upload \
  -H "X-Tenant-ID: tenant-a" \
  -F "file=@kafka_overview.txt"

# Upload with a custom title
curl -X POST http://localhost:8080/documents/upload \
  -H "X-Tenant-ID: tenant-a" \
  -F "file=@report_q4.txt" \
  -F "title=Q4 2024 Financial Summary"

# Upload an HTML file (tags are stripped; only visible text is indexed)
curl -X POST http://localhost:8080/documents/upload \
  -H "X-Tenant-ID: tenant-a" \
  -F "file=@guide.html"
```

Response `201 Created`:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "tenant-a",
  "title": "kafka overview",
  "content": "Apache Kafka is a distributed event streaming platform...",
  "createdAt": "2026-05-04T10:30:00",
  "updatedAt": "2026-05-04T10:30:00"
}
```

After uploading, search the document content immediately:

```bash
curl "http://localhost:8080/search?q=streaming+analytics" \
  -H "X-Tenant-ID: tenant-a"
```

---

### 7.7 Multi-Tenant Demo

Index the same content as two different tenants — they cannot see each other's data:

```bash
# Tenant A indexes a document
curl -X POST http://localhost:8080/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: company-x" \
  -d '{"title": "Secret Strategy", "content": "confidential roadmap"}'

# Tenant B searches — returns 0 results (complete isolation)
curl "http://localhost:8080/search?q=confidential" \
  -H "X-Tenant-ID: company-y"
```

---

## 8. Configuration Reference

All settings are in `src/main/resources/application.yml`.

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP listen port |
| `app.rate-limit.requests-per-second` | `100.0` | Max requests per second per tenant |
| `spring.servlet.multipart.max-file-size` | `10MB` | Maximum size of a single uploaded file |
| `spring.servlet.multipart.max-request-size` | `12MB` | Maximum total size of a multipart request |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI redirect path |
| `springdoc.api-docs.path` | `/v3/api-docs` | OpenAPI JSON endpoint |

### Environment Variable Overrides

Spring Boot maps `APP_RATE_LIMIT_REQUESTS_PER_SECOND` → `app.rate-limit.requests-per-second`. Use environment variables in Docker/Kubernetes:

```bash
APP_RATE_LIMIT_REQUESTS_PER_SECOND=500.0
SERVER_PORT=9090
```

### Test Configuration

`src/test/resources/application.yml` overrides the rate limit to `1,000,000 RPS` for integration tests so multi-step test flows don't trigger 429 responses. This file is only loaded during `mvn test`.

---

## 9. Health Monitoring

### Health endpoint

```
GET /health
```

Returns component status and live metrics:

| Field | Description |
|---|---|
| `status` | `"UP"` when all components healthy |
| `details.indexEngine` | Lucene version and mode |
| `details.cache` | Cache configuration summary |
| `details.rateLimiter` | Rate limiter type |
| `details.tenantsIndexed` | Number of distinct tenants with documents |
| `details.totalDocuments` | Sum of all documents across all tenants |
| `details.activeTenantLimiters` | Number of tenants that have made requests |
| `timestamp` | ISO-8601 timestamp of the health check |

### Actuator endpoint

Spring Boot Actuator is included:

```bash
curl http://localhost:8080/actuator
```

### Key indicators to watch

| Indicator | Healthy | Warning |
|---|---|---|
| HTTP `GET /health` response | `200 {"status":"UP"}` | Non-200 or `status != "UP"` |
| Search latency (`tookMs`) | < 50 ms | > 200 ms |
| 429 rate | 0% of requests | > 5% of requests |
| App startup time | < 2 seconds | > 10 seconds |
| JVM heap usage | < 70% | > 90% |

---

## 10. Troubleshooting

### App fails to start — "Port 8080 already in use"

```bash
# Find and kill the process using port 8080
lsof -ti:8080 | xargs kill -9
```

Or change the port:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

---

### `mvn spring-boot:run` fails — "requires Maven version 3.6.3"

The Maven version installed is too old. The `pom.xml` pins `maven-compiler-plugin` to `3.12.1` to support Maven 3.6.0+. If you see this error, your Maven is below 3.6.0:

```bash
mvn -version  # check version
brew upgrade maven  # upgrade
```

---

### Java 11 / Java 8 — "class file has wrong version"

Spring Boot 3 requires Java 17+. Check your `JAVA_HOME`:

```bash
java -version
# Must print: openjdk 17.x.x or higher
```

If multiple JDKs are installed:
```bash
export JAVA_HOME=$(/usr/libenv/java_home -v 17)
```

---

### 400 Bad Request — "Missing required header: X-Tenant-ID"

All endpoints except `/health` require the `X-Tenant-ID` header.

```bash
# Wrong
curl http://localhost:8080/search?q=test

# Correct
curl "http://localhost:8080/search?q=test" -H "X-Tenant-ID: my-tenant"
```

---

### 400 Bad Request — "Invalid query syntax"

Lucene query parser rejected the query string. Common causes:

| Cause | Example | Fix |
|---|---|---|
| Unbalanced quotes | `"open quote` | Close the quote: `"open quote"` |
| Leading wildcard | `*term` | Lucene doesn't support leading wildcards by default |
| Unbalanced parentheses | `(a AND b` | Close: `(a AND b)` |

---

### 429 Too Many Requests

You have exceeded the per-tenant rate limit (default: 100 requests/second).

- Wait 1 second and retry (see `Retry-After: 1` response header)
- Increase the limit: `--app.rate-limit.requests-per-second=500`
- Check if a bug is causing a request loop in your client

---

### Search returns 0 results after indexing

Possible causes:

1. **Wrong tenant** — Check the `X-Tenant-ID` you indexed with matches the one you search with (case-sensitive).
2. **Query mismatch** — Lucene uses the `StandardAnalyzer` which lowercases and tokenises. Try lowercase query terms.
3. **Recently indexed** — The NRT reader refresh is triggered immediately after each write. If search still fails, try a full restart.

Debug: retrieve the document by ID directly to confirm it was indexed:
```bash
curl http://localhost:8080/documents/{id} -H "X-Tenant-ID: your-tenant"
```

---

### Data is lost after restart

**This is expected behaviour for the prototype.** The Lucene indexes and document store are fully in-memory. Restarting the JVM clears all data.

For persistent storage, the production architecture uses `FSDirectory` (disk-based Lucene) backed by a durable database. See `DESIGN.md §13` for the production gap analysis.

---

### Docker container stuck in "starting" health check

The container health check runs:
```
wget -q -O - http://localhost:8080/health
```

If it stays in "starting" status for > 40 seconds, check the logs:
```bash
docker-compose logs document-search
```

Common causes:
- Port 8080 conflict inside the container
- JVM OOM (increase Docker memory limit to 512m+)
- Missing `wget` in base image (the `eclipse-temurin:17-jre-alpine` image includes it)

---

### 415 Unsupported Media Type — "Unsupported file type: application/zip"

The uploaded file's MIME type is not supported by the parser. Accepted formats: TXT, HTML, RTF, PDF, DOCX, XLSX, PPTX, ODT.

```bash
# This will fail
curl -X POST http://localhost:8080/documents/upload \
  -H "X-Tenant-ID: tenant-a" \
  -F "file=@archive.zip"

# Convert to a supported format first, or extract the text manually and POST to /documents
```

---

### 413 Payload Too Large — "Maximum upload size exceeded"

The uploaded file exceeds the configured limit (default: 10 MB per file, 12 MB total request).

To increase the limit, add to `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB
```

Or pass as a JVM argument:

```bash
java -jar target/DistributedDocumentSearch-1.0-SNAPSHOT.jar \
  --spring.servlet.multipart.max-file-size=50MB \
  --spring.servlet.multipart.max-request-size=55MB
```

---

### Running out of memory with large document sets

The prototype stores all documents in-memory. For production loads, tune the JVM heap:

```bash
java -Xmx2g -Xms512m -jar target/DistributedDocumentSearch-1.0-SNAPSHOT.jar
```

Or in Docker Compose:
```yaml
services:
  document-search:
    environment:
      - JAVA_OPTS=-Xmx2g -Xms512m
```

---

*For architecture decisions and design trade-offs, see [DESIGN.md](./DESIGN.md).*
