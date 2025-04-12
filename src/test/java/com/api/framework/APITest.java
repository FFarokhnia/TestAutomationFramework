package com.api.framework;

import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import io.restassured.config.SSLConfig;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchema;


import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

public class APITest {

    // Declare ExtentReports and ExtentTest
    private static ExtentReports extent = new ExtentReports();
    private static ExtentTest test;
    private static Map<String, String> staticVariablesMap;  // To hold static variable values
    private static Map<String, String> dynamicVariablesMap = new HashMap<>(); // To hold dynamic variable values

    static {
        // Initialize ExtentSparkReporter to generate HTML report
        ExtentSparkReporter spark = new ExtentSparkReporter("target/ExtentReport.html");
        extent.attachReporter(spark);
    }



    static {
        RestAssured.config = RestAssured.config()
                .sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());
    }
    public class Constants {
        public static final String SCHEMA_BASE_PATH = "src/main/resources/schemas/";
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("data")
    public void executeApiTest(String testCaseID, String method, String description, JsonNode params) throws IOException {
        // Create a test in the report
        test = extent.createTest(testCaseID+"---"+description);

        // Log the details of the test
        test.info("Executing API Test: " + description);

        Response response;

        // Replace any static variables in the request body or endpoint
        JsonNode updatedParams = replaceStaticVariables(params);

        // Set base URL for RestAssured
        RestAssured.baseURI = updatedParams.get("baseUrl").asText();

        String endpoint = replaceDynamicVariables(updatedParams.get("endpoint").asText());
        String saveResponseField;
        // Prepare the request
        var request = given().contentType(updatedParams.has("headers") && updatedParams.get("headers").has("Content-Type") ? updatedParams.get("headers").get("Content-Type").asText() : "application/json");
        if (updatedParams.has("headers") && updatedParams.get("headers").has("Cookie"))
             request = request.header("Cookie", replaceDynamicVariables(updatedParams.get("headers").get("Cookie").asText()));
        if (updatedParams.has("headers") && updatedParams.get("headers").has("Authorization"))
            request = request.header("Authorization", replaceDynamicVariables(updatedParams.get("headers").get("Authorization").asText()));
        if (updatedParams.has("queryParams")) {
            JsonNode queryParams = updatedParams.get("queryParams");
            addQueryParamsToRequest(request,queryParams);
        }

        switch (method.toUpperCase()) {
            case "POST":
                response = request.body(updatedParams.get("request_body").toString()) // Convert JsonNode to String
                        .when()
                        .post(endpoint);

                // Save dynamic variables from the response if "save_response" is specified
                saveResponseField = updatedParams.has("save_response") ? updatedParams.get("save_response").asText() : null;
                if (saveResponseField != null) {
                    saveDynamicVariable(response, saveResponseField);
                }
                break;

            case "GET":
                response = request.when().get(endpoint);
                break;

            case "PUT":
               response = request.body(updatedParams.get("request_body").toString()) // Convert JsonNode to String
                        .when()
                        .put(endpoint);

                // Save dynamic variables from the response if "save_response" is specified
                saveResponseField = updatedParams.has("save_response") ? updatedParams.get("save_response").asText() : null;
                if (saveResponseField != null) {
                    saveDynamicVariable(response, saveResponseField);
                }
                break;
            case "PATCH":
                response = request.body(updatedParams.get("request_body").toString()) // Convert JsonNode to String
                        .when()
                        .patch(endpoint);

                // Save dynamic variables from the response if "save_response" is specified
                saveResponseField = updatedParams.has("save_response") ? updatedParams.get("save_response").asText() : null;
                if (saveResponseField != null) {
                    saveDynamicVariable(response, saveResponseField);
                }
                break;
            case "DELETE":
                response = request.when().delete(endpoint);
                break;
            default:
                throw new UnsupportedOperationException("HTTP method not supported: " + method);
        }

        // Log the response details
        test.info("Response Status Code: " + response.getStatusCode());
        test.info("Response Body: " + response.asString());

        // Validate the response status code and catch the failure if any
        try {
            assertEquals(updatedParams.get("expected_status").asInt(), response.getStatusCode(),
                    "Expected status code " + updatedParams.get("expected_status").asInt() + " but got " + response.getStatusCode());
            test.pass("Status Code Validation Passed: " + updatedParams.get("expected_status").asInt());
        } catch (AssertionError e) {
            test.fail("Status Code Validation Failed: Expected " + updatedParams.get("expected_status").asInt() + " but got " + response.getStatusCode());
            throw e;  // Re-throw the exception to ensure JUnit also fails the test
        }

        // Validate response body fields
        if (updatedParams.has("expected_response_body")) {
            JsonNode expectedResponseBody = updatedParams.get("expected_response_body");

            // Parse the actual response body as a JsonNode
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode actualResponseBody;
            try {
                actualResponseBody = objectMapper.readTree(response.asString());
            } catch (IOException e) {
                test.fail("Failed to parse response body as JSON.");
                throw new RuntimeException(e); // Stop further execution
            }

            // Compare expected and actual response bodies
            compareJsonFields(expectedResponseBody, actualResponseBody, "");
        }
        // Validate the JSON schema if provided in params
        if (updatedParams.has("expected_json_schema")) {
            String schemaFileName = updatedParams.get("expected_json_schema").asText();
            String fullSchemaPath = Constants.SCHEMA_BASE_PATH + schemaFileName;

//
//            // Log the schema file path for debugging
            System.out.println("Loading JSON schema from: " + fullSchemaPath);

            try {
                // Validate the response against the schema
                response.then().body(matchesJsonSchema(new File(fullSchemaPath)));
                test.pass("JSON Schema Validation Passed.");
            } catch (AssertionError e) {
                test.fail("JSON Schema Validation Failed: " + e.getMessage());
                throw e;
            }
        }
        // Validate response time
        if (updatedParams.has("expected_response_time")) {
            long expectedTimeMs = updatedParams.get("expected_response_time").asLong(); // time in milliseconds
            long actualTimeMs = response.getTime(); // get actual response time in ms

            test.info("Expected Response Time (ms): " + expectedTimeMs);
            test.info("Actual Response Time (ms): " + actualTimeMs);

            try {
                assertTrue(actualTimeMs <= expectedTimeMs, "Response time exceeded: " + actualTimeMs + "ms > " + expectedTimeMs + "ms");
                test.pass("Response Time Validation Passed: " + actualTimeMs + "ms <= " + expectedTimeMs + "ms");
            } catch (AssertionError e) {
                test.fail("Response Time Validation Failed: Actual time = " + actualTimeMs + "ms, Expected <= " + expectedTimeMs + "ms");
                throw e; // Let JUnit handle the failed assertion
            }
        }

    }



    // Replace static variables in JSON with actual values
    private JsonNode replaceStaticVariables(JsonNode params) {
        ObjectMapper mapper = new ObjectMapper();
        String jsonStr = params.toString();

        // Iterate over static variables and replace placeholders in the JSON string
        for (Map.Entry<String, String> entry : staticVariablesMap.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            jsonStr = jsonStr.replace(placeholder, entry.getValue());
        }
        // Replace the date placeholder with the current date
        String currentUTCDate = getCurrentUTCDate();
        jsonStr = jsonStr.replace("{{date_UTC}}", currentUTCDate);

        // Convert the updated string back to a JsonNode
        try {
            return mapper.readTree(jsonStr);
        } catch (IOException e) {
            throw new RuntimeException("Error while replacing static variables in JSON", e);
        }
    }

    // Replace dynamic variables in the endpoint or request body
    private String replaceDynamicVariables(String input) {
        for (Map.Entry<String, String> entry : dynamicVariablesMap.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            input = input.replace(placeholder, entry.getValue());
        }
        return input;
    }

    // Save dynamic variables from the API response
    private void saveDynamicVariable(Response response, String fieldName) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonResponse;
        try {
            jsonResponse = objectMapper.readTree(response.asString());

            // Check if the response has the field to save
            if (jsonResponse.has(fieldName)) {
                dynamicVariablesMap.put(fieldName, jsonResponse.get(fieldName).asText());
                test.info("Saved dynamic variable: " + fieldName + " = " + jsonResponse.get(fieldName).asText());
            } else {
                test.warning("Field " + fieldName + " not found in the response.");
            }
        } catch (IOException e) {
            test.fail("Failed to parse response body to save dynamic variable.");
            throw new RuntimeException(e); // Stop further execution
        }
    }

    private void addQueryParamsToRequest(RequestSpecification request, JsonNode queryParams) {
        if (queryParams.isObject()) {
            queryParams.fieldNames().forEachRemaining(fieldName -> {
                String value = queryParams.get(fieldName).asText();
                request.queryParam(fieldName, value);
            });
        } else {
            throw new IllegalArgumentException("Query parameters JSON must be an object");
        }

    }

// Method to compare JSON fields recursively and validate response fields
    private void compareJsonFields(JsonNode expectedNode, JsonNode actualNode, String jsonPath) {
        if (expectedNode.isObject()) {
            // For each field in the expected JSON
            expectedNode.fieldNames().forEachRemaining(fieldName -> {
                String currentPath = jsonPath.isEmpty() ? fieldName : jsonPath + "." + fieldName;
                JsonNode expectedValue = expectedNode.get(fieldName);
                JsonNode actualValue = actualNode.get(fieldName);

                // If the field is missing or unexpected in the actual response
                if (actualValue == null && expectedValue != null) {
                    test.fail("Missing field in response: " + currentPath);
                } else if (expectedValue == null && actualValue != null) {
                    test.fail("Unexpected field in response: " + currentPath);
                } else if (expectedValue != null) {
                    // Recursively check nested objects or compare scalar values
                    compareJsonFields(expectedValue, actualValue, currentPath);
                }
            });
        } else {
            // Handle nulls for scalar values
            if (expectedNode.isNull() && actualNode.isNull()) {
                test.pass("Field validation passed for: " + jsonPath + " (both are null)");
                return;
            } else if (expectedNode.isNull() || actualNode.isNull()) {
                test.fail("Field validation failed for: " + jsonPath + " - Expected: " + expectedNode + ", Actual: " + actualNode);
                return;
            }

            // Special handling for "dateUpdated" field
            if (jsonPath.endsWith("dateUpdated")) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                try {
                    LocalDateTime expectedDate = LocalDateTime.parse(expectedNode.asText(), formatter);
                    LocalDateTime actualDate = LocalDateTime.parse(actualNode.asText(), formatter);

                    long differenceInSeconds = Math.abs(Duration.between(expectedDate, actualDate).getSeconds());
                    int allowedDifference = 5; // Allow up to 5 seconds of difference
                    Assertions.assertTrue(differenceInSeconds <= allowedDifference,
                            "Mismatch for field: " + jsonPath + " - Difference in seconds: " + differenceInSeconds);
                    test.pass("Field validation passed for: " + jsonPath);
                } catch (Exception e) {
                    test.fail("Date parsing failed for field: " + jsonPath + " - Expected: " + expectedNode.asText() + ", Actual: " + actualNode.asText());
                    throw e;
                }
            } else {
                // Compare actual and expected scalar values
                try {
                    assertEquals(expectedNode.asText(), actualNode.asText(), "Mismatch for field: " + jsonPath);
                    test.pass("Field validation passed for: " + jsonPath);
                } catch (AssertionError e) {
                    test.fail("Field validation failed for: " + jsonPath + " - Expected: " + expectedNode.asText() + ", Actual: " + actualNode.asText());
                    throw e;
                }
            }
        }
    }


    // Method to load test data from JSON file
    public static Object[][] data() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
//        JsonNode testCases = mapper.readTree(new File("src/main/resources/SMSCenterAPI.json"));
        JsonNode testCases = mapper.readTree(new File("src/main/resources/bookingAPITest.json"));

        // Load static variables
        staticVariablesMap = mapper.convertValue(testCases.get("variables"), Map.class);

        // Create an array to hold the test data
        Object[][] testData = new Object[testCases.get("tests").size()][4];

        for (int i = 0; i < testCases.get("tests").size(); i++) {
            JsonNode testCase = testCases.get("tests").get(i);
            testData[i][0] = testCase.get("testCaseID").asText();
            testData[i][1] = testCase.get("method").asText();
            testData[i][2] = testCase.get("description").asText();
            testData[i][3] = testCase.get("params");
        }

        return testData;
    }

    // Method to get the current date in the required format
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public static String getCurrentUTCDate() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC"));
        return nowUtc.format(FORMATTER);

    }

    public static String getCurrentDate() {
        return LocalDateTime.now().format(FORMATTER);

    }


    @AfterAll
    public static void tearDown() {
        // Flush the ExtentReports at the end
        extent.flush();
    }
}
