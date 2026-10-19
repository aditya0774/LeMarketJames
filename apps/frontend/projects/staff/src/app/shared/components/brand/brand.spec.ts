import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Brand } from './brand';

describe('Brand (staff)', () => {
  const render = (link: string | null) => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Brand);
    fixture.componentRef.setInput('link', link);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('marks the app as Courtside View', () => {
    expect(render(null).textContent).toContain('Courtside View');
  });

  it('is plain text without a link', () => {
    expect(render(null).querySelector('a')).toBeNull();
  });

  it('links to the given route', () => {
    expect(render('/analyst').querySelector('a')?.getAttribute('href')).toBe('/analyst');
  });
});
