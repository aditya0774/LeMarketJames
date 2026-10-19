import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CashService } from './cash.service';

describe('CashService', () => {
  let cash: CashService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    cash = TestBed.inject(CashService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts a deposit with its amount and request id, and returns the new balance', async () => {
    const answer = cash.move('DEPOSIT', 250, 'id-1');

    const request = http.expectOne('/api/v1/portfolio/deposits');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ amount: 250, requestId: 'id-1' });
    request.flush({ success: true, balance: { buyingPower: 1250 } });

    expect((await answer).balance?.buyingPower).toBe(1250);
  });

  it('posts a withdrawal to its own path', async () => {
    const answer = cash.move('WITHDRAW', 100, 'id-2');

    http.expectOne('/api/v1/portfolio/withdrawals').flush({ success: true, balance: { buyingPower: 900 } });

    expect((await answer).success).toBe(true);
  });
});
