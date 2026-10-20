/**
 * The Learn tab's text, kept apart from the components that show it so lessons and terms can be
 * added or reworded (or later served by a backend) without touching any template.
 */

/** The diagrams lesson-visual.ts can draw; a lesson names the one that explains it. */
export type LessonVisualId = 'order-progress' | 'bid-ask' | 'diversification' | 'day-range';

export type LessonLevel = 'beginner' | 'intermediate';

/** A link from a lesson into the tab where the reader can try what it describes. */
export interface LessonAction {
  label: string;
  route: string;
}

export interface Lesson {
  id: string;
  title: string;
  level: LessonLevel;
  /** Rough reading time, shown on the card. */
  minutes: number;
  /** A diagram shown under the lesson text, for ideas that are easier to see than to read. */
  visual?: LessonVisualId;
  /** Where to practise the lesson, when there is a tab for it. */
  cta?: LessonAction;
  /** One line shown under the title, so the card can be scanned. */
  summary: string;
  /** Short paragraphs, shown in order. */
  body: string[];
  /**
   * The glossary terms this lesson teaches, by name. Each is marked where the text first mentions
   * it and listed with its definition under the lesson, so every term is explained in context.
   */
  terms: string[];
}

export interface GlossaryTerm {
  term: string;
  definition: string;
  /** Other wordings that mean this term in lesson text, for names the text does not use as written. */
  match?: string[];
}

export const LESSONS: Lesson[] = [
  {
    id: 'basics',
    level: 'beginner',
    minutes: 1,
    cta: { label: 'Browse the market', route: '/market' },
    title: 'How the stock market works',
    summary: 'What a share is and why prices move.',
    body: [
      'A share is a small slice of ownership in a company. When you buy one, you own a piece of that business.',
      'Prices move with supply and demand: more buyers than sellers push a price up, more sellers than buyers push it down. News, earnings and the mood of the market all change how much people are willing to pay.',
    ],
    terms: ['Share', 'Dividend', 'Market cap', 'Bull and bear market'],
  },
  {
    id: 'trade',
    level: 'beginner',
    minutes: 1,
    cta: { label: 'See your orders', route: '/orders' },
    title: 'How a trade works',
    summary: 'From placing an order to owning shares.',
    body: [
      'You place an order saying which stock, how many shares and whether you want to buy or sell. When the order fills, cash and shares change hands and your holdings update.',
      'You can follow every order, from submitted to filled, on the Orders tab.',
    ],
    terms: ['Fill'],
  },
  {
    id: 'orders',
    visual: 'bid-ask',
    level: 'beginner',
    minutes: 1,
    cta: { label: 'Try the simulator', route: '/learn/simulator' },
    title: 'Market and limit orders',
    summary: 'Choosing between speed and price.',
    body: [
      'A market order fills as soon as it can at the current price. It is quick, but the price you get can differ slightly from the one you saw.',
      'A limit order only fills at your chosen price or better. You control the price, but the order may wait, or never fill, if the market does not reach it.',
    ],
    terms: ['Market order', 'Limit order', 'Bid', 'Ask', 'Spread'],
  },
  {
    id: 'risk',
    visual: 'diversification',
    level: 'beginner',
    minutes: 1,
    cta: { label: 'Review your holdings', route: '/holdings' },
    title: 'Risk and diversification',
    summary: 'Why not to put everything in one stock.',
    body: [
      'Every investment can lose value. Spreading money across several companies and industries means one bad result hurts your total less.',
      'Higher possible returns usually come with bigger swings. Decide how much of a drop you could live with before you invest.',
    ],
    terms: ['Diversification', 'Volatility', 'ETF'],
  },
  {
    id: 'quotes',
    visual: 'day-range',
    level: 'beginner',
    minutes: 1,
    cta: { label: 'Open the Market tab', route: '/market' },
    title: 'Reading a quote and chart',
    summary: 'What the numbers on the Market tab mean.',
    body: [
      'The price is what the stock last traded for. Open, high and low show where it started, peaked and bottomed today, and volume counts the shares traded.',
      'A chart plots price over time. Looking at a longer period shows the trend, while a single day shows mostly noise.',
    ],
    terms: ['Volume'],
  },
  {
    id: 'order-progress',
    visual: 'order-progress',
    level: 'intermediate',
    minutes: 2,
    cta: { label: 'Follow an order', route: '/orders' },
    title: 'What happens to your order',
    summary: 'Open, filled or rejected, and what each means.',
    body: [
      'After you place an order it is open: it is waiting to be executed. A market order usually fills within moments. A limit order stays open until the price reaches your limit, and an order placed while the market is closed waits for it to open.',
      'An order ends in one of two ways. Filled means it executed and your cash and shares were updated. Rejected means it was refused, for example because you did not have enough buying power, and nothing changed. Both are final.',
    ],
    terms: ['Fill', 'Market hours'],
  },
  {
    id: 'buying-power',
    level: 'intermediate',
    minutes: 2,
    cta: { label: 'Check your balance', route: '/dashboard' },
    title: 'Cash and buying power',
    summary: 'How much you can spend, and why it can be less than your balance.',
    body: [
      'Buying power is the cash you can still use for new buy orders. Money already committed to open buy orders is not available for a second order.',
      'If a buy order is larger than your buying power it is rejected, so check your balance before you place it.',
    ],
    terms: ['Buying power'],
  },
  {
    id: 'holdings',
    level: 'intermediate',
    minutes: 2,
    cta: { label: 'Open Holdings', route: '/holdings' },
    title: 'Reading your holdings',
    summary: 'Cost basis, current value and profit or loss.',
    body: [
      "Cost basis is what you paid for the shares you still hold. Current value is those shares at today's price.",
      'Profit or loss is the difference between the two. It is only a paper gain or loss until you sell, and it changes whenever the price does.',
    ],
    terms: ['Cost basis', 'Profit and loss (P&L)', 'Portfolio'],
  },
  {
    id: 'habits',
    level: 'beginner',
    minutes: 1,
    title: 'Long-term investing habits',
    summary: 'Patience, regular investing and keeping costs low.',
    body: [
      'Investing a steady amount on a regular schedule avoids guessing the best day to buy.',
      'Prices rise and fall in the short run. Staying invested through the dips, and not reacting to every headline, is what many long-term investors do.',
    ],
    terms: ['Dollar-cost averaging'],
  },
];

export const GLOSSARY: GlossaryTerm[] = [
  { term: 'Ask', definition: 'The lowest price a seller will accept right now.' },
  { term: 'Bid', definition: 'The highest price a buyer will pay right now.' },
  {
    term: 'Bull and bear market',
    definition: 'A bull market is a long stretch of rising prices; a bear market is a long stretch of falling ones.',
    match: ['bull market', 'bear market'],
  },
  { term: 'Diversification', definition: 'Spreading money across different investments to reduce the effect of any one of them doing badly.' },
  { term: 'Dividend', definition: "A share of a company's profit paid out to its shareholders." },
  { term: 'Limit order', definition: 'An order to buy or sell only at a set price or better.' },
  { term: 'Market cap', definition: "A company's total value: the share price times the number of shares." },
  { term: 'Market order', definition: 'An order to buy or sell right away at the best price available.' },
  { term: 'Portfolio', definition: 'Everything you own and invest in, taken together.' },
  { term: 'Share', definition: 'One unit of ownership in a company. Also called a stock.' },
  { term: 'Volatility', definition: 'How much and how quickly a price moves up and down.' },
  { term: 'Volume', definition: 'The number of shares traded in a period, usually a day.' },
  { term: 'Buying power', definition: 'The cash you can still use for new buy orders.' },
  { term: 'Cost basis', definition: 'What you paid for the shares you currently hold.' },
  { term: 'Dollar-cost averaging', definition: 'Investing a fixed amount on a regular schedule, whatever the price is that day.' },
  { term: 'ETF', definition: 'A fund that holds many stocks and trades like a single stock.' },
  { term: 'Fill', definition: 'The moment an order executes and cash and shares change hands.', match: ['filled'] },
  { term: 'Market hours', definition: 'The times of day the market is open. Orders placed outside them wait for the next open.' },
  {
    term: 'Profit and loss (P&L)',
    definition: 'How much a holding has gained or lost compared with what you paid.',
    match: ['P&L', 'profit or loss', 'profit and loss'],
  },
  { term: 'Spread', definition: 'The gap between the bid and the ask.' },
];
