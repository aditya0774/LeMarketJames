import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AccountPage } from './account-page';
import { Auth } from '../../core/auth/auth';
import { AccountContents } from '../../core/account/account-closure.service';

// BR-03: a client closes their own account, once nothing on it can still move.
describe('Account page: close my account', () => {
  let http: HttpTestingController;
  let router: Router;
  const auth = { currentUser: () => 'bronny', clearSession: vi.fn() };
  const url = '/api/auth/close-account';
  const empty: AccountContents = { closable: true, cashBalance: 1234.5, currency: 'USD', holdings: 0, openOrders: 0 };

  beforeEach(() => {
    auth.clearSession.mockReset();
    TestBed.configureTestingModule({
      imports: [AccountPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: Auth, useValue: auth }],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });
  afterEach(() => http.verify());

  async function render(contents: AccountContents) {
    const fixture = TestBed.createComponent(AccountPage);
    fixture.detectChanges();
    http.expectOne(r => r.method === 'GET' && r.url.endsWith(url)).flush(contents);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const refresh = async () => { await fixture.whenStable(); fixture.detectChanges(); };
    const button = (text: string) => Array.from(el.querySelectorAll('button')).find(b => b.textContent?.includes(text))!;
    return { fixture, el, refresh, button };
  }

  it('says what to do first while stocks or open orders remain, and offers no close button', async () => {
    const { el, button } = await render({ ...empty, closable: false, holdings: 2, openOrders: 1 });

    expect(el.textContent).toContain("Your account can't be closed yet.");
    expect(el.textContent).toContain('You hold shares in 2 stocks.');
    expect(el.textContent).toContain('1 order is still in progress.');
    expect(el.querySelector('a[href="/holdings"]')).not.toBeNull();
    expect(el.querySelector('a[href="/orders"]')).not.toBeNull();
    expect(button('Close my account')).toBeUndefined();
  });

  it('shows the cash that stays, then asks for the password before closing', async () => {
    const { el, refresh, button } = await render(empty);
    expect(el.querySelector('.acct-cash dd')?.textContent).toContain('$1,234.50');
    expect(el.textContent).toContain('Our operations team will pay it out to you.');
    expect(el.querySelector('#acct-password')).toBeNull();

    button('Close my account').click();
    await refresh();
    expect(el.querySelector('#acct-password')).not.toBeNull();

    // No password, no request.
    button('Close my account permanently').click();
    await refresh();
    expect(el.querySelector('.field-error')?.textContent).toBe('Enter your password.');
    http.expectNone(r => r.method === 'POST');
  });

  it('closes the account, forgets the session and says so on the login page', async () => {
    const { fixture, refresh, button } = await render(empty);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    button('Close my account').click();
    await refresh();
    (fixture.componentInstance as unknown as { password: string }).password = 'Pass123!';
    button('Close my account permanently').click();

    const post = http.expectOne(r => r.method === 'POST' && r.url.endsWith(url));
    expect(post.request.body).toEqual({ password: 'Pass123!' });
    post.flush({ message: 'Your account is closed.', cashBalance: 1234.5, currency: 'USD' });
    await refresh();

    expect(auth.clearSession).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'closed' }, replaceUrl: true });
  });

  it("shows the server's message for a wrong password and stays signed in", async () => {
    const { el, fixture, refresh, button } = await render(empty);
    button('Close my account').click();
    await refresh();
    (fixture.componentInstance as unknown as { password: string }).password = 'Wrong1!';
    button('Close my account permanently').click();

    http.expectOne(r => r.method === 'POST').flush(
      { errors: { password: 'The password is not correct.' } }, { status: 400, statusText: 'Bad Request' });
    await refresh();

    expect(el.querySelector('.field-error')?.textContent).toBe('The password is not correct.');
    expect(auth.clearSession).not.toHaveBeenCalled();
  });

  it('reloads what is left when the server refuses because something arrived meanwhile', async () => {
    const { el, fixture, refresh, button } = await render(empty);
    button('Close my account').click();
    await refresh();
    (fixture.componentInstance as unknown as { password: string }).password = 'Pass123!';
    button('Close my account permanently').click();

    http.expectOne(r => r.method === 'POST').flush(
      { success: false, code: 'ACCOUNT_NOT_EMPTY', error: 'Wait for your open orders to finish before closing your account.', holdings: 0, openOrders: 1 },
      { status: 409, statusText: 'Conflict' });
    await refresh();
    http.expectOne(r => r.method === 'GET' && r.url.endsWith(url)).flush({ ...empty, closable: false, openOrders: 1 });
    await refresh();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Wait for your open orders to finish');
    expect(el.textContent).toContain('1 order is still in progress.');
    expect(auth.clearSession).not.toHaveBeenCalled();
  });
});
