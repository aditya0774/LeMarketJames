import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { OvernightReports } from './overnight-reports';

describe('OvernightReports', () => {
  it('renders its heading', async () => {
    await TestBed.configureTestingModule({
      imports: [OvernightReports],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(OvernightReports);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain('Overnight reports');
  });
});
