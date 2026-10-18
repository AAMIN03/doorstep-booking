package com.booking.api.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

/** Asserts the full {@link com.booking.dto.ErrorResponse} JSON shape in MockMvc integration tests. */
public final class ApiErrorTestSupport {

    private ApiErrorTestSupport() {}

    /** Matchers for status, error, code, message, path, and timestamp. */
    public static ResultMatcher[] apiError(int httpStatus, String httpErrorName, String code, String message, String path) {
        return new ResultMatcher[] {
            status().is(httpStatus),
            jsonPath("$.status").value(httpStatus),
            jsonPath("$.error").value(httpErrorName),
            jsonPath("$.code").value(code),
            jsonPath("$.message").value(message),
            jsonPath("$.path").value(path),
            jsonPath("$.timestamp").exists()
        };
    }

    /** Applies {@link #apiError(int, String, String, String, String)} to the given {@link ResultActions}. */
    public static ResultActions expectApiError(
            ResultActions actions, int httpStatus, String httpErrorName, String code, String message, String path)
            throws Exception {
        for (ResultMatcher matcher : apiError(httpStatus, httpErrorName, code, message, path)) {
            actions.andExpect(matcher);
        }
        return actions;
    }
}
