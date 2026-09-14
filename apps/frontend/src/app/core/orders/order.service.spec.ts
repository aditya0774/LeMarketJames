import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OrderService, OrderRequest, OrderResponse } from './order.service';

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [OrderService]
    });
    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should create an order', () => {
    const mockRequest: OrderRequest = {
      accountId: 1,
      instrumentId: 1,
      orderType: 'BUY',
      quantity: 10
    };

    const mockResponse: OrderResponse = {
      orderId: 1,
      accountId: 1,
      instrumentId: 1,
      orderType: 'BUY',
      quantity: 10,
      orderStatus: 'SUBMITTED',
      submittedAt: '2026-09-10T20:43:16Z',
      createdAt: '2026-09-10T20:43:16Z',
      updatedAt: '2026-09-10T20:43:16Z'
    };

    service.createOrder(mockRequest).subscribe(order => {
      expect(order.orderId).toBe(1);
      expect(order.orderStatus).toBe('SUBMITTED');
    });

    const req = httpMock.expectOne('/api/v1/orders');
    expect(req.request.method).toBe('POST');
    req.flush(mockResponse);
  });

  it('should retrieve order by ID', () => {
    const mockResponse: OrderResponse = {
      orderId: 1,
      accountId: 1,
      instrumentId: 1,
      orderType: 'BUY',
      quantity: 10,
      orderStatus: 'SUBMITTED',
      submittedAt: '2026-09-10T20:43:16Z',
      createdAt: '2026-09-10T20:43:16Z',
      updatedAt: '2026-09-10T20:43:16Z'
    };

    service.getOrderById(1).subscribe(order => {
      expect(order.orderId).toBe(1);
    });

    const req = httpMock.expectOne('/api/v1/orders/1');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should retrieve orders by account ID', () => {
    const mockResponse: OrderResponse[] = [
      {
        orderId: 1,
        accountId: 1,
        instrumentId: 1,
        orderType: 'BUY',
        quantity: 10,
        orderStatus: 'SUBMITTED',
        submittedAt: '2026-09-10T20:43:16Z',
        createdAt: '2026-09-10T20:43:16Z',
        updatedAt: '2026-09-10T20:43:16Z'
      }
    ];

    service.getOrdersByAccountId(1).subscribe(orders => {
      expect(orders.length).toBe(1);
      expect(orders[0].accountId).toBe(1);
    });

    const req = httpMock.expectOne('/api/v1/orders/account/1');
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);
  });

  it('should update order status', () => {
    const mockResponse: OrderResponse = {
      orderId: 1,
      accountId: 1,
      instrumentId: 1,
      orderType: 'BUY',
      quantity: 10,
      orderStatus: 'ACCEPTED',
      submittedAt: '2026-09-10T20:43:16Z',
      acceptedAt: '2026-09-10T20:43:20Z',
      createdAt: '2026-09-10T20:43:16Z',
      updatedAt: '2026-09-10T20:43:20Z'
    };

    service.updateOrderStatus(1, 'ACCEPTED').subscribe(order => {
      expect(order.orderStatus).toBe('ACCEPTED');
    });

    const req = httpMock.expectOne('/api/v1/orders/1/status/ACCEPTED');
    expect(req.request.method).toBe('PUT');
    req.flush(mockResponse);
  });

  it('should reject an order', () => {
    const mockResponse: OrderResponse = {
      orderId: 1,
      accountId: 1,
      instrumentId: 1,
      orderType: 'BUY',
      quantity: 10,
      orderStatus: 'REJECTED',
      rejectionReason: 'Insufficient funds',
      submittedAt: '2026-09-10T20:43:16Z',
      createdAt: '2026-09-10T20:43:16Z',
      updatedAt: '2026-09-10T20:43:20Z'
    };

    service.rejectOrder(1, 'Insufficient funds').subscribe(order => {
      expect(order.orderStatus).toBe('REJECTED');
      expect(order.rejectionReason).toBe('Insufficient funds');
    });

    const req = httpMock.expectOne(req => req.url === '/api/v1/orders/1/reject' && req.method === 'POST');
    req.flush(mockResponse);
  });
});
