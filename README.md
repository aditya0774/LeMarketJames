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
│   └── schema/001_core_schema.sql # PostgreSQL schema
├── docker-compose.yml             # 3 services: frontend (4200), backend (8081), db (5432)
├── Jenkinsfile                    # CI/CD pipeline
├── AGENTS.md                      # Conventions for contributors and AI agents
└── README.md                      # This file
```

## Architecture

This section describes the current system architecture. As new features are added, this will be updated to reflect changes.

### System Topology

LeMarketJames is a **3-tier distributed architecture** with three independent services communicating over HTTP:

```
┌─────────────────┐            ┌─────────────────┐            ┌─────────────────┐
│  Angular        │            │  Spring Boot    │            │  PostgreSQL     │
│  Frontend       │  --REST->  │  Backend        │            │  Database       │
│  (port 4200)    │ <-Cookies- │  (port 8081)    │<---JDBC--->│  (port 5432)    │
│                 │            │                 │            │                 │
└─────────────────┘            └─────────────────┘            └─────────────────┘
```

**Key Characteristics:**
- **Stateless backend:** No session state; authentication via JWT tokens
- **Unidirectional data flow:** Browser → Backend → Database (only backend talks to database)
- **Containerized:** Each service can run independently or together via Docker Compose
- **Separation of concerns:** Frontend handles UI/UX; backend handles business logic and security; database stores persistent state

### Authentication Flow

The application implements **JWT (JSON Web Token) based authentication** with HTTP-only cookies:

1. **Registration:** User submits credentials and profile info to `/api/auth/register`
   - Backend validates input, hashes password with BCrypt, stores user in database
   - Returns success/error response

2. **Login:** User submits username/password to `/api/auth/login`
   - Backend verifies credentials, generates JWT token
   - Returns JWT token in an HTTP-only, secure cookie
   - Token includes username as subject, issued-at time, and expiration time

3. **Authenticated Requests:** Subsequent requests include JWT cookie automatically
   - `JwtAuthenticationFilter` extracts token from cookie
   - `JwtService` validates token signature and expiration
   - If valid, request is authenticated; invalid tokens return 401 Unauthorized

4. **Logout:** Clears the JWT cookie on the client side

### Technology Stack

| Layer | Technology | Purpose | Version |
|-------|-----------|---------|---------|
| **Frontend** | Angular | Reactive UI framework | 22 |
| | Angular Material | Design system and component library | 22 |
| | TypeScript | Type-safe JavaScript | Latest |
| | Zod | Schema validation (client-side) | Latest |
| **Backend** | Spring Boot | Web framework and server | 3 |
| | Spring Security | Authentication and authorization | 6 |
| | JWT (io.jsonwebtoken) | Token generation and validation | Latest |
| | Maven | Dependency management and build | 3.9.9+ |
| | JUnit 5 | Unit and integration testing | 5 |
| **Database** | PostgreSQL | Relational database | 16 |
| | Raw SQL | Schema definition (no migration tool) | SQL |
| **Deployment** | Docker | Container runtime | Latest |
| | Docker Compose | Multi-container orchestration | v2+ |

### Current Features & Endpoints

**Authentication Module** (`com.lemarketjames.auth`):
- `POST /api/auth/register` — Register new user with profile information
- `POST /api/auth/login` — Authenticate user and receive JWT token
- `POST /api/auth/logout` — Clear authentication cookie
- `GET /api/auth/me` — Retrieve current authenticated user (requires valid JWT)

**Data Models:**
- User registration captures: username, password, email, full name, address, SSN, date of birth, initial deposit, investment experience, phone number
- Passwords stored as BCrypt hashes with salt
- User data stored in PostgreSQL (schema: `database/schema/001_core_schema.sql`)

### Design Principles

- **Feature-based organization:** Code organized by business capability (e.g., `auth/` package contains all auth-related classes)
- **Separation of layers:** Controllers handle HTTP; services handle business logic; DTOs transfer data
- **Security first:** Passwords hashed with BCrypt; JWT tokens signed; HTTP-only cookies prevent XSS attacks
- **Scalability:** Stateless backend allows horizontal scaling; database can be scaled independently

---

## Prerequisites

Before running the application, ensure you have the following installed:

| Component | Version | Command to Verify |
|-----------|---------|-------------------|
| Java | 21 | `java -version` |
| Maven | 3.9.9 or higher | `mvn -version` |
| Node.js | 11.16.0 or higher | `node -version` |
| npm | 11.16.0 or higher | `npm -version` |
| Docker | (optional, for containerized deployment) | `docker --version` |
| Docker Compose | v2+ (optional, for full stack) | `docker compose version` |

## Running the Application

Choose one of the three methods below based on your use case:

### Method 1: Local Development (Maven + npm)

This is the recommended approach for active development, as it provides hot-reload for both backend and frontend.

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
   The backend will be available at `http://localhost:8081`

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

**Note:** The database is not required for local frontend development. If you need to test the full registration flow with backend validation, ensure the Spring Boot backend is also running on `http://localhost:8081`

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

2. View logs:
   ```bash
   docker compose logs -f backend
   ```

3. Stop all services:
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
| **PostgreSQL Database** | `localhost:5432` | Database server (Method 3 only) |

**Frontend Routes:**
- `/register` - User registration page with comprehensive form
- `/login` - User login (if implemented)

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

### Database Connection Refused (Docker Compose)

- Verify all services are running:
  ```bash
  docker compose ps
  ```

- Check if the database is healthy:
  ```bash
  docker compose logs db
  ```

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

## Javadocs

The backend API is documented using Javadoc comments. To generate and view the documentation:

### Generating Updated Javadoc

1. Navigate to the backend directory:
   ```bash
   cd apps/backend
   ```

2. Generate updated Javadoc:
   ```bash
   mvn javadoc:javadoc
   ```

3. Documentation will be generated at `apps/backend/target/reports/apidocs/index.html`

### Viewing Javadoc in Your Browser

**Windows:** Run the provided script from the project root:
```bash
open-javadoc.bat
```

**Mac/Linux:** Run the provided script from the project root:
```bash
./open-javadoc.sh
```

The scripts will automatically open the Javadoc in your default browser.

## ER Diagram

Will be added later to GitHub later but is completed, see Amara for access
