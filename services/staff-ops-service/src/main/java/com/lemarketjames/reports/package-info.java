/**
 * Report endpoints, all under {@code /api/v1/reports/**} and all for analysts only.
 *
 * <p>Every report here follows the rules in this module's README.md ("Rules for every report
 * endpoint"): data comes only from the {@code reporting_trades} view, and a response holds
 * aggregates only, never anything that identifies an individual client. Read them before adding
 * an endpoint.
 */
package com.lemarketjames.reports;
