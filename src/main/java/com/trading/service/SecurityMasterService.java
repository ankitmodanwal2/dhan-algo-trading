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

            try (CSVReader reader = new CSVReader(new InputStreamReader(url.openStream()))) {
                String[] parts;
                reader.readNext(); // Skip header row

                while ((parts = reader.readNext()) != null) {
                    // Dhan Compact CSV Format:
                    // 0: SEM_EXM_EXCH_ID (Exchange)
                    // 1: SEM_SEGMENT (Segment)
                    // 2: SEM_SMST_SECURITY_ID (Security ID)
                    // 3: SEM_GMT_INSTRUMENT_TYPE (Instrument Type)
                    // 4: SEM_INSTRUMENT_NAME (Name)
                    // 5: SEM_TRADING_SYMBOL (Symbol)
                    // 6: SEM_EXPIRY_DATE (Expiry)
                    // 7: SEM_TICK_SIZE (Tick Size)
                    // 8: SEM_LOT_UNITS (Lot Size)

                    if (parts.length >= 6) {
                        SecurityMaster security = new SecurityMaster();
                        security.setExchangeSegment(parts[0].trim()); // e.g., NSE
                        security.setSecurityId(parts[2].trim());
                        security.setInstrumentType(parts[3].trim());  // e.g., EQUITY, OPTIDX
                        security.setName(parts[4].trim());
                        security.setTradingSymbol(parts[5].trim());

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
                    // Filter by Exchange (e.g., NSE)
                    boolean matchesExchange = exchange == null ||
                            exchange.isEmpty() ||
                            (s.getExchangeSegment() != null && s.getExchangeSegment().startsWith(exchange));

                    // Filter by Query in Symbol or Name
                    boolean matchesQuery = (s.getTradingSymbol() != null && s.getTradingSymbol().contains(searchQuery)) ||
                            (s.getName() != null && s.getName().toUpperCase().contains(searchQuery));

                    return matchesExchange && matchesQuery;
                })
                // 🌟 FIX: Sort to prioritize Equities and Exact Matches 🌟
                .sorted((s1, s2) -> {
                    // 1. Prioritize Exact Match
                    boolean s1Exact = s1.getTradingSymbol().equals(searchQuery);
                    boolean s2Exact = s2.getTradingSymbol().equals(searchQuery);
                    if (s1Exact && !s2Exact) return -1;
                    if (!s1Exact && s2Exact) return 1;

                    // 2. Prioritize EQUITY Instrument Type
                    boolean s1Equity = "EQUITY".equals(s1.getInstrumentType());
                    boolean s2Equity = "EQUITY".equals(s2.getInstrumentType());
                    if (s1Equity && !s2Equity) return -1;
                    if (!s1Equity && s2Equity) return 1;

                    // 3. Prioritize Shorter Symbols (likely underlying)
                    int lenCompare = Integer.compare(s1.getTradingSymbol().length(), s2.getTradingSymbol().length());
                    if (lenCompare != 0) return lenCompare;

                    // 4. Alphabetical Order
                    return s1.getTradingSymbol().compareTo(s2.getTradingSymbol());
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