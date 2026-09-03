# LeMarketJames

A full-stack web application built with **Spring Boot 3** (Java 21) backend, **Angular 22** frontend, and **PostgreSQL 16** database. Includes Docker and Docker Compose support for containerized deployment.

## Project Structure

See [AGENTS.md](AGENTS.md) for the full conventions doc (package/folder rules, build & test commands). Summary:

```
LeMarketJames/
├── apps/
│   ├── backend/                   # Java/Spring Boot backend (feature-based packages)
│   │   ├── src/main/java/com/lemarketjames/
│   │   │   ├── Main.java           # Spring Boot application entry point
│   │   │   ├── Greeter.java        # Sample service
│   │   │   ├── auth/               # Auth feature: controller, service, dto/, security/
│   │   │   ├── common/             # Shared code used by multiple features
│   │   │   └── config/             # Cross-cutting configuration (SecurityConfig, etc.)
│   │   ├── src/main/resources/
│   │   │   └── application.properties  # Spring Boot configuration
│   │   ├── src/test/java/         # JUnit test suite, mirrors main package layout
│   │   ├── pom.xml                # Maven configuration
│   │   └── Dockerfile             # Backend container image definition
│   └── frontend/                  # Angular 22 frontend (formerly lemarket-ui/)
│       ├── src/
│       │   ├── index.html         # HTML entry point
│       │   ├── main.ts            # Angular bootstrap
│       │   ├── app/
│       │   │   ├── app.ts         # Root component
│       │   │   ├── app.routes.ts  # Route definitions
│       │   │   ├── core/          # App-wide singletons: auth state, interceptors
│       │   │   ├── shared/        # Reusable presentational components/pipes/models
│       │   │   └── features/auth/ # Routed, feature-specific UI
│       │   └── styles.css         # Global styles
│       ├── package.json           # npm dependencies and scripts
│       ├── angular.json           # Angular CLI config
│       ├── nginx.conf             # SPA routing config for the container
│       └── Dockerfile             # Frontend container image definition
├── database/                      # Database schemas (raw SQL, no migration tool)
│   └── schema/                    # Numbered, ordered SQL files applied in order
│       ├── 001_core_schema.sql
├── docker-compose.yml             # 3 services: frontend (4200), backend (8081), db (5432)
├── Jenkinsfile                    # CI/CD pipeline
├── AGENTS.md                      # Conventions for contributors and AI agents
└── README.md                      # This file
```

## Prerequisites

Before running the application, ensure you have the following installed:

| Component | Version | Command to Verify |
|-----------|---------|-------------------|
| Java | 21 | `java -version` |
| Maven | 3.9.9 or higher | `mvn -version` |
| Node.js | 11.16.0 or higher | `node -version` |
| npm | 11.16.0 or higher | `npm -version` |
| Docker | required (runs PostgreSQL locally; also used for containerized deployment) | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| psql / a Postgres client | to apply `database/schema/*.sql` | `psql --version` |

**PostgreSQL is required even for local (non-Docker) backend development.** The backend persists to Postgres via JPA and fails to start without a reachable database — there is no in-memory fallback.

## Running the Application

Choose one of the three methods below based on your use case:

### Method 1: Local Development (Maven + npm)

This is the recommended approach for active development, as it provides hot-reload for both backend and frontend.

**Database Setup (required before starting the backend):**

1. Start just the `db` service (Postgres 16) in the background:
   ```bash
   docker compose up -d db
   ```

2. Apply the schema files **in numeric order** (only needed once, or after `docker compose down -v`):
   ```bash
   psql -h localhost -U paysprint -d paysprint -f database/schema/001_core_schema.sql
   psql -h localhost -U paysprint -d paysprint -f database/schema/002_registration_fixes.sql
   ```
   Default password is `changeme` (see `docker-compose.yml`). Schema changes always land in new numbered files — never edit `001_...`/`002_...` in place.

**Backend Setup (Spring Boot on port 8081):**

1. Navigate to the backend project:
   ```bash
   cd apps/backend
   ```

2. Install Java dependencies with Maven:
   ```bash
   mvn clean install
   ```

3. Start the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
   The backend will be available at `http://localhost:8081`. It connects to `jdbc:postgresql://localhost:5432/paysprint` by default (see `apps/backend/src/main/resources/application.properties`); override with the `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME`/`SPRING_DATASOURCE_PASSWORD` env vars if needed.

4. Confirm the backend can reach the database:
   ```bash
   curl http://localhost:8081/actuator/health
   ```
   Expect `{"status":"UP"}`. `{"status":"DOWN"}` usually means the `db` container isn't running or the schema hasn't been applied yet.

**Frontend Setup (Angular on port 4200):**

1. Navigate to the frontend directory:
   ```bash
   cd apps/frontend
   ```

2. Install frontend dependencies:
   ```bash
   npm install
   ```

3. Start the Angular development server:
   ```bash
   npm start
   ```
   The frontend will be available at `http://localhost:4200`

4. Access the application:
   - **Registration Form:** `http://localhost:4200/register`
   - **Login:** `http://localhost:4200/login`
   - **Home:** `http://localhost:4200`

**Frontend Features:**
- **Material Design UI:** Built with Angular Material 22 for professional appearance
- **Registration Form:** Comprehensive registration with validation
  - Personal Information (first name, middle name, last name)
  - Address (street, city, state dropdown, ZIP)
  - Identity (SSN, date of birth with date picker)
  - Financial (initial deposit, investment experience level)
  - Contact (email, phone with auto-formatting)
  - Security (password with show/hide toggle)
  - All fields have real-time validation with Material error messages
- **State-based Management:** Using Angular signals for reactive state updates
- **Responsive Design:** Mobile, tablet, and desktop layouts

**Note:** The frontend itself has no database dependency. But to exercise registration/login end-to-end, the backend must be running on `http://localhost:8081` *and* connected to a schema-initialized Postgres instance (see Database Setup above).

---

### Method 2: Docker Build & Run

This method builds a single Docker image and runs the application in a container.

1. Build the Docker image:
   ```bash
   docker build -t le-market-james:latest apps/backend
   ```

2. Run the container:
   ```bash
   docker run -p 8081:8081 --rm le-market-james
   ```
   The application will be available at `http://localhost:8081`

3. To stop the container, press `Ctrl+C` in the terminal.

**Note:** This method does not include the PostgreSQL database. The application will start but may have limited functionality. To use the database, see Method 3.

---

### Method 3: Docker Compose (Full Stack with Database)

This method spins up the complete stack: Angular frontend + Spring Boot backend + PostgreSQL database.

1. Start all services:
   ```bash
   docker compose up -d --build
   ```
   - Angular frontend: `http://localhost:4200`
   - Spring Boot backend: `http://localhost:8081`
   - PostgreSQL database: `localhost:5432`

2. Apply the schema (schema application is manual, not automated — see `database/README.md`):
   ```bash
   psql -h localhost -U paysprint -d paysprint -f database/schema/001_core_schema.sql
   psql -h localhost -U paysprint -d paysprint -f database/schema/002_registration_fixes.sql
   ```

3. Confirm the backend is up and connected to the database:
   ```bash
   curl http://localhost:8081/actuator/health
   ```

4. View logs:
   ```bash
   docker compose logs -f backend
   ```

5. Stop all services:
   ```bash
   docker compose down
   ```

**Database Credentials:**
- Username: `paysprint`
- Password: `changeme` (default; override with environment variable)

To use a custom database password, set the `DB_PASSWORD` environment variable:
```bash
DB_PASSWORD=your_secure_password docker compose up -d --build
```

**Database Port:** PostgreSQL is exposed on `localhost:5432` for use with database tools (e.g., pgAdmin, DBeaver).

---

## Testing

### Backend Tests (Java/JUnit)

Run all JUnit tests:

```bash
cd apps/backend
mvn test
```

Test results are generated in `apps/backend/target/surefire-reports/`. Successful tests confirm the Spring Boot application and authentication logic are functioning correctly.

### Frontend Tests (TypeScript/Vitest)

Run all frontend tests:

```bash
cd apps/frontend
npm test
```

---

## API & Frontend Access

Once the application is running (via any of the three methods), you can access:

| Service | URL | Purpose |
|---------|-----|---------|
| **Angular Frontend** | `http://localhost:4200` | User interface (when using Method 1) |
| **Registration Page** | `http://localhost:4200/register` | User registration with Material Design form |
| **Spring Boot Backend** | `http://localhost:8081` | REST API endpoints |
| **Auth Register API** | `POST http://localhost:8081/api/auth/register` | Register new user |
| **Auth Login API** | `POST http://localhost:8081/api/auth/login` | User login |
| **Health Check** | `GET http://localhost:8081/actuator/health` | Backend + DB liveness (`UP`/`DOWN`) |
| **PostgreSQL Database** | `localhost:5432` | Database server |

**Frontend Routes:**
- `/register` - User registration page with comprehensive form
- `/login` - User login

See [apps/backend/src/main/java/com/lemarketjames/auth/AuthController.java](apps/backend/src/main/java/com/lemarketjames/auth/AuthController.java) for complete API endpoint definitions.

**Form Validation:**
All form validation is performed client-side using Zod schema validation before submission to the backend. See [apps/frontend/src/app/features/auth/register/register.schema.ts](apps/frontend/src/app/features/auth/register/register.schema.ts) for validation rules.

---

## Troubleshooting

### Port Already in Use

If you see an error like "Address already in use" or "Port X is already allocated":

- **Port 8081 (Spring Boot):** Check if another service is using it:
  ```bash
  netstat -ano | findstr :8081
  ```
  Kill the process or choose a different port in `apps/backend/src/main/resources/application.properties`.

- **Port 4200 (Angular):** Start the dev server on a different port:
  ```bash
  ng serve --port 4300
  ```

- **Port 5432 (PostgreSQL):** Choose a different port in `docker-compose.yml` or stop other PostgreSQL instances.

### Java Version Mismatch

The application requires **Java 21**. If you have multiple Java versions installed:

```bash
java -version
```

If this shows Java 11 or 17, update your `JAVA_HOME` environment variable to point to Java 21:
```bash
# Windows
setx JAVA_HOME "C:\Program Files\Java\jdk-21"
```

Then restart your terminal and verify:
```bash
java -version
```

### Maven Build Fails

- Clear Maven cache and rebuild:
  ```bash
  cd apps/backend
  mvn clean install
  ```

- Ensure you're in `apps/backend` (where `pom.xml` is located).

### npm Install Fails

- Use the npm ci (clean install) command for reproducible builds:
  ```bash
  cd apps/frontend
  npm ci
  ```

- Clear npm cache:
  ```bash
  npm cache clean --force
  ```

### Database Connection Refused (Docker Compose or Local Dev)

- Check `GET http://localhost:8081/actuator/health` first — `{"status":"DOWN"}` means the backend can't reach Postgres.

- Verify all services are running (Docker Compose):
  ```bash
  docker compose ps
  ```

- For local dev (Method 1), confirm the `db` container is up:
  ```bash
  docker compose up -d db
  docker compose logs db
  ```

- Confirm the schema was applied — `spring.jpa.hibernate.ddl-auto=validate` means the backend refuses to start if expected tables/columns are missing:
  ```bash
  psql -h localhost -U paysprint -d paysprint -c "\dt"
  ```
  If tables are missing, re-run the `psql -f database/schema/...sql` commands from the Database Setup section, in order.

- Ensure PostgreSQL has time to start (it may take 10-15 seconds):
  ```bash
  docker compose logs backend | grep "Hibernate" | head -1
  ```

- Rebuild from scratch:
  ```bash
  docker compose down -v
  docker compose up -d --build
  ```

### Container Exits Immediately (Docker)

Check the logs:
```bash
docker logs le-market-james
```

Common causes:
- Java 21 not available in the container (check Dockerfile `FROM` base image)
- Database not running when the app expects it (use Docker Compose, not standalone `docker run`)
- Missing `application.properties` configuration

---

## CI/CD Pipeline

This repository includes a **Jenkins Pipeline** (`Jenkinsfile`) that:

1. Runs Maven tests (`mvn test`)
2. Builds the Docker image
3. Runs the containerized application
4. Verifies the output
5. Cleans up resources

The Jenkins agent requires:
- Java 21
- Maven 3.9.9+
- Docker daemon
- Permission to run Docker commands

To set up the pipeline, create a **Pipeline job** in Jenkins and point it to this repository with "Pipeline script from SCM" selected.
