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
import java.util.Iterator;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

public class APITest {

    // Declare ExtentReports and ExtentTest
    private static ExtentReports extent = new ExtentReports();
    private static ExtentTest test;

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

        // Set base URL for RestAssured
        RestAssured.baseURI = params.get("baseUrl").asText();
        String endpoint = params.get("endpoint").asText();

        // Prepare the request
        var request = given().contentType(params.has("headers") ? params.get("headers").get("Content-Type").asText() : "application/json");

        switch (method.toUpperCase()) {
            case "POST":
                response = request.body(params.get("request_body").toString()) // Convert JsonNode to String
                        .when()
                        .post(endpoint);
                break;

            case "GET":
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
            assertEquals(params.get("expected_status").asInt(), response.getStatusCode(),
                    "Expected status code " + params.get("expected_status").asInt() + " but got " + response.getStatusCode());
            test.pass("Status Code Validation Passed: " + params.get("expected_status").asInt());
        } catch (AssertionError e) {
            // Log the failure in the Extent report and mark the test as failed
            test.fail("Status Code Validation Failed: Expected " + params.get("expected_status").asInt() + " but got " + response.getStatusCode());
            throw e;  // Re-throw the exception to ensure JUnit also fails the test
        }

        // Validate response body fields
        if (params.has("expected_response_body")) {
            for (Iterator<String> it = params.get("expected_response_body").fieldNames(); it.hasNext(); ) {
                String key = it.next();
                JsonNode expectedValueNode = params.get("expected_response_body").get(key);

                if (expectedValueNode != null) {
                    // Call the method to assert JSON field values
                    assertJsonField(expectedValueNode, response, key);
                } else {
                    test.fail("Expected value for key '" + key + "' is null.");
                }
            }
        }

        // Log the response for the current test case
        test.info("Response for " + description + ": " + response.asString());
    }

    // Method to assert JSON field values and log results
    private void assertJsonField(JsonNode expectedValueNode, Response response, String jsonPath) {
        // Get the expected value and determine its type
        Object expectedValue = expectedValueNode.isNumber() ? expectedValueNode.asInt() : expectedValueNode.asText();

        // Get the actual value from the response
        Object actualValue = expectedValueNode.isNumber()
                ? response.jsonPath().getInt(jsonPath) // If expecting a number
                : response.jsonPath().getString(jsonPath); // If expecting a string

        // Handle type conversion for comparison
        if (expectedValue instanceof Number && actualValue instanceof String) {
            actualValue = Integer.parseInt((String) actualValue);
        } else if (expectedValue instanceof String && actualValue instanceof Number) {
            actualValue = actualValue.toString();
        }

        // Perform the assertion and log results
        try {
            assertEquals(expectedValue, actualValue, "Mismatch for JSON path: " + jsonPath);
            test.pass("Field validation passed for: " + jsonPath);
        } catch (AssertionError e) {
            test.fail("Field validation failed for: " + jsonPath + " - Expected: " + expectedValue + ", Actual: " + actualValue);
            throw e;  // Ensure JUnit fails as well
        }
    }

    // Method to load test data from JSON file
    public static Object[][] data() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode testCases = mapper.readTree(new File("src/main/resources/bookingAPITest.json"));

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

    // Add @AfterAll method to ensure report is generated
    @AfterAll
    public static void tearDown() {
        // Flush the report at the end of all tests
        extent.flush();
    }
}
