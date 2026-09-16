# Database Schema Management

Raw SQL, no ORM migration tool by design. `database/schema/` holds numbered SQL files applied in order.

## File Structure & Naming

Files in `database/schema/` are numbered and applied in alphabetical order on container startup:

- `001_core_schema.sql` — Initial schema (users, accounts, core tables)
- `002_add_email_unique.sql` — Add unique constraint on email
- `003_add_experience.sql` — Add new column
- `004_widen_ssn_for_hash.sql` — Modify column
- `005_add_orders_table.sql` — Add new table for orders feature

Format: `NNN_descriptive_name.sql` (three-digit prefix, snake_case description)

## When Adding Schema Changes

1. **Never edit existing files** (001, 002, 003, etc.) — breaks reproducibility and existing deployments
2. **Create a new numbered file** with descriptive name:
   ```bash
   touch database/schema/005_add_orders_table.sql
   ```
3. **Add comments explaining what changed and why:**
   ```sql
   -- Migration: Add Orders table for order tracking feature
   -- Date: 2026-09-16
   -- Purpose: Support order placement, tracking, and history
   
   CREATE TABLE orders (
     order_id SERIAL PRIMARY KEY,
     account_id INT NOT NULL REFERENCES accounts(account_id),
     symbol VARCHAR(10) NOT NULL,
     quantity INT NOT NULL,
     order_type ENUM('BUY', 'SELL') NOT NULL,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
   ```
4. **Apply migrations** by running the full stack:
   ```bash
   docker compose up -d --build
   ```
   Docker automatically applies all SQL files in order on startup.

## Database Initialization

- Container startup → executes all SQL files in `database/schema/` in alphabetical order
- Schema becomes part of container image → reproducible across environments
- New developer: Just run `docker compose up -d` and database is ready with full schema

---

← Back to [AGENTS.md](../AGENTS.md)
