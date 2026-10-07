import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivityByClientSegment } from './activity-by-client-segment';

describe('ActivityByClientSegment', () => {
  it('renders its heading', async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityByClientSegment],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(ActivityByClientSegment);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Activity by client segment');
  });
});
