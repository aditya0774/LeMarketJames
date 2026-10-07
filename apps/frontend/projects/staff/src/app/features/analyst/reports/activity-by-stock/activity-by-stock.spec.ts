import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivityByStock } from './activity-by-stock';

describe('ActivityByStock', () => {
  it('renders its heading', async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityByStock],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(ActivityByStock);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Activity by stock');
  });
});
