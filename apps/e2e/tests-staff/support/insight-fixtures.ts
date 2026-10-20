import { expect } from '@playwright/test';
import { Client } from 'pg';

/**
 * Fixtures shared by the staff insight specs (LMKT-46 volume, LMKT-47 most active stocks,
 * LMKT-48 active clients): whole
 * report-time-zone days, and filled orders placed on a chosen day for a throwaway client. Only
 * data setup uses SQL; the figures under test always come from the real API.
 */

/** A calendar day as YYYY-MM-DD, moved by whole days. UTC arithmetic keeps DST out of it. */
export function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Today's date in the given time zone, as YYYY-MM-DD. */
export function todayIn(timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

/** One filled order to place. `symbol` defaults to AAPL; the most-active-stocks spec needs several stocks. */
export interface Fill { day: string; side: 'BUY' | 'SELL'; quantity: number; price: number; symbol?: string }

/** Inserts one FILLED order for the account at noon on the report day, in the reports time zone. */
export async function insertFill(db: Client, accountId: number, timeZone: string, fill: Fill): Promise<void> {
  // Noon on the report day in the reports time zone, converted to the UTC wall clock the table stores.
  const result = await db.query(
    `INSERT INTO orders (account_id, instrument_id, order_type, quantity, price_per_unit, order_status,
                         submitted_at, accepted_at, filled_at, created_at, updated_at)
     SELECT $1, i.instrument_id, $2, $3, $4, 'FILLED', t.at, t.at, t.at, t.at, t.at
     FROM instruments i,
          (SELECT (($5::date + time '12:00') AT TIME ZONE $6::text) AT TIME ZONE 'UTC' AS at) t
     WHERE i.ticker = $7`,
    [accountId, fill.side, fill.quantity, fill.price, fill.day, timeZone, fill.symbol ?? 'AAPL']);
  expect(result.rowCount).toBe(1);
}

/** The account id of a registered throwaway client. */
export async function accountIdOf(db: Client, username: string): Promise<number> {
  const { rows } = await db.query(
    'SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1', [username]);
  expect(rows).toHaveLength(1);
  return rows[0].account_id;
}

/** Removes a throwaway client and its orders only; never truncates the shared database. */
export async function removeClient(db: Client, username: string): Promise<void> {
  await db.query('DELETE FROM orders WHERE account_id IN (SELECT a.account_id FROM accounts a JOIN clients c ON c.client_id=a.client_id WHERE c.username=$1)', [username]);
  await db.query('DELETE FROM accounts WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [username]);
  await db.query('DELETE FROM addresses WHERE client_id IN (SELECT client_id FROM clients WHERE username=$1)', [username]);
  await db.query('DELETE FROM clients WHERE username=$1', [username]);
}
