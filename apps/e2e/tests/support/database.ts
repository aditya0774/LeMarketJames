import { Client } from 'pg';

/**
 * Direct database access for what the API does not expose yet (the audit trail, C2) and for
 * fixtures the API cannot create. Refuses to connect unless the run says the database is a
 * disposable one, so the suite can never touch a real database by accident.
 */
export async function connectDatabase(): Promise<Client> {
  const connectionString = process.env.E2E_DATABASE_URL;
  if (!connectionString && process.env.E2E_ALLOW_DATABASE_SEED !== 'true') {
    throw new Error('Set E2E_DATABASE_URL or explicitly allow seeding a disposable database.');
  }
  // node-postgres uses PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD when no URL is supplied.
  const db = connectionString ? new Client({ connectionString }) : new Client();
  await db.connect();
  return db;
}

export interface AuditEvent {
  action: string;
  details: Record<string, unknown>;
}

/**
 * An order's audit events in the order they were written. Read from the table because the audit
 * API is still planned (C6); audit_id is assigned at insert, so it orders events across services.
 */
export async function auditTrail(db: Client, orderId: number): Promise<AuditEvent[]> {
  const result = await db.query(
    'SELECT action, details FROM audit_log WHERE order_id=$1 ORDER BY audit_id', [orderId]);
  return result.rows;
}

/**
 * The lifecycle steps of a trail, without the RULE_CHECKED event written for each placement check
 * between SUBMITTED and VALIDATED (C2). Which checks run depends on the side of the order, and
 * buy-sell-service's own tests cover them; these specs are about the steps around them.
 */
export function lifecycleSteps(trail: AuditEvent[]): string[] {
  return trail.filter(event => event.action !== 'RULE_CHECKED').map(event => event.action);
}

/** The placement checks of a trail, in the order they ran. */
export function ruleChecks(trail: AuditEvent[]): AuditEvent[] {
  return trail.filter(event => event.action === 'RULE_CHECKED');
}

export interface StoredOrder {
  order_status: string;
  /** NUMERIC comes back as a string, so no precision is lost. */
  price_per_unit: string | null;
  quote_source: string | null;
  quote_time: Date | null;
  /** UTC wall-clock time rendered as text; the column has no time zone to convert from. */
  filled_at: string | null;
  rejection_reason: string | null;
}

/** The order row itself: what is stored with a fill, independent of how the API renders it. */
export async function storedOrder(db: Client, orderId: number): Promise<StoredOrder> {
  const result = await db.query(
    `SELECT order_status, price_per_unit, quote_source, quote_time,
            to_char(filled_at, 'YYYY-MM-DD"T"HH24:MI:SS.US') AS filled_at, rejection_reason
     FROM orders WHERE order_id=$1`, [orderId]);
  return result.rows[0];
}
