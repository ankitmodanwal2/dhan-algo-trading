package com.trading.service;

import com.trading.model.SecurityMaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 7) {
                    SecurityMaster security = new SecurityMaster();
                    security.setSecurityId(parts[0].trim());
                    security.setExchangeSegment(parts[1].trim());
                    security.setTradingSymbol(parts[2].trim());
                    security.setName(parts[3].trim());

                    try {
                        security.setLotSize(Integer.parseInt(parts[5].trim()));
                    } catch (NumberFormatException e) {
                        security.setLotSize(1);
                    }

                    try {
                        security.setTickSize(Double.parseDouble(parts[6].trim()));
                    } catch (NumberFormatException e) {
                        security.setTickSize(0.05);
                    }

                    securityList.add(security);
                }
            }

            reader.close();
            log.info("Loaded {} securities from Dhan", securityList.size());

        } catch (Exception e) {
            log.error("Failed to load security master: {}", e.getMessage());
        }
    }

    public List<SecurityMaster> searchSymbols(String query, String exchange, int limit) {
        String searchQuery = query.toUpperCase();

        return securityList.stream()
                .filter(s -> {
                    boolean matchesExchange = exchange == null ||
                            exchange.isEmpty() ||
                            s.getExchangeSegment().startsWith(exchange);

                    boolean matchesQuery = s.getTradingSymbol().contains(searchQuery) ||
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