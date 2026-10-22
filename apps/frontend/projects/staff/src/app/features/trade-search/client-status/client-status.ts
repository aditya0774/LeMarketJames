import { Component, computed, effect, inject, input, OnDestroy, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AccountStatus, ClientStatus, ClientStatusService } from '../../../core/clients/client-status.service';

/** How each status reads in the panel, and what choosing it does to the client (BR-03). */
const STATUS_TEXT: Readonly<Record<AccountStatus, { label: string; effect: string }>> = {
  ACTIVE: { label: 'Active', effect: 'The client can sign in and trade again.' },
  SUSPENDED: { label: 'Suspended', effect: 'The client is signed out at once and cannot sign in until the account is made active again.' },
  CLOSED: { label: 'Closed', effect: 'The client is signed out at once and cannot sign in. Any cash stays on the account.' },
  EXPIRED: { label: 'Expired', effect: 'The client is signed out at once and cannot sign in until their documents are renewed.' },
};

/** The longest reason auth-service keeps (account_status_changes.reason, 027). */
export const MAX_REASON_LENGTH = 500;

/**
 * The selected client's account status in trade search, with its history and a "Change account
 * status" form (BR-03). A change needs a reason and a second, explicit confirmation, because
 * suspending or closing signs the client out on their next request.
 */
@Component({
  selector: 'staff-client-status',
  imports: [FormsModule],
  templateUrl: './client-status.html',
  styleUrl: './client-status.css',
})
export class ClientStatusPanel implements OnDestroy {
  private readonly api = inject(ClientStatusService);
  private request?: Subscription;

  /** The client whose status is shown; a new id loads it afresh. */
  readonly clientId = input.required<number>();

  readonly statuses = Object.keys(STATUS_TEXT) as AccountStatus[];
  readonly maxReasonLength = MAX_REASON_LENGTH;
  readonly client = signal<ClientStatus | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly saved = signal('');
  /** True between "Change status" and the confirmation's answer. */
  readonly confirming = signal(false);
  next: AccountStatus | '' = '';
  reason = '';

  /** The statuses the account can move to: every one but its current one. */
  readonly choices = computed(() => this.statuses.filter(status => status !== this.client()?.status));

  constructor() {
    effect(() => {
      const clientId = this.clientId();
      untracked(() => this.load(clientId));
    });
  }

  label(status: AccountStatus): string {
    return STATUS_TEXT[status].label;
  }

  effectOf(status: AccountStatus): string {
    return STATUS_TEXT[status].effect;
  }

  /** Checks the form, then asks for confirmation; nothing is sent yet. */
  review(): void {
    this.saved.set('');
    if (!this.next) { this.error.set('Choose the new status.'); return; }
    if (!this.reason.trim()) { this.error.set('Give a reason for the change.'); return; }
    this.error.set('');
    this.confirming.set(true);
  }

  cancel(): void {
    this.confirming.set(false);
  }

  confirm(): void {
    const next = this.next;
    if (!next) { return; }
    this.request?.unsubscribe();
    this.saving.set(true);
    this.request = this.api.change(this.clientId(), next, this.reason.trim()).subscribe({
      next: client => {
        this.client.set(client);
        this.saving.set(false);
        this.confirming.set(false);
        this.next = '';
        this.reason = '';
        this.saved.set(`The account is now ${this.label(client.status).toLowerCase()}.`);
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.confirming.set(false);
        this.error.set(this.message(error, 'Unable to change the account status. Please try again.'));
      },
    });
  }

  /** Shows a UTC instant as "2026-10-21 14:05 UTC", like the rest of trade search. */
  utcTime(value: string): string {
    return value.slice(0, 16).replace('T', ' ') + ' UTC';
  }

  private load(clientId: number): void {
    this.request?.unsubscribe();
    this.client.set(null);
    this.error.set('');
    this.saved.set('');
    this.confirming.set(false);
    this.next = '';
    this.reason = '';
    this.loading.set(true);
    this.request = this.api.status(clientId).subscribe({
      next: client => { this.client.set(client); this.loading.set(false); },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(this.message(error, 'Unable to load the account status. Please try again.'));
      },
    });
  }

  // A 400 names the broken field; show its message as auth-service wrote it.
  private message(error: HttpErrorResponse, fallback: string): string {
    const fieldErrors = error.status === 400 ? error.error?.errors : null;
    if (fieldErrors && typeof fieldErrors === 'object') {
      const first = Object.values(fieldErrors)[0];
      if (typeof first === 'string') { return first; }
    }
    return error.status === 403 ? 'Access denied. Account status changes require Trading Operations.'
      : error.status === 404 ? 'No client has this ID.'
      : error.status === 401 ? 'Your session has expired. Please sign in again.'
      : fallback;
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }
}
