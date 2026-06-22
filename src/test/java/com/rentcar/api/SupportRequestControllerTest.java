package com.rentcar.api;

import com.rentcar.api.domain.support.SupportRequestStatus;
import com.rentcar.api.domain.support.SupportRequestTopic;
import com.rentcar.api.repository.SupportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Test
    void createSupportRequest_validPayload_storesOpenRequest() throws Exception {
        long before = supportRequestRepository.count();

        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "BOOKING",
                                  "bookingReference": "PD-1234",
                                  "email": "customer@example.com",
                                  "phoneCountryCode": "+34",
                                  "phoneNumber": "600 000 000",
                                  "message": "Can you confirm my pickup details?"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.topic").value("BOOKING"))
                .andExpect(jsonPath("$.bookingReference").value("PD-1234"))
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.phoneCountryCode").value("+34"))
                .andExpect(jsonPath("$.phoneNumber").value("600 000 000"))
                .andExpect(jsonPath("$.message").value("Can you confirm my pickup details?"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertThat(supportRequestRepository.count()).isEqualTo(before + 1);
        var saved = supportRequestRepository.findAll().getLast();
        assertThat(saved.getTopic()).isEqualTo(SupportRequestTopic.BOOKING);
        assertThat(saved.getBookingReference()).isEqualTo("PD-1234");
        assertThat(saved.getEmail()).isEqualTo("customer@example.com");
        assertThat(saved.getPhoneCountryCode()).isEqualTo("+34");
        assertThat(saved.getPhoneNumber()).isEqualTo("600 000 000");
        assertThat(saved.getMessage()).isEqualTo("Can you confirm my pickup details?");
        assertThat(saved.getStatus()).isEqualTo(SupportRequestStatus.OPEN);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createSupportRequest_withoutAuth_reachesValidation() throws Exception {
        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message", containsString("topic")))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.message", containsString("phoneCountryCode")))
                .andExpect(jsonPath("$.message", containsString("phoneNumber")))
                .andExpect(jsonPath("$.message", containsString("message")));
    }

    @Test
    void createSupportRequest_missingPhoneCountryCode_returns400() throws Exception {
        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "BOOKING",
                                  "bookingReference": "PD-NO-CC",
                                  "email": "customer@example.com",
                                  "phoneNumber": "600 000 000",
                                  "message": "Can you confirm my pickup details?"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message", containsString("phoneCountryCode")));
    }

    @Test
    void createSupportRequest_missingPhoneNumber_returns400() throws Exception {
        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "BOOKING",
                                  "bookingReference": "PD-NO-PHONE",
                                  "email": "customer@example.com",
                                  "phoneCountryCode": "+34",
                                  "message": "Can you confirm my pickup details?"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message", containsString("phoneNumber")));
    }

    @Test
    void createSupportRequest_invalidEmailAndLongFields_returns400() throws Exception {
        String longReference = "R".repeat(51);
        String longMessage = "M".repeat(501);

        mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "PAYMENT",
                                  "bookingReference": "%s",
                                  "email": "not-an-email",
                                  "phoneCountryCode": "+34",
                                  "phoneNumber": "600 000 000",
                                  "message": "%s"
                                }
                                """.formatted(longReference, longMessage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message", containsString("bookingReference")))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.message", containsString("message")));
    }
}
