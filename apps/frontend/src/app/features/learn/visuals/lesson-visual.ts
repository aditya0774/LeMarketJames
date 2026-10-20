import { Component, computed, input } from '@angular/core';
import { LessonVisualId } from '../learn-content';

/** What a screen reader says for a diagram, and the same thing as a visible caption. */
const DESCRIPTIONS: Record<LessonVisualId, string> = {
  'order-progress':
    'An order starts open. It then ends in one of two ways: filled, when it executes, or rejected, when it is refused.',
  'bid-ask':
    'The bid is the highest price a buyer will pay and the ask is the lowest price a seller will accept. The gap between them is the spread.',
  diversification:
    'When one stock falls, money held in a single stock drops a lot, while money spread over several stocks drops much less.',
  'day-range':
    "A stock's day is described by where it opened, its high, its low and its latest price, which sits between the high and the low.",
};

/**
 * One small diagram for a Learn lesson, picked by id. The drawing is plain SVG coloured with the
 * app's theme variables so it follows light and dark mode, and every diagram carries a caption
 * with the same meaning in words, so nothing depends on seeing the picture.
 */
@Component({
  selector: 'app-lesson-visual',
  templateUrl: './lesson-visual.html',
  styleUrl: './lesson-visual.css',
})
export class LessonVisual {
  readonly id = input.required<string>();

  /** Null for an id with no diagram, so the template draws nothing rather than an empty frame. */
  protected readonly description = computed(() => DESCRIPTIONS[this.id() as LessonVisualId] ?? null);
}
