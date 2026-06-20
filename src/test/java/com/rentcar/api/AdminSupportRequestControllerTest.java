package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import com.rentcar.api.domain.support.SupportRequest;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminSupportRequestControllerTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "change-me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Test
    void listSupportRequests_returnsNewestFirstWithExpectedFields() throws Exception {
        SupportRequest older = createSupportRequest("older-list@example.com", "PD-OLDER", "Older support request");
        Thread.sleep(5);
        SupportRequest newer = createSupportRequest("newer-list@example.com", "PD-NEWER", "Newer support request");

        MvcResult result = mockMvc.perform(get("/api/admin/support-requests")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$.content[*].id");
        int olderIndex = ids.indexOf(older.getId().intValue());
        int newerIndex = ids.indexOf(newer.getId().intValue());

        assertThat(newerIndex).isLessThan(olderIndex);
        assertThat(json).contains(
                "\"topic\"",
                "\"email\"",
                "\"phoneCountryCode\"",
                "\"phoneNumber\"",
                "\"fullPhone\"",
                "\"bookingReference\"",
                "\"status\"",
                "\"createdAt\"");
        assertThat(json).doesNotContain("\"message\"");
    }

    @Test
    void listSupportRequests_returnsPaginationMetadata() throws Exception {
        createSupportRequest("page-one@example.com", "PD-PAGE-1", "Page one request");
        createSupportRequest("page-two@example.com", "PD-PAGE-2", "Page two request");

        mockMvc.perform(get("/api/admin/support-requests")
                        .param("page", "0")
                        .param("size", "1")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void publicSubmittedSupportRequestAppearsInAdminList() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/support-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "PAYMENT",
                                  "bookingReference": "PD-PUBLIC",
                                  "email": "public-admin-list@example.com",
                                  "phoneCountryCode": "+34",
                                  "phoneNumber": "600 000 000",
                                  "message": "Payment support request from the public site."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        int createdId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/admin/support-requests")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].topic".formatted(createdId), hasItem("PAYMENT")))
                .andExpect(jsonPath("$.content[?(@.id == %d)].email".formatted(createdId), hasItem("public-admin-list@example.com")))
                .andExpect(jsonPath("$.content[?(@.id == %d)].phoneCountryCode".formatted(createdId), hasItem("+34")))
                .andExpect(jsonPath("$.content[?(@.id == %d)].phoneNumber".formatted(createdId), hasItem("600 000 000")))
                .andExpect(jsonPath("$.content[?(@.id == %d)].fullPhone".formatted(createdId), hasItem("+34 600 000 000")))
                .andExpect(jsonPath("$.content[?(@.id == %d)].bookingReference".formatted(createdId), hasItem("PD-PUBLIC")))
                .andExpect(jsonPath("$.content[?(@.id == %d)].status".formatted(createdId), hasItem("OPEN")));
    }

    @Test
    void getSupportRequest_returnsFullDetails() throws Exception {
        SupportRequest supportRequest = createSupportRequest(
                "details-support@example.com",
                "PD-DETAIL",
                "Please help me update my booking.");

        mockMvc.perform(get("/api/admin/support-requests/{id}", supportRequest.getId())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supportRequest.getId().intValue()))
                .andExpect(jsonPath("$.topic").value("BOOKING"))
                .andExpect(jsonPath("$.email").value("details-support@example.com"))
                .andExpect(jsonPath("$.phoneCountryCode").value("+34"))
                .andExpect(jsonPath("$.phoneNumber").value("600 000 000"))
                .andExpect(jsonPath("$.fullPhone").value("+34 600 000 000"))
                .andExpect(jsonPath("$.bookingReference").value("PD-DETAIL"))
                .andExpect(jsonPath("$.message").value("Please help me update my booking."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void resolveSupportRequest_marksOpenRequestResolvedAndUpdatesTimestamp() throws Exception {
        SupportRequest supportRequest = createSupportRequest(
                "resolve-support@example.com",
                "PD-RESOLVE",
                "This request should be resolved.");
        Instant previousUpdatedAt = supportRequest.getUpdatedAt();
        Thread.sleep(5);

        mockMvc.perform(post("/api/admin/support-requests/{id}/resolve", supportRequest.getId())
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supportRequest.getId().intValue()))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.updatedAt").exists());

        SupportRequest updated = supportRequestRepository.findById(supportRequest.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SupportRequestStatus.RESOLVED);
        assertThat(updated.getUpdatedAt()).isAfter(previousUpdatedAt);
    }

    @Test
    void listSupportRequests_allFilterSortsOpenFirstThenResolvedEachNewestFirst() throws Exception {
        SupportRequest openOlder = createSupportRequest("all-open-older@example.com", "PD-ALL-OPEN-OLDER", "Open older");
        Thread.sleep(5);
        SupportRequest resolvedOlder = createSupportRequest("all-resolved-older@example.com", "PD-ALL-RES-OLDER", "Resolved older", SupportRequestStatus.RESOLVED);
        Thread.sleep(5);
        SupportRequest openNewer = createSupportRequest("all-open-newer@example.com", "PD-ALL-OPEN-NEWER", "Open newer");
        Thread.sleep(5);
        SupportRequest resolvedNewer = createSupportRequest("all-resolved-newer@example.com", "PD-ALL-RES-NEWER", "Resolved newer", SupportRequestStatus.RESOLVED);

        MvcResult result = mockMvc.perform(get("/api/admin/support-requests")
                        .param("page", "0")
                        .param("size", "100")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].id");
        assertThat(ids.indexOf(openNewer.getId().intValue())).isLessThan(ids.indexOf(openOlder.getId().intValue()));
        assertThat(ids.indexOf(openOlder.getId().intValue())).isLessThan(ids.indexOf(resolvedNewer.getId().intValue()));
        assertThat(ids.indexOf(resolvedNewer.getId().intValue())).isLessThan(ids.indexOf(resolvedOlder.getId().intValue()));
    }

    @Test
    void listSupportRequests_openFilterSortsNewestFirst() throws Exception {
        SupportRequest older = createSupportRequest("open-older@example.com", "PD-OPEN-OLDER", "Open older");
        Thread.sleep(5);
        SupportRequest newer = createSupportRequest("open-newer@example.com", "PD-OPEN-NEWER", "Open newer");
        createSupportRequest("resolved-hidden-open@example.com", "PD-HIDDEN-OPEN", "Resolved hidden", SupportRequestStatus.RESOLVED);

        MvcResult result = mockMvc.perform(get("/api/admin/support-requests")
                        .param("status", "OPEN")
                        .param("size", "100")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].id");
        assertThat(ids).contains(newer.getId().intValue(), older.getId().intValue());
        assertThat(ids.indexOf(newer.getId().intValue())).isLessThan(ids.indexOf(older.getId().intValue()));
    }

    @Test
    void listSupportRequests_resolvedFilterSortsNewestFirst() throws Exception {
        SupportRequest older = createSupportRequest("resolved-older@example.com", "PD-RES-OLDER", "Resolved older", SupportRequestStatus.RESOLVED);
        Thread.sleep(5);
        SupportRequest newer = createSupportRequest("resolved-newer@example.com", "PD-RES-NEWER", "Resolved newer", SupportRequestStatus.RESOLVED);
        createSupportRequest("open-hidden-resolved@example.com", "PD-HIDDEN-RESOLVED", "Open hidden");

        MvcResult result = mockMvc.perform(get("/api/admin/support-requests")
                        .param("status", "RESOLVED")
                        .param("size", "100")
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isOk())
                .andReturn();

        List<Integer> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].id");
        assertThat(ids).contains(newer.getId().intValue(), older.getId().intValue());
        assertThat(ids.indexOf(newer.getId().intValue())).isLessThan(ids.indexOf(older.getId().intValue()));
    }

    @Test
    void resolveSupportRequest_nonExistingRequestReturns404() throws Exception {
        mockMvc.perform(post("/api/admin/support-requests/{id}/resolve", 999_999_999L)
                        .with(httpBasic(ADMIN_USER, ADMIN_PASS)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Support request not found"));
    }

    private SupportRequest createSupportRequest(String email, String bookingReference, String message) {
        return createSupportRequest(email, bookingReference, message, SupportRequestStatus.OPEN);
    }

    private SupportRequest createSupportRequest(
            String email,
            String bookingReference,
            String message,
            SupportRequestStatus status
    ) {
        return supportRequestRepository.save(SupportRequest.builder()
                .topic(SupportRequestTopic.BOOKING)
                .bookingReference(bookingReference)
                .email(email)
                .phoneCountryCode("+34")
                .phoneNumber("600 000 000")
                .message(message)
                .status(status)
                .build());
    }
}
