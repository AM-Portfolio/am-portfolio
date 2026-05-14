package com.portfolio.app.integration;

import com.portfolio.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for GlobalExceptionHandler.
 * Uses @WebMvcTest to load only the web layer with a minimal test controller,
 * avoiding the need for full Spring context or any infrastructure.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerIntegrationTest.TestExceptionController.class)
@ContextConfiguration(classes = {GlobalExceptionHandler.class, GlobalExceptionHandlerIntegrationTest.TestExceptionController.class})
@ActiveProfiles("web-test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestExceptionController {
        @GetMapping("/api/test/runtime-exception")
        public void throwRuntime() {
            throw new RuntimeException("Custom Runtime Exception");
        }

        @GetMapping("/api/test/checked-exception")
        public void throwChecked() throws Exception {
            throw new Exception("Custom Checked Exception");
        }
    }

    @Test
    void whenRuntimeExceptionThrown_thenStatus500AndJsonBody() throws Exception {
        mockMvc.perform(get("/api/test/runtime-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal Server Error: Custom Runtime Exception"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void whenCheckedExceptionThrown_thenStatus500AndGenericMessage() throws Exception {
        mockMvc.perform(get("/api/test/checked-exception"))
                .andExpect(status().isInternalServerError());
    }
}
