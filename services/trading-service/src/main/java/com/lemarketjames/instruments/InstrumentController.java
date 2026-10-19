package com.lemarketjames.instruments;

import com.lemarketjames.common.instruments.InstrumentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The supported stock list (contract C5: the instruments table is the list). The frontend loads it
 * from here instead of keeping its own copy, so adding or suspending a stock is a database change only.
 */
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

    private final InstrumentRepository instruments;

    public InstrumentController(InstrumentRepository instruments) {
        this.instruments = instruments;
    }

    /** Every stock, in id order (the market-cap order the migrations assign), suspended ones included. */
    @GetMapping
    public List<InstrumentDto> getAll() {
        return instruments.findAll(Sort.by("instrumentId")).stream()
                .map(InstrumentDto::from)
                .toList();
    }
}
