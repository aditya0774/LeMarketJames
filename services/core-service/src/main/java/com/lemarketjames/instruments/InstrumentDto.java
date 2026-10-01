package com.lemarketjames.instruments;

import com.lemarketjames.common.instruments.Instrument;

/**
 * One supported stock as the frontend sees it.
 *
 * @param instrumentId the id orders and holdings refer to
 * @param symbol       the ticker
 * @param name         the display name
 * @param tradable     false when the stock is suspended
 */
public record InstrumentDto(Integer instrumentId, String symbol, String name, boolean tradable) {

    static InstrumentDto from(Instrument instrument) {
        return new InstrumentDto(instrument.getInstrumentId(), instrument.getTicker(), instrument.getName(),
                instrument.isTradable());
    }
}
