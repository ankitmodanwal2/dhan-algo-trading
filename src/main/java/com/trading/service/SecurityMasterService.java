package com.trading.service;

import com.opencsv.CSVReader;
import com.trading.model.SecurityMaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SecurityMasterService {

    private List<SecurityMaster> securityList = new ArrayList<>();
    private static final String SECURITY_MASTER_URL = "https://images.dhan.co/api-data/api-scrip-master.csv";

    @PostConstruct
    public void loadSecurityMaster() {
        try {
            log.info("Loading security master from Dhan...");
            URL url = new URL(SECURITY_MASTER_URL);

            // Use OpenCSV for robust parsing
            try (CSVReader reader = new CSVReader(new InputStreamReader(url.openStream()))) {
                String[] parts;
                reader.readNext(); // Skip header row

                while ((parts = reader.readNext()) != null) {
                    // Dhan Compact CSV Format (approximate mapping):
                    // 0: Exchange (SEM_EXM_EXCH_ID) e.g., "NSE"
                    // 1: Segment (SEM_SEGMENT) e.g., "E"
                    // 2: Security ID (SEM_SMST_SECURITY_ID) e.g., "1333"
                    // 3: Instrument Type (SEM_GMT_INSTRUMENT_TYPE) e.g., "EQUITY"
                    // 4: Instrument Name (SEM_INSTRUMENT_NAME) e.g., "HDFC Bank Ltd"
                    // 5: Trading Symbol (SEM_TRADING_SYMBOL) e.g., "HDFCBANK"

                    if (parts.length >= 6) {
                        SecurityMaster security = new SecurityMaster();

                        // Map parts[0] "NSE" to exchangeSegment so filter startsWith("NSE") works
                        security.setExchangeSegment(parts[0].trim());
                        security.setSecurityId(parts[2].trim());
                        security.setInstrumentType(parts[3].trim());
                        security.setName(parts[4].trim());
                        security.setTradingSymbol(parts[5].trim());

                        // Parse optional numeric fields safely
                        if (parts.length > 7) {
                            try {
                                security.setTickSize(Double.parseDouble(parts[7].trim()));
                            } catch (Exception e) {
                                security.setTickSize(0.05);
                            }
                        }

                        if (parts.length > 8) {
                            try {
                                security.setLotSize(Integer.parseInt(parts[8].trim()));
                            } catch (Exception e) {
                                security.setLotSize(1);
                            }
                        }

                        securityList.add(security);
                    }
                }
            }

            log.info("Loaded {} securities from Dhan", securityList.size());

        } catch (Exception e) {
            log.error("Failed to load security master: {}", e.getMessage());
        }
    }

    public List<SecurityMaster> searchSymbols(String query, String exchange, int limit) {
        String searchQuery = query.toUpperCase();

        return securityList.stream()
                .filter(s -> {
                    // Filter matches if Exchange (parts[0]) starts with "NSE" (derived from "NSE_EQ")
                    boolean matchesExchange = exchange == null ||
                            exchange.isEmpty() ||
                            (s.getExchangeSegment() != null && s.getExchangeSegment().startsWith(exchange));

                    boolean matchesQuery = (s.getTradingSymbol() != null && s.getTradingSymbol().contains(searchQuery)) ||
                            (s.getName() != null && s.getName().toUpperCase().contains(searchQuery));

                    return matchesExchange && matchesQuery;
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    public SecurityMaster getBySecurityId(String securityId) {
        return securityList.stream()
                .filter(s -> s.getSecurityId().equals(securityId))
                .findFirst()
                .orElse(null);
    }
}