import { Component, input, output } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PortfolioTotals } from '../../../core/holdings/portfolio-totals';
import { SignedCurrencyPipe } from '../../../shared/pipes/signed-currency.pipe';
import { CashMovement } from '../../../core/cash/cash.service';
import { RollingNumber } from '../../../shared/components/rolling-number/rolling-number';

/**
 * The Overview's four headline cards from the redesign: portfolio value, total gain/loss, buying
 * power with Deposit and Withdraw (which ask the page to open "Move money"), and open orders
 * (which links to the Orders tab). Gain/loss is unrealised: no day-open
 * portfolio value is exposed yet (see contracts/C6-api.md), so there is no "today" figure.
 * The figures roll to each new value (RollingNumber), since the page updates them live.
 */
@Component({
  selector: 'app-stat-strip',
  imports: [CurrencyPipe, DecimalPipe, RouterLink, SignedCurrencyPipe, RollingNumber],
  template: `
    <div class="stat-strip">
      <div class="stat-card">
        <span class="lbl">Portfolio value</span>
        <span class="val mono"><app-rolling-number [text]="(totals().value | currency: 'USD') ?? ''" /></span>
        <span class="note">Your stocks at today's prices</span>
      </div>
      <div class="stat-card">
        <span class="lbl">Total gain / loss</span>
        <span class="val mono" [class.up]="totals().gainLoss > 0" [class.down]="totals().gainLoss < 0">
          <app-rolling-number [text]="totals().gainLoss | signedCurrency" />
        </span>
        <span class="note">
          <span class="mono" [class.up]="totals().gainLoss > 0" [class.down]="totals().gainLoss < 0">
            {{ totals().gainLoss >= 0 ? '▲' : '▼' }}
            <app-rolling-number [text]="((totals().gainLossPercent < 0 ? -totals().gainLossPercent : totals().gainLossPercent) | number: '1.2-2') + '%'" />
          </span>
          · locked in only when you sell
        </span>
      </div>
      <div class="stat-card">
        <span class="lbl">Buying power</span>
        <span class="val mono"><app-rolling-number [text]="buyingPower() != null ? ((buyingPower() | currency: 'USD') ?? '') : '—'" /></span>
        @if (buyingPower() != null) {
          <div class="cash-btns">
            <button type="button" class="btn btn-sm btn-soft" (click)="moveMoney.emit('DEPOSIT')">Deposit</button>
            <button type="button" class="btn btn-sm btn-outline" (click)="moveMoney.emit('WITHDRAW')">Withdraw</button>
          </div>
        } @else {
          <span class="note">Balance unavailable</span>
        }
      </div>
      <a class="stat-card stat-link" routerLink="/orders">
        <span class="lbl">Open orders <span class="view">View →</span></span>
        <span class="val mono">{{ openOrders() }}</span>
        <span class="note">Still on their way to completing</span>
      </a>
    </div>
  `,
  styles: `
    .stat-strip {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 14px;
    }
    .stat-card {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 16px 18px;
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: var(--radius-md);
      color: var(--text);
    }
    .lbl {
      display: flex;
      justify-content: space-between;
      font-size: 12.5px;
      color: var(--dim);
    }
    .view {
      color: var(--pri);
    }
    .val {
      font-size: 24px;
      font-weight: 500;
    }
    .cash-btns {
      display: flex;
      gap: 6px;
      margin-top: 2px;
    }
    .cash-btns .btn {
      height: 30px;
      padding: 0 12px;
      font-size: 12.5px;
    }
    .note {
      font-size: var(--font-xs);
      color: var(--faint);
    }
    .stat-link:hover {
      color: var(--text);
      background: var(--card2);
    }
    @media (max-width: 1000px) {
      .stat-strip {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }
  `,
})
export class StatStrip {
  readonly totals = input.required<PortfolioTotals>();
  readonly openOrders = input(0);
  readonly buyingPower = input<number | null>(null);
  readonly moveMoney = output<CashMovement>();
}
