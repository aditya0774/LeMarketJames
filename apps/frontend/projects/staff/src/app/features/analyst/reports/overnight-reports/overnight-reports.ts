import { Component, OnInit, inject, signal } from '@angular/core';
import { OvernightReport, OvernightReportStatus } from '@staff/shared/models/overnight-report.model';
import { OvernightReportsService } from '@staff/shared/services/overnight-reports.service';
import { formatReportTime } from '@staff/shared/utils/report-time';

/** The label each status is shown with: the badge carries text, not only a colour. */
const STATUS_LABELS: Record<OvernightReportStatus, string> = {
  SCHEDULED: 'Scheduled',
  RUNNING: 'Running',
  READY: 'Ready',
  LATE: 'Late',
  FAILED: 'Failed',
};

/** The shared status pill (styles/base.css) each status is drawn with, as the order statuses are. */
const STATUS_PILLS: Record<OvernightReportStatus, string> = {
  SCHEDULED: 'pill-submitted',
  RUNNING: 'pill-pending',
  READY: 'pill-filled',
  LATE: 'pill-delayed',
  FAILED: 'pill-rejected',
};

/**
 * Analyst report "Overnight reports" (LMKT-45): every overnight run with its status, when it was
 * ready, and why it failed. The platform sends no notifications, so this list is where an analyst
 * finds out that a run failed. Times are shown in the reports time zone the server sends. The
 * heading, subtitle and tabs come from the analyst layout around it.
 */
@Component({
  selector: 'staff-overnight-reports',
  templateUrl: './overnight-reports.html',
  styleUrl: './overnight-reports.css',
})
export class OvernightReports implements OnInit {
  private readonly overnightReports = inject(OvernightReportsService);

  protected readonly isLoading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly reports = signal<readonly OvernightReport[]>([]);
  protected readonly timeZone = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  /** Asks again: a run may finish, or fail, while the page is open. */
  protected refresh(): void {
    this.load();
  }

  protected statusLabel(status: OvernightReportStatus): string {
    return STATUS_LABELS[status];
  }

  protected statusPill(status: OvernightReportStatus): string {
    return STATUS_PILLS[status];
  }

  protected time(isoUtc: string): string {
    return formatReportTime(isoUtc, this.timeZone() ?? 'UTC');
  }

  private load(): void {
    this.error.set(null);
    this.isLoading.set(true);

    this.overnightReports.list().subscribe({
      next: (response) => {
        if (response.success) {
          this.reports.set(response.data);
          this.timeZone.set(response.timeZone);
        } else {
          this.error.set('Unable to load overnight reports. Please try again.');
        }
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        if (err.status === 403) {
          this.error.set('You do not have permission to access this report.');
        } else if (err.status === 401) {
          this.error.set('Your session has expired. Please log in again.');
        } else {
          this.error.set('Unable to load overnight reports. Please try again.');
        }
      },
    });
  }
}
