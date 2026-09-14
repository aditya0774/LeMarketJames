import { Component, OnInit } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { HoldingsService } from '@app/core/holdings/holdings.service';
import { HoldingDto, HoldingsResponse } from '@app/shared/models/holdings.model';

@Component({
  selector: 'app-holdings-list',
  templateUrl: './holdings-list.component.html',
  styleUrls: ['./holdings-list.component.scss'],
  imports: [CommonModule, DecimalPipe]
})
export class HoldingsListComponent implements OnInit {
  
  holdings: HoldingDto[] = [];
  isLoading = false;
  errorMessage: string | null = null;
  accountId = 1;  // In real app, get from auth context

  constructor(private holdingsService: HoldingsService) {}

  ngOnInit(): void {
    this.loadHoldings();
  }

  loadHoldings(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.holdingsService.getHoldings(this.accountId).subscribe({
      next: (response: HoldingsResponse) => {
        if (response.success) {
          this.holdings = response.holdings;
        } else {
          this.errorMessage = 'Failed to load holdings';
        }
        this.isLoading = false;
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Error loading holdings';
        this.isLoading = false;
      }
    });
  }
}
