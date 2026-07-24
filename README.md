# DevBraid Backend

A Spring Boot backend application for the DevBraid platform.

## Prerequisites

- **Java 17** or higher
- **Maven 3.8+** (or use the included Maven Wrapper `./mvnw`)
- **Docker & Docker Compose** (for local PostgreSQL database)
- **Git**

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/your-org/devbraid-backend.git
cd devbraid-backend
```

### 2. Set up environment variables

```bash
cp .env.example .env
# Edit .env with your local configuration
```

### 3. Start the database

```bash
docker compose up -d
```

### 4. Run the application

```bash
# Using Maven Wrapper (recommended)
./mvnw spring-boot:run

# Or with explicit profile
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

---

## Environment Configuration

This project uses **Spring Profiles** to manage environment-specific configuration. Configuration is split across
multiple YAML files:

| File                     | Purpose                                                           |
|--------------------------|-------------------------------------------------------------------|
| `application.yml`        | Common configuration shared across all environments               |
| `application-dev.yaml`   | Development-specific settings (local, verbose logging)            |
| `application-stage.yaml` | Staging-specific settings (moderate logging, validation)          |
| `application-prod.yaml`  | Production-specific settings (minimal logging, hardened security) |

### How Profiles Work

1. **Base configuration** (`application.yml`) loads first with common settings.
2. **Profile-specific configuration** (`application-{profile}.yaml`) overrides or extends the base config.
3. Environment variables using `${VAR_NAME}` syntax inject secrets and environment-specific values.

### Activating a Profile

There are several ways to activate a Spring profile:

#### Option 1: Environment Variable (Recommended)

```bash
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

#### Option 2: Command-Line Argument

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=stage"
```

#### Option 3: System Property

```bash
./mvnw spring-boot:run -Dspring.profiles.active=prod
```

#### Option 4: In the `.env` file

```bash
SPRING_PROFILES_ACTIVE=dev
```

---

## Profile Details

### Development (`dev`)

The default profile for local development.

| Setting       | Value                                       |
|---------------|---------------------------------------------|
| Database URL  | `jdbc:postgresql://localhost:5433/devbraid` |
| Server Port   | `8080`                                      |
| Hibernate DDL | `update` (auto-creates/updates tables)      |
| SQL Logging   | Enabled (verbose)                           |
| Swagger UI    | Enabled                                     |
| Actuator      | Enabled                                     |

**Features:**

- Auto-schema updates via Hibernate
- Detailed SQL logging for debugging
- CORS allows multiple local origins (`localhost:3000`, `5173`, `4200`)
- Development JWT secret (for local use only)

### Staging (`stage`)

For QA and pre-production testing.

| Setting       | Value                                |
|---------------|--------------------------------------|
| Database URL  | `${STAGE_DB_URL}` (from environment) |
| Server Port   | `8080`                               |
| Hibernate DDL | `validate` (no auto-changes)         |
| SQL Logging   | Disabled                             |
| Swagger UI    | Enabled                              |
| Actuator      | Health, Info, Metrics                |

**Features:**

- All secrets via environment variables (no defaults)
- Schema validation only (no auto-migration)
- Moderate logging levels
- CORS restricted to staging domain

### Production (`prod`)

For live production deployments.

| Setting       | Value                               |
|---------------|-------------------------------------|
| Database URL  | `${PROD_DB_URL}` (from environment) |
| Server Port   | `8080`                              |
| Hibernate DDL | `validate`                          |
| SQL Logging   | Disabled                            |
| Swagger UI    | Disabled                            |
| Actuator      | Health, Info only (restricted)      |

**Features:**

- All secrets via environment variables (REQUIRED, no defaults)
- Tomcat tuned for production (200 threads, connection pooling)
- Response compression enabled
- HTTPS enforcement and HSTS headers
- Strict CORS (single production domain)
- Structured logging format
- Actuator on separate port (9090)

---

## Environment Variables

### Quick Reference

All environment variables are documented in `.env.example`. Here's a summary of the **required** variables:

#### Common (All Environments)

| Variable                 | Description                                    | Default |
|--------------------------|------------------------------------------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`dev`, `stage`, `prod`) | `dev`   |
| `JWT_SECRET`             | JWT signing secret (256-bit recommended)       | *none*  |

#### Development

All development variables have sensible defaults. Override via `DEV_*` prefixed environment variables.

#### Staging (Required)

| Variable            | Description               |
|---------------------|---------------------------|
| `STAGE_DB_URL`      | PostgreSQL connection URL |
| `STAGE_DB_USERNAME` | Database username         |
| `STAGE_DB_PASSWORD` | Database password         |
| `STAGE_JWT_SECRET`  | JWT signing secret        |

#### Production (Required)

| Variable           | Description               |
|--------------------|---------------------------|
| `PROD_DB_URL`      | PostgreSQL connection URL |
| `PROD_DB_USERNAME` | Database username         |
| `PROD_DB_PASSWORD` | Database password         |
| `PROD_JWT_SECRET`  | JWT signing secret        |

### Variable Injection Pattern

Configuration files use the `${VARIABLE_NAME:default}` pattern:

```yaml
# Uses environment variable with fallback default
datasource:
  url: ${DB_URL:jdbc:postgresql://localhost:5433/devbraid}
  password: ${DB_PASSWORD:devbraid_password}

# Requires environment variable (no default - will fail if not set)
jwt:
  secret: ${JWT_SECRET}
```

---

## Docker Setup

### Start Database Only

```bash
docker compose up -d postgres
```

This starts PostgreSQL on port `5433` (mapped from container's `5432`).

### Start All Services

```bash
docker compose up -d
```

### Stop Services

```bash
docker compose down
```

### Stop and Remove Data

```bash
docker compose down -v
```

---

## Database

### Connection Details (Development)

| Property | Value               |
|----------|---------------------|
| Host     | `localhost`         |
| Port     | `5433`              |
| Database | `devbraid`          |
| Username | `devbraid_user`     |
| Password | `devbraid_password` |

### Migrations

This project uses **Flyway** for database migrations. Migration files are located in `src/main/resources/db/migration/`.

Migrations run automatically on application startup:

- **dev**: Runs migrations + auto-updates schema via Hibernate
- **stage**: Runs migrations, validates schema only
- **prod**: Runs migrations, validates schema only (no out-of-order)

---

## Building

### Development Build

```bash
./mvnw clean package -DskipTests
```

### Production Build

```bash
./mvnw clean package -P prod
```

### Run Tests

```bash
./mvnw test
```

---

## Project Structure

```
devbraid-backend/
├── src/
│   ├── main/
│   │   ├── java/com/devbraid/     # Application source code
│   │   └── resources/
│   │       ├── application.yml         # Common configuration
│   │       ├── application-dev.yaml    # Development profile
│   │       ├── application-stage.yaml  # Staging profile
│   │       ├── application-prod.yaml   # Production profile
│   │       ├── db/migration/           # Flyway migrations
│   │       ├── static/                 # Static resources
│   │       └── templates/              # Template files
│   └── test/                       # Test source code
├── .env.example                    # Environment variable template
├── docker-compose.yml              # Docker services configuration
├── pom.xml                         # Maven project configuration
└── mvnw                            # Maven Wrapper
```

---

## Development Workflow

1. **Start database**: `docker compose up -d postgres`
2. **Set environment**: `export SPRING_PROFILES_ACTIVE=dev`
3. **Run application**: `./mvnw spring-boot:run`
4. **Access endpoints**: `http://localhost:8080/`

### IDE Configuration

#### IntelliJ IDEA

1. Go to **Run > Edit Configurations**
2. Add **Environment variable**: `SPRING_PROFILES_ACTIVE=dev`
3. Or add **VM Options**: `-Dspring.profiles.active=dev`

#### VS Code

Add to `.vscode/launch.json`:

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "DevBraid Backend",
      "request": "launch",
      "mainClass": "com.devbraid.DevbraidBackendApplication",
      "env": {
        "SPRING_PROFILES_ACTIVE": "dev"
      }
    }
  ]
}
```

---

## Security Notes

- **Never commit `.env` files** to version control (already in `.gitignore`)
- **Use `.env.example`** as a template and copy it to `.env` for local development
- **Production secrets** must be set via environment variables or a secrets manager
- **JWT secrets** should be at least 256 bits (32 characters) for HS256
- **Development JWT secret** is hardcoded for convenience - never use in production

---

## License

[Add your license here]
