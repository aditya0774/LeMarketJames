import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Home } from './home';

describe('Home', () => {
  it('offers log in and sign up', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    const hrefs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('main a')).map((a) =>
      a.getAttribute('href'),
    );

    expect(hrefs).toEqual(['/register', '/login']);
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Investing that');
  });
});
