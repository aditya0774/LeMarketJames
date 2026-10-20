import { GLOSSARY, GlossaryTerm, Lesson } from './learn-content';

/** A run of lesson text; `term` is set when the run is a glossary term the reader can open. */
export interface TextSegment {
  text: string;
  term?: GlossaryTerm;
}

/** The glossary entries a lesson teaches, in the order the lesson lists them. */
export function termsOf(lesson: Pick<Lesson, 'terms'>): GlossaryTerm[] {
  return lesson.terms.flatMap((name) => GLOSSARY.filter((g) => g.term === name));
}

/**
 * Splits a lesson's paragraphs into plain and term segments. Only the first mention of each term
 * in the whole lesson is marked, so the text is not covered in links. The result is data, not
 * markup: the template prints each segment as text, so lesson text can never become HTML.
 */
export function splitLesson(paragraphs: readonly string[], terms: readonly GlossaryTerm[]): TextSegment[][] {
  const used = new Set<string>();
  return paragraphs.map((paragraph) => splitParagraph(paragraph, terms, used));
}

function splitParagraph(text: string, terms: readonly GlossaryTerm[], used: Set<string>): TextSegment[] {
  const segments: TextSegment[] = [];
  let from = 0;
  for (;;) {
    const hit = earliestMatch(text, from, terms.filter((t) => !used.has(t.term)));
    if (!hit) {
      break;
    }
    if (hit.start > from) {
      segments.push({ text: text.slice(from, hit.start) });
    }
    segments.push({ text: text.slice(hit.start, hit.end), term: hit.term });
    used.add(hit.term.term);
    from = hit.end;
  }
  if (from < text.length) {
    segments.push({ text: text.slice(from) });
  }
  return segments;
}

/** The match that starts first at or after `from`; on a tie the longer one, so "market order" beats "market". */
function earliestMatch(
  text: string,
  from: number,
  terms: readonly GlossaryTerm[],
): { term: GlossaryTerm; start: number; end: number } | null {
  let best: { term: GlossaryTerm; start: number; end: number } | null = null;
  for (const term of terms) {
    const pattern = patternFor(term);
    pattern.lastIndex = from;
    const m = pattern.exec(text);
    if (!m) {
      continue;
    }
    const start = m.index;
    const end = start + m[0].length;
    if (!best || start < best.start || (start === best.start && end > best.end)) {
      best = { term, start, end };
    }
  }
  return best;
}

/**
 * Whole words only (so "share" never matches inside "shareholders"), any case, with an optional
 * plural "s". Lookarounds rather than \b because a form such as "P&L" ends in a non-word character.
 */
function patternFor(term: GlossaryTerm): RegExp {
  const forms = [term.term, ...(term.match ?? [])]
    .sort((a, b) => b.length - a.length)
    .map((f) => f.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  return new RegExp(`(?<!\\w)(?:${forms.join('|')})s?(?!\\w)`, 'gi');
}
