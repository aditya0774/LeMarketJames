import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { Auth } from '../auth/auth';
import { AccountOrders } from './account-orders';
import { OrderResponse, OrdersStreamSnapshot, OrderService } from './order.service';

const order = (orderId: number, orderStatus: OrderResponse['orderStatus']) =>
  ({ orderId, orderStatus }) as OrderResponse;

describe('AccountOrders', () => {
  let list: () => unknown;
  let stream: Subject<OrdersStreamSnapshot>;

  const create = () => {
    stream = new Subject();
    TestBed.configureTestingModule({
      providers: [
        AccountOrders,
        { provide: Auth, useValue: { currentAccountId: () => 7 } },
        { provide: OrderService, useValue: { getAllOrdersByAccountId: () => list(), watchOrderStatusStream: () => stream } },
      ],
    });
    return TestBed.inject(AccountOrders);
  };

  it('counts the orders still on their way', () => {
    list = () => of([order(1, 'FILLED'), order(2, 'DELAYED'), order(3, 'SUBMITTED'), order(4, 'REJECTED')]);
    const orders = create();
    orders.start();

    expect(orders.loaded()).toBe(true);
    expect(orders.openCount()).toBe(2);
  });

  it('reports a status change it sees in a reloaded list', () => {
    list = () => of([order(1, 'PENDING')]);
    const orders = create();
    orders.start();
    let changes = 0;
    orders.statusChanged.subscribe(() => changes++);

    list = () => of([order(1, 'FILLED')]);
    orders.reload();

    expect(changes).toBe(1);
  });

  it('remembers an expired session, closes the stream and opens no new one', () => {
    list = () => throwError(() => new HttpErrorResponse({ status: 401 }));
    const orders = create();
    orders.start();

    expect(orders.sessionExpired()).toBe(true);
    expect(stream.observed).toBe(false);

    orders.start();
    expect(stream.observed).toBe(false);
  });
});
