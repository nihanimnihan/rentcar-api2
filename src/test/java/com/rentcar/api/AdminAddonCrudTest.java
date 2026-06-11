package com.rentcar.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET/POST/PUT/PATCH/DELETE /api/admin/addons.
 *
 * NOTE: /api/admin/** is currently permitAll (demo mode).
 * TODO before production: restore hasRole("ADMIN") and add auth assertions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminAddonCrudTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String BASE = "/api/admin/addons";

    // ── List ───────────────────────────────────────────────────────────────────

    @Test
    void listAddons_returnsArrayWithSeededAddons() throws Exception {
        mockMvc.perform(get(BASE).with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void listAddons_responseContainsExpectedFields() throws Exception {
        mockMvc.perform(get(BASE).with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].price").exists())
                .andExpect(jsonPath("$[0].pricingType").exists())
                .andExpect(jsonPath("$[0].active").exists())
                .andExpect(jsonPath("$[0].recommended").exists())
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @Test
    void createAddon_validRequest_returns201WithBody() throws Exception {
        String body = """
                {
                  "name": "Admin GPS Unit",
                  "nameEs": "GPS Admin",
                  "code": "ADMIN-GPS-001",
                  "description": "Premium GPS",
                  "price": 8.50,
                  "pricingType": "DAILY",
                  "recommended": false,
                  "active": true
                }
                """;

        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("ADMIN-GPS-001"))
                .andExpect(jsonPath("$.name").value("Admin GPS Unit"))
                .andExpect(jsonPath("$.pricingType").value("DAILY"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createAddon_missingName_returns400() throws Exception {
        String body = """
                {"code":"NONAME","price":5.00,"pricingType":"DAILY","active":true}
                """;

        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAddon_duplicateCode_returns409() throws Exception {
        String code = "DUP-CODE-" + System.currentTimeMillis();
        String body = """
                {"name":"First","code":"%s","price":1.00,"pricingType":"ONE_TIME","active":true}
                """.formatted(code);

        // Create first — should succeed
        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Create duplicate — should conflict
        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    @Test
    void updateAddon_changesFields() throws Exception {
        // Create
        String create = """
                {"name":"To Update","code":"UPD-%d","price":5.00,"pricingType":"DAILY","active":true}
                """.formatted(System.currentTimeMillis());

        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = JsonPath.parse(created).read("$.id", Long.class);
        String existingCode = JsonPath.parse(created).read("$.code");

        // Update
        String update = """
                {"name":"Updated Name","nameEs":"Nombre actualizado","code":"%s","price":12.00,"pricingType":"ONE_TIME","recommended":true,"active":true}
                """.formatted(existingCode);

        mockMvc.perform(put(BASE + "/" + id).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.nameEs").value("Nombre actualizado"))
                .andExpect(jsonPath("$.price").value(12.00))
                .andExpect(jsonPath("$.pricingType").value("ONE_TIME"))
                .andExpect(jsonPath("$.recommended").value(true));
    }

    // ── PATCH active ───────────────────────────────────────────────────────────

    @Test
    void patchActive_deactivatesAddon() throws Exception {
        String create = """
                {"name":"To Deactivate","code":"DEACT-%d","price":3.00,"pricingType":"ONE_TIME","active":true}
                """.formatted(System.currentTimeMillis());

        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = JsonPath.parse(created).read("$.id", Long.class);

        mockMvc.perform(patch(BASE + "/" + id + "/active?value=false").with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void patchActive_deactivatedAddonNotInPublicList() throws Exception {
        String create = """
                {"name":"Hide Me","code":"HIDE-%d","price":2.00,"pricingType":"DAILY","active":true}
                """.formatted(System.currentTimeMillis());

        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = JsonPath.parse(created).read("$.id", Long.class);

        mockMvc.perform(patch(BASE + "/" + id + "/active?value=false").with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk());

        // Must not appear in public active list
        mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    // ── Delete (soft) ──────────────────────────────────────────────────────────

    @Test
    void deleteAddon_softDeletesSetsInactive() throws Exception {
        String create = """
                {"name":"To Soft Delete","code":"SDEL-%d","price":7.00,"pricingType":"DAILY","active":true}
                """.formatted(System.currentTimeMillis());

        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = JsonPath.parse(created).read("$.id", Long.class);

        mockMvc.perform(delete(BASE + "/" + id).with(httpBasic("admin", "change-me")))
                .andExpect(status().isNoContent());

        // Still exists, but inactive
        mockMvc.perform(get(BASE + "/" + id).with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Not in public list
        mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    // ── Historical snapshot integrity ──────────────────────────────────────────

    @Test
    void updateAddon_doesNotChangePublicActiveList_count() throws Exception {
        // Create a dedicated active addon for this test
        String code = "COUNT-TEST-" + System.currentTimeMillis();
        String create = """
                {"name":"Count Test Addon","code":"%s","price":5.00,"pricingType":"DAILY","active":true}
                """.formatted(code);

        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(create))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = JsonPath.parse(created).read("$.id", Long.class);

        // Count active addons with this new addon included
        String before = mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int countBefore = ((java.util.List<?>) JsonPath.parse(before).read("$")).size();

        // Update price only — active stays true
        String update = """
                {"name":"Count Test Addon Updated","code":"%s","price":99.99,"pricingType":"DAILY","recommended":false,"active":true}
                """.formatted(code);

        mockMvc.perform(put(BASE + "/" + id).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(99.99));

        // Active count must be unchanged after price update
        String after = mockMvc.perform(get("/api/addons/active"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int countAfter = ((java.util.List<?>) JsonPath.parse(after).read("$")).size();

        org.assertj.core.api.Assertions.assertThat(countAfter).isEqualTo(countBefore);
    }
}
