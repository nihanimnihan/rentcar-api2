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
 * Integration tests for /api/admin/cars.
 *
 * Admin endpoints require ADMIN credentials.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCarCrudTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String BASE = "/api/admin/cars";

    private static final String RENTAL_CAR_JSON = """
            {
              "brand": "Toyota",
              "model": "Corolla",
              "segment": "ECONOMY",
              "vehicleType": "SEDAN",
              "transmission": "AUTOMATIC",
              "fuelType": "GASOLINE",
              "seats": 5,
              "bags": 2,
              "doors": 4,
              "minDriverAge": 21,
              "airConditioning": true,
              "premium": false,
              "guaranteedModel": false,
              "dailyPrice": 49.99,
              "active": true,
              "displayClass": "Economy Sedan",
              "chauffeurAvailable": false
            }
            """;

    @Test
    void listCars_returnsArray() throws Exception {
        mockMvc.perform(get(BASE).with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createRentalCar_returns201() throws Exception {
        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENTAL_CAR_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.model").value("Corolla"))
                .andExpect(jsonPath("$.chauffeurAvailable").value(false))
                .andExpect(jsonPath("$.chauffeurCategory").doesNotExist());
    }

    @Test
    void createCar_missingBrand_returns400() throws Exception {
        String json = """
                {
                  "model": "Corolla",
                  "segment": "ECONOMY",
                  "vehicleType": "SEDAN",
                  "transmission": "AUTOMATIC",
                  "fuelType": "GASOLINE",
                  "seats": 5,
                  "bags": 2,
                  "doors": 4,
                  "minDriverAge": 21,
                  "airConditioning": true,
                  "premium": false,
                  "guaranteedModel": false,
                  "dailyPrice": 49.99,
                  "active": true,
                  "displayClass": "Economy"
                }
                """;
        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChauffeurCar_withoutCategory_returns400() throws Exception {
        String json = """
                {
                  "brand": "Mercedes",
                  "model": "E-Class",
                  "segment": "LUXURY",
                  "vehicleType": "SEDAN",
                  "transmission": "AUTOMATIC",
                  "fuelType": "GASOLINE",
                  "seats": 5,
                  "bags": 3,
                  "doors": 4,
                  "minDriverAge": 25,
                  "airConditioning": true,
                  "premium": true,
                  "guaranteedModel": true,
                  "dailyPrice": 150.00,
                  "active": true,
                  "displayClass": "Executive",
                  "chauffeurAvailable": true
                }
                """;
        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChauffeurCar_withoutHourlyPrice_returns400() throws Exception {
        // Lookup a real category id from seed
        String categoriesJson = mockMvc.perform(get("/api/admin/chauffeur-categories").with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer categoryId = JsonPath.read(categoriesJson, "$[0].id");

        String json = """
                {
                  "brand": "Mercedes",
                  "model": "E-Class",
                  "segment": "LUXURY",
                  "vehicleType": "SEDAN",
                  "transmission": "AUTOMATIC",
                  "fuelType": "GASOLINE",
                  "seats": 5,
                  "bags": 3,
                  "doors": 4,
                  "minDriverAge": 25,
                  "airConditioning": true,
                  "premium": true,
                  "guaranteedModel": true,
                  "dailyPrice": 150.00,
                  "active": true,
                  "displayClass": "Executive",
                  "chauffeurAvailable": true,
                  "chauffeurCategoryId": %d
                }
                """.formatted(categoryId);

        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createChauffeurCar_withCategoryAndHourlyPrice_returns201() throws Exception {
        String categoriesJson = mockMvc.perform(get("/api/admin/chauffeur-categories").with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer categoryId = JsonPath.read(categoriesJson, "$[0].id");

        String json = """
                {
                  "brand": "Mercedes",
                  "model": "S-Class Test",
                  "segment": "LUXURY",
                  "vehicleType": "SEDAN",
                  "transmission": "AUTOMATIC",
                  "fuelType": "GASOLINE",
                  "seats": 4,
                  "bags": 3,
                  "doors": 4,
                  "minDriverAge": 25,
                  "airConditioning": true,
                  "premium": true,
                  "guaranteedModel": true,
                  "dailyPrice": 200.00,
                  "active": true,
                  "displayClass": "Executive",
                  "chauffeurAvailable": true,
                  "chauffeurCategoryId": %d,
                  "hourlyPrice": 95.00
                }
                """.formatted(categoryId);

        mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chauffeurAvailable").value(true))
                .andExpect(jsonPath("$.chauffeurCategory.id").value(categoryId))
                .andExpect(jsonPath("$.hourlyPrice").value(95.00));
    }

    @Test
    void updateCar_price_isReflected() throws Exception {
        // Create first
        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENTAL_CAR_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        // Update with higher price
        String update = RENTAL_CAR_JSON.replace("49.99", "79.99");
        mockMvc.perform(put(BASE + "/" + id).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyPrice").value(79.99));
    }

    @Test
    void patchActive_deactivatesCar() throws Exception {
        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENTAL_CAR_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mockMvc.perform(patch(BASE + "/" + id + "/active?value=false").with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivatedCar_notReturnedByPublicSearch() throws Exception {
        // Create + deactivate
        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENTAL_CAR_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mockMvc.perform(patch(BASE + "/" + id + "/active?value=false").with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk());

        // Public endpoint should not include this car
        mockMvc.perform(get("/api/cars/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    void deleteCar_softDeletesViaActive() throws Exception {
        String created = mockMvc.perform(post(BASE).with(httpBasic("admin", "change-me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RENTAL_CAR_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mockMvc.perform(delete(BASE + "/" + id).with(httpBasic("admin", "change-me")))
                .andExpect(status().isNoContent());

        // Still present in admin list (soft deleted = inactive)
        mockMvc.perform(get(BASE + "/" + id).with(httpBasic("admin", "change-me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
