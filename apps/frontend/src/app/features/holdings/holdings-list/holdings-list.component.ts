import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { HoldingsService, ErrorInfo } from '@app/core/holdings/holdings.service';
import { Auth } from '@app/core/auth/auth';
import { HoldingDto, HoldingsResponse } from '@app/shared/models/holdings.model';
import { ByStockReportComponent } from '../by-stock-report/by-stock-report.component';

/**
 * Holdings Display Component
 * 
 * AC1: Unauthorized data access prevented
 * - Detects 403 errors and shows safe message: "You do not have permission to view this account."
 * - Never leaks sensitive data (account details, database errors)
 * 
 * AC2: Requests scoped to authenticated user
 * - Gets accountId from Auth service (not hardcoded or passed in)
 * - Passes authenticated user context to HoldingsService
 * - Component doesn't need to know about accountId extraction
 */
@Component({
  selector: 'app-holdings-list',
  templateUrl: './holdings-list.component.html',
  styleUrls: ['./holdings-list.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    DecimalPipe,
    MatProgressSpinnerModule,
    MatButtonModule,
    MatTableModule,
    MatCardModule,
    MatIconModule,
    MatTabsModule,
    ByStockReportComponent
  ]
})
export class HoldingsListComponent implements OnInit {
  // Tab selection state
  protected readonly activeTab = signal<'holdings' | 'by-stock'>('holdings');
  
  // Use getters to access service signals (avoids initialization order issues)
  get isLoading() {
    return this.holdingsService.isLoading;
  }
  
  get error() {
    return this.holdingsService.error;
  }
  
  get holdings$() {
    return this.holdingsService.holdings;
  }
  
  get currentUser() {
    return this.auth.currentUser;
  }
  
  // For template
  displayedColumns: string[] = ['symbol', 'quantity', 'averageCost', 'currentPrice', 'totalCost', 'currentValue', 'gainLoss', 'gainLossPercent'];

  constructor(
    private readonly holdingsService: HoldingsService,
    private readonly auth: Auth
  ) {}

  ngOnInit(): void {
    this.loadHoldings();
  }

  /**
   * AC1: Load user's holdings
   * AC2: Automatically scoped to authenticated user via HoldingsService
   * 
   * Handles all error cases:
   * - 403 ACCOUNT_ACCESS_DENIED: Shows safe message
   * - 400 Bad Request: Shows validation error
   * - 500+ Server Error: Shows generic error
   * - Network Error: Shows user-friendly message
   */
  loadHoldings(): void {
    try {
      // AC2: HoldingsService extracts accountId from Auth context
      this.holdingsService.getOwnHoldings().subscribe({
        next: (response: HoldingsResponse) => {
          // Service already updates its signals
        },
        error: (error: any) => {
          // Service error signal already updated in HoldingsService.handleError()
          // Component just displays what service provides
        }
      });
    } catch (err: any) {
      // Handle errors thrown by service (e.g., no accountId)
      // Service already set error signal
    }
  }

  /**
   * Allow user to retry after auth error
   * AC1: Safe error message shown before retry
   */
  retry(): void {
    this.loadHoldings();
  }

  /**
   * AC1: Check if error is authorization-related (403)
   * Used to show/hide retry button
   */
  isAuthError(): boolean {
    const currentError = this.error();
    return currentError?.code === 'ACCOUNT_ACCESS_DENIED';
  }

  /**
   * Switch between Holdings and By Stock tabs
   */
  selectTab(tab: 'holdings' | 'by-stock'): void {
    this.activeTab.set(tab);
  }
}
