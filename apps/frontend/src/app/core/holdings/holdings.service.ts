import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HoldingsResponse, ValidateHoldingRequest, ValidateHoldingResponse } 
  from '@app/shared/models/holdings.model';

@Injectable({ providedIn: 'root' })
export class HoldingsService {
  
  private readonly API_URL = '/api/holdings';
  
  constructor(private http: HttpClient) {}
  
  /**
   * AC1: Retrieve holdings for authenticated user
   */
  getHoldings(accountId: number): Observable<HoldingsResponse> {
    return this.http.get<HoldingsResponse>(`${this.API_URL}?accountId=${accountId}`);
  }
  
  /**
   * AC2: Validate sufficient holdings before SELL order
   */
  validateHoldings(request: ValidateHoldingRequest): Observable<ValidateHoldingResponse> {
    return this.http.post<ValidateHoldingResponse>(`${this.API_URL}/validate`, request);
  }
}
