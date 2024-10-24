package com.api.framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("data")
    public void executeApiTest(String method, String description, JsonNode params) {
        // Create a test in the report
        test = extent.createTest(description);

        // Log the details of the test
        test.info("Executing API Test: " + description);

        Response response;

        // Replace any static variables in the request body or endpoint
        JsonNode updatedParams = replaceStaticVariables(params);

        // Set base URL for RestAssured
        RestAssured.baseURI = updatedParams.get("baseUrl").asText();
        String endpoint = updatedParams.get("endpoint").asText();

        // Prepare the request
        var request = given().contentType(updatedParams.has("headers") ? updatedParams.get("headers").get("Content-Type").asText() : "application/json");

        switch (method.toUpperCase()) {
            case "POST":
                response = request.body(updatedParams.get("request_body").toString()) // Convert JsonNode to String
                        .when()
                        .post(endpoint);

                // Save dynamic variables from the response if "save_response" is specified
                String saveResponseField = updatedParams.has("save_response") ? updatedParams.get("save_response").asText() : null;
                if (saveResponseField != null) {
                    saveDynamicVariable(response, saveResponseField);
                }
                break;

            case "GET":
                // Replace any dynamic variables in the endpoint before making the request
                endpoint = replaceDynamicVariables(endpoint);
                response = request.when().get(endpoint);
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

    // Method to compare expected and actual JSON fields recursively and log results
    private void compareJsonFields(JsonNode expectedNode, JsonNode actualNode, String jsonPath) {
        if (expectedNode.isObject()) {
            // For each field in the expected JSON
            expectedNode.fieldNames().forEachRemaining(fieldName -> {
                String currentPath = jsonPath.isEmpty() ? fieldName : jsonPath + "." + fieldName;
                JsonNode expectedValue = expectedNode.get(fieldName);
                JsonNode actualValue = actualNode.get(fieldName);

                // If the field is missing in the actual response
                if (actualValue == null) {
                    test.fail("Missing field in response: " + currentPath);
                } else {
                    // Recursively check nested objects
                    compareJsonFields(expectedValue, actualValue, currentPath);
                }
            });
        } else {
            // Compare actual and expected values
            try {
                assertEquals(expectedNode.asText(), actualNode.asText(), "Mismatch for field: " + jsonPath);
                test.pass("Field validation passed for: " + jsonPath);
            } catch (AssertionError e) {
                test.fail("Field validation failed for: " + jsonPath + " - Expected: " + expectedNode.asText() + ", Actual: " + actualNode.asText());
                throw e;
            }
        }
    }

    // Method to load test data from JSON file
    public static Object[][] data() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testCases = mapper.readTree(new File("src/main/resources/bookingAPITest.json"));

        // Load static variables
        staticVariablesMap = mapper.convertValue(testCases.get("variables"), Map.class);

        // Create an array to hold the test data
        Object[][] testData = new Object[testCases.get("tests").size()][3];

        for (int i = 0; i < testCases.get("tests").size(); i++) {
            JsonNode testCase = testCases.get("tests").get(i);
            testData[i][0] = testCase.get("method").asText();
            testData[i][1] = testCase.get("description").asText();
            testData[i][2] = testCase.get("params");
        }

        return testData;
    }

    @AfterAll
    public static void tearDown() {
        // Flush the ExtentReports at the end
        extent.flush();
    }
}
