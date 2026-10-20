import { GlossaryTerm, GLOSSARY, LESSONS } from './learn-content';
import { splitLesson, termsOf } from './term-links';

const SHARE: GlossaryTerm = { term: 'Share', definition: 'One unit of ownership.' };
const MARKET: GlossaryTerm = { term: 'Market', definition: 'Where things trade.' };
const MARKET_ORDER: GlossaryTerm = { term: 'Market order', definition: 'Buy or sell right away.' };
const PNL: GlossaryTerm = { term: 'Profit and loss (P&L)', definition: 'Gain or loss.', match: ['P&L', 'profit or loss'] };

/** The text of a segment list with each term wrapped in [], to read a result at a glance. */
const show = (segs: { text: string; term?: GlossaryTerm }[]) => segs.map((s) => (s.term ? `[${s.text}]` : s.text)).join('');

describe('splitLesson', () => {
  it('returns one plain segment when nothing matches', () => {
    expect(splitLesson(['Nothing to see.'], [SHARE])).toEqual([[{ text: 'Nothing to see.' }]]);
  });

  it('marks a term in any case and keeps the original wording', () => {
    expect(show(splitLesson(['A SHARE is a slice.'], [SHARE])[0])).toBe('A [SHARE] is a slice.');
  });

  it('matches a simple plural', () => {
    expect(show(splitLesson(['You own shares.'], [SHARE])[0])).toBe('You own [shares].');
  });

  it('matches whole words only', () => {
    expect(splitLesson(['Shareholders and sharing.'], [SHARE])[0].some((s) => s.term)).toBe(false);
  });

  it('prefers the longer term when two start at the same place', () => {
    expect(show(splitLesson(['Place a market order now.'], [MARKET, MARKET_ORDER])[0])).toBe('Place a [market order] now.');
  });

  it('marks only the first mention in the whole lesson, across paragraphs', () => {
    const result = splitLesson(['One share here.', 'Another share there.'], [SHARE]);

    expect(show(result[0])).toBe('One [share] here.');
    expect(show(result[1])).toBe('Another share there.');
  });

  it('marks several different terms in one paragraph, in reading order', () => {
    expect(show(splitLesson(['A market order for a share.'], [SHARE, MARKET_ORDER])[0])).toBe('A [market order] for a [share].');
  });

  it('handles a term with special characters through its aliases', () => {
    expect(show(splitLesson(['Your P&L is up; profit or loss changes.'], [PNL])[0])).toBe('Your [P&L] is up; profit or loss changes.');
    expect(show(splitLesson(['See profit or loss.'], [PNL])[0])).toBe('See [profit or loss].');
  });

  it('never treats text as markup', () => {
    const segs = splitLesson(['<b>share</b>'], [SHARE])[0];

    expect(segs.map((s) => s.text).join('')).toBe('<b>share</b>');
  });
});

describe('the lessons and the glossary', () => {
  const names = GLOSSARY.map((g) => g.term);

  it('only name glossary terms that exist', () => {
    for (const lesson of LESSONS) {
      for (const name of lesson.terms) {
        expect(names, `${lesson.id} names "${name}"`).toContain(name);
      }
    }
  });

  it('teach every glossary term in at least one lesson', () => {
    const taught = new Set(LESSONS.flatMap((l) => l.terms));

    expect(names.filter((n) => !taught.has(n))).toEqual([]);
  });

  it('define each term once', () => {
    expect(new Set(names).size).toBe(names.length);
  });

  it('resolve a lesson to its definitions, in the order listed', () => {
    const lesson = LESSONS.find((l) => l.terms.length > 1)!;

    expect(termsOf(lesson).map((t) => t.term)).toEqual(lesson.terms);
  });
});
