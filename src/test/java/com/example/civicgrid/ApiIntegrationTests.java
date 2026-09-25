package com.example.civicgrid;

import com.example.civicgrid.entity.StreetLight;
import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.ZoneRepository;
import com.example.civicgrid.service.StreetLightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ApiIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private StreetLightService streetLightService;

    @Test
    void testApi1RegisterStreetLightSuccess() throws Exception {
        Zone zone = zoneRepository.findAll().get(0);
        String poleCode = "API-POLE-" + System.currentTimeMillis();

        String payload = String.format("""
                {
                    "poleCode": "%s",
                    "zoneId": %d,
                    "dimmingPercentage": 70
                }
                """, poleCode, zone.getId());

        mockMvc.perform(post("/api/street-lights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.poleCode").value(poleCode))
                .andExpect(jsonPath("$.dimmingPercentage").value(70))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void testApi1RegisterStreetLightInvalidDimming() throws Exception {
        Zone zone = zoneRepository.findAll().get(0);
        String payload = String.format("""
                {
                    "poleCode": "BAD-DIM-POLE",
                    "zoneId": %d,
                    "dimmingPercentage": 120
                }
                """, zone.getId());

        mockMvc.perform(post("/api/street-lights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.dimmingPercentage").exists());
    }

    @Test
    void testApi2SetDimming() throws Exception {
        Zone zone = zoneRepository.findAll().get(0);
        String poleCode = "DIM-API-" + System.currentTimeMillis();
        StreetLight light = streetLightService.registerLight(poleCode, zone.getId(), 50);

        String payload = """
                {
                    "dimmingPercentage": 85
                }
                """;

        mockMvc.perform(put("/api/street-lights/" + light.getId() + "/dimming")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(light.getId()))
                .andExpect(jsonPath("$.dimmingPercentage").value(85));
    }

    @Test
    void testApi3GetPowerSummary() throws Exception {
        mockMvc.perform(get("/api/street-lights/power-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLights").isNumber())
                .andExpect(jsonPath("$.totalPowerDrawWatts").isNumber())
                .andExpect(jsonPath("$.averagePowerDrawWatts").isNumber());
    }

    @Test
    void testAccountantDataEntryVendorBill() throws Exception {
        Zone zone = zoneRepository.findAll().get(0);
        String payload = String.format("""
                {
                    "type": "VENDOR_BILL",
                    "amount": 750.00,
                    "date": "2026-09-25",
                    "partyName": "Electric Utility",
                    "accountCode": "4010",
                    "zoneId": %d
                }
                """, zone.getId());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType").value("VENDOR_BILL"))
                .andExpect(jsonPath("$.documentNumber").exists())
                .andExpect(jsonPath("$.journalEntryNumber").exists())
                .andExpect(jsonPath("$.journalLines", hasSize(2)));
    }

    @Test
    void testAccountantDataEntryCustomerInvoice() throws Exception {
        String payload = """
                {
                    "type": "CUSTOMER_INVOICE",
                    "amount": 1200.00,
                    "date": "2026-09-25",
                    "partyName": "City Municipality",
                    "accountCode": "3010"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType").value("CUSTOMER_INVOICE"))
                .andExpect(jsonPath("$.documentNumber").exists())
                .andExpect(jsonPath("$.journalEntryNumber").exists())
                .andExpect(jsonPath("$.journalLines", hasSize(2)));
    }

    @Test
    void testAccountantDataEntryOutboundPayment() throws Exception {
        String payload = """
                {
                    "type": "PAYMENT",
                    "amount": 500.00,
                    "date": "2026-09-25",
                    "partyName": "Electric Utility",
                    "accountCode": "2010"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionType").value("PAYMENT"))
                .andExpect(jsonPath("$.journalLines", hasSize(2)));
    }
}
