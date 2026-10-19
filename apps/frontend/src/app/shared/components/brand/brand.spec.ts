import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Brand } from './brand';

describe('Brand', () => {
  const render = (inputs: Record<string, unknown> = {}) => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Brand);
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
  };

  it('links home with the lowercase wordmark', () => {
    const link = render();

    expect(link.getAttribute('href')).toBe('/');
    expect(link.getAttribute('aria-label')).toBe('lemarket home');
    expect(link.textContent?.trim()).toBe('lemarket');
  });

  it('can link elsewhere and render large', () => {
    const link = render({ link: '/dashboard', size: 'lg' });

    expect(link.getAttribute('href')).toBe('/dashboard');
    expect(link.classList).toContain('lg');
  });
});
