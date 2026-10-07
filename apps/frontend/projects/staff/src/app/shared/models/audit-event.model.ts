/**
 * Audit event from the order timeline.
 * Matches contract C6 response structure for GET /api/v1/orders/{orderId}/timeline
 */
export interface AuditEvent {
  eventType: string;
  occurredAt: string;
  details: Record<string, any>;
}
