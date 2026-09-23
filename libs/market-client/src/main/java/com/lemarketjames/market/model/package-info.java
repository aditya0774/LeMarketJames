/**
 * Immutable market types and the pricing maths, free of Spring and of any mutable state so they
 * can be unit tested directly: the GBM formulas ({@link com.lemarketjames.market.model.GbmModel}),
 * trading hours, an instrument's definition, a price snapshot, and a completed candle.
 */
package com.lemarketjames.market.model;
