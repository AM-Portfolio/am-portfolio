package com.portfolio.app.integration;

import com.portfolio.api.exception.ResponseStatusExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ensures ResponseStatusException keeps its HTTP status (403/400) instead of becoming 500.
 */
@WebMvcTest(controllers = ResponseStatusExceptionAdviceTest.TestController.class)
@ContextConfiguration(classes = {
        ResponseStatusExceptionAdvice.class,
        ResponseStatusExceptionAdviceTest.TestController.class
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("web-test")
class ResponseStatusExceptionAdviceTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestController {
        @GetMapping("/api/test/forbidden")
        public void forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of portfolio");
        }

        @GetMapping("/api/test/bad-request")
        public void badRequest() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown stress preset");
        }
    }

    @Test
    void whenForbidden_thenStatus403Not500() throws Exception {
        mockMvc.perform(get("/api/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Not owner of portfolio"));
    }

    @Test
    void whenBadRequest_thenStatus400Not500() throws Exception {
        mockMvc.perform(get("/api/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unknown stress preset"));
    }
}
