/**
 * Shared JPA mappings for clients, addresses, and accounts (database/schema/001_core_schema.sql).
 * auth-service writes these rows at registration; other services (e.g. core-service) read
 * accounts from the same shared database to check ownership and cash balances.
 */
package com.lemarketjames.common.domain;
