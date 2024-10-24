package com.api.framework;

public class APITestFramework {
    public static void main(String[] args) {
        // This can be used for setup or any additional configurations if needed
        System.out.println("API Test Framework Initialized.");
    }
}

//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.qameta.allure.Allure;
//import io.qameta.allure.Step;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.util.HashMap;
//import java.util.Map;
//
//public class APITestFramework {
//    private static final String JSON_TEMPLATE_PATH = "path_to_your_json_template.json";
//    private static final String REPORT_PATH = "path_to_your_report"; // Update if needed
//    private ObjectMapper objectMapper = new ObjectMapper();
//
//    public static void main(String[] args) {
//        APITestFramework framework = new APITestFramework();
//        framework.runTests();
//    }
//
//    public void runTests() {
//        try {
//            String jsonContent = new String(Files.readAllBytes(Paths.get(JSON_TEMPLATE_PATH)));
//            JsonNode apiTests = objectMapper.readTree(jsonContent).get("api_tests");
//
//            apiTests.fieldNames().forEachRemaining(api -> {
//                JsonNode functionalities = apiTests.get(api);
//                functionalities.fieldNames().forEachRemaining(method -> {
//                    JsonNode testCases = functionalities.get(method).get("test_cases");
//                    testCases.forEach(testCase -> executeTestCase(api, method, testCase));
//                });
//            });
//        } catch (IOException e) {
//            e.printStackTrace();
//            Allure.addAttachment("Setup Error", "Could not read JSON template: " + e.getMessage());
//        }
//    }
//
//    @Step("Executing test case for API: {api}, Method: {method}")
//    private void executeTestCase(String api, String method, JsonNode testCase) {
//        String url = "http://your.api.endpoint/" + api; // Modify the URL based on the API structure
//        Response response = null;
//
//        try {
//            switch (method) {
//                case "POST":
//                    response = RestAssured.given()
//                            .headers(getHeaders(testCase))
//                            .body(testCase.get("request_body").toString())
//                            .when()
//                            .post(url);
//                    break;
//
//                case "GET":
//                    response = RestAssured.given()
//                            .headers(getHeaders(testCase))
//                            .when()
//                            .get(url);
//                    break;
//
//                case "PATCH":
//                    response = RestAssured.given()
//                            .headers(getHeaders(testCase))
//                            .body(testCase.get("request_body").toString())
//                            .when()
//                            .patch(url);
//                    break;
//
//                case "DELETE":
//                    response = RestAssured.given()
//                            .headers(getHeaders(testCase))
//                            .when()
//                            .delete(url);
//                    break;
//            }
//
//            validateResponse(response, testCase);
//        } catch (Exception e) {
//            Allure.addAttachment("Test Failure", "Exception occurred: " + e.getMessage()); // Log failure in Allure
//        }
//    }
//
//    private void validateResponse(Response response, JsonNode testCase) {
//        int expectedStatus = testCase.get("expected_status").asInt();
//        String expectedErrorMessage = testCase.has("expected_error_message") ? testCase.get("expected_error_message").asText() : null;
//        String expectedResponseBody = testCase.has("expected_response_body") ? testCase.get("expected_response_body").toString() : null;
//
//        boolean statusCheck = response.getStatusCode() == expectedStatus;
//        boolean errorMessageCheck = expectedErrorMessage == null || response.jsonPath().getString("error").equals(expectedErrorMessage);
//        boolean responseBodyCheck = expectedResponseBody == null || response.asString().equals(expectedResponseBody);
//
//        if (statusCheck && errorMessageCheck && responseBodyCheck) {
//            Allure.addAttachment("Test Passed", "Test case passed: " + testCase.get("description").asText());
//        } else {
//            Allure.addAttachment("Test Failed", "Expected status " + expectedStatus + " but got " + response.getStatusCode());
//            if (expectedErrorMessage != null && !errorMessageCheck) {
//                Allure.addAttachment("Error Message Mismatch", "Expected error message '" + expectedErrorMessage + "' but got '" + response.jsonPath().getString("error") + "'");
//            } else if (expectedResponseBody != null && !responseBodyCheck) {
//                Allure.addAttachment("Response Body Mismatch", "Expected response body " + expectedResponseBody + " but got " + response.asString());
//            }
//        }
//    }
//
//    private Map<String, String> getHeaders(JsonNode testCase) {
//        Map<String, String> headers = new HashMap<>();
//        if (testCase.has("headers")) {
//            testCase.get("headers").fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
//        }
//        return headers;
//    }
//}
