# Comprehensive Testing Plan for Shelly-Emulator

## Overview

Add comprehensive unit tests and integration tests to the shelly-emulator Spring Boot application. The emulator transforms MQTT messages from Sungather (solar/battery monitoring) to Shelly Pro 3 EM format, maintaining cumulative energy totals via InfluxDB.

## Implementation Phases

### Phase 1: Setup Testing Infrastructure

#### 1.1 Add Test Dependencies to pom.xml
Add the following dependencies:
- **TestContainers** (1.19.8) - For InfluxDB integration tests
- **Moquette MQTT Broker** (0.17) - Embedded MQTT broker for integration tests
- **AssertJ** (3.26.0) - Fluent assertions
- **Awaitility** (4.2.1) - Async testing support
- **JaCoCo** (0.8.12) - Code coverage reporting

#### 1.2 Configure Maven Build
- Configure maven-surefire-plugin to exclude integration tests
- Configure maven-failsafe-plugin for integration tests (*IntegrationTest.java)
- Add JaCoCo plugin for code coverage reports

#### 1.3 Create Test Utilities
Create helper classes to support testing:
- **TestDataBuilder** - Builder methods for test data (SungatherMessage, FluxRecords)
- **MqttTestHelper** - Embedded Moquette broker management and message capture
- **InfluxDBTestHelper** - InfluxDB test data setup and verification
- **BaseIntegrationTest** - Abstract base class with common TestContainer setup

#### 1.4 Create Test Resources
- `src/test/resources/application-test.properties` - Test configuration
- `src/test/resources/test-data/` - Sample JSON test messages

### Phase 2: Unit Tests

#### 2.1 Model Tests
**File:** `src/test/java/.../model/SungatherMessageTest.java`
- Test `getDailyConsumption()` calculation (kWh to Wh conversion)
- Test JSON deserialization with valid/invalid data
- Test with zero, fractional, and large values

**File:** `src/test/java/.../model/ShellyMessageTest.java`
- Test ShellyMessageCurrent and ShellyMessageTotal JSON serialization
- Verify property names match expected Shelly format

#### 2.2 MessageTransformer Tests (Most Critical)
**File:** `src/test/java/.../logic/MessageTransformerTest.java`

Test scenarios:
1. **First run** - No data in InfluxDB, initializes with Instant.EPOCH and 0.0
2. **Application restart - same day** - Fetches last measurement from today, doesn't add to total
3. **Application restart - previous day** - Fetches yesterday's data, adds current daily consumption
4. **Day rollover** - Detects negative difference, starts new day cumulative calculation
5. **Normal operation** - Adds incremental difference to cumulative total
6. **MQTT publishing** - Verifies correct topics, QoS, and message format
7. **InfluxDB error handling** - Handles empty results, multiple tables, multiple records
8. **Edge cases** - Zero values, large values, multiple messages in sequence

Mocking strategy:
- Mock `InfluxDBClient` and `QueryApi` to control responses
- Mock `IMqttClient` to capture published messages
- Use real `ObjectMapper` for JSON validation

#### 2.3 SungatherMessageHandler Tests
**File:** `src/test/java/.../mqtt/SungatherMessageHandlerTest.java`
- Test successful JSON deserialization
- Test invalid JSON handling and error logging
- Test message delegation to MessageTransformer
- Test MQTT subscription setup

#### 2.4 Configuration Tests
**File:** `src/test/java/.../config/MqttConfigTest.java`
- Test configuration properties binding from application.properties

**File:** `src/test/java/.../mqtt/MqttConfigurationTest.java`
- Test MQTT client bean creation with correct broker URL
- Test ObjectMapper bean creation

### Phase 3: Integration Tests

#### 3.1 End-to-End Integration Test
**File:** `src/test/java/.../integration/ShellyEmulatorIntegrationTest.java`

Test scenarios:
1. **Complete message flow** - Publish Sungather message, verify Shelly messages published
2. **Day rollover scenario** - Verify cumulative calculation across day boundary
3. **Application restart scenario** - Restart context, verify continuation from InfluxDB
4. **Error recovery** - Handle InfluxDB/MQTT failures gracefully

Setup:
- Use `@SpringBootTest` with `webEnvironment = NONE`
- Use `@Testcontainers` for InfluxDB
- Use Moquette embedded MQTT broker
- Use Awaitility for async message verification

#### 3.2 MQTT Integration Test
**File:** `src/test/java/.../integration/MqttIntegrationTest.java`
- Test MQTT connectivity and subscriptions
- Validate message format matches Shelly specification
- Test concurrent message handling

#### 3.3 InfluxDB Integration Test
**File:** `src/test/java/.../integration/InfluxDBIntegrationTest.java`
- Test `fetchLastMeasurement()` with various scenarios
- Verify data persistence and query accuracy
- Test with no data, old data (>1 year)

### Phase 4: Code Coverage and Refinement
- Run JaCoCo to generate coverage reports
- Target 80%+ code coverage
- Add missing test cases for uncovered paths
- Document complex test scenarios

## Critical Files to Modify

**Main file to update:**
- `E:\Programming\GIT\shelly-pro-3-em\shelly-emulator\pom.xml`

**Files under test:**
- `E:\Programming\GIT\shelly-pro-3-em\shelly-emulator\src\main\java\com\github\n4zroth\sungather\shellyemulator\logic\MessageTransformer.java` (lines 32-112)
- `E:\Programming\GIT\shelly-pro-3-em\shelly-emulator\src\main\java\com\github\n4zroth\sungather\shellyemulator\mqtt\SungatherMessageHandler.java` (lines 17-42)
- `E:\Programming\GIT\shelly-pro-3-em\shelly-emulator\src\main\java\com\github\n4zroth\sungather\shellyemulator\model\SungatherMessage.java` (lines 7-17)

## Test Structure

```
src/test/java/com/github/n4zroth/sungather/shellyemulator/
├── config/
│   ├── MqttConfigTest.java
│   └── TestConfiguration.java
├── fixtures/
│   └── TestDataBuilder.java
├── integration/
│   ├── BaseIntegrationTest.java
│   ├── ShellyEmulatorIntegrationTest.java
│   ├── MqttIntegrationTest.java
│   └── InfluxDBIntegrationTest.java
├── logic/
│   └── MessageTransformerTest.java
├── model/
│   ├── SungatherMessageTest.java
│   └── ShellyMessageTest.java
├── mqtt/
│   ├── MqttConfigurationTest.java
│   └── SungatherMessageHandlerTest.java
└── testutil/
    ├── MqttTestHelper.java
    └── InfluxDBTestHelper.java
```

## Key Testing Considerations

1. **Stateful Components** - MessageTransformer maintains state; reset between tests
2. **Time-based Logic** - Day rollover uses `Instant.now()`; use fixed test data
3. **MQTT Async** - Use Awaitility with timeouts for message arrival
4. **InfluxDB Queries** - Mock FluxTable/FluxRecord structure carefully
5. **Test Isolation** - Use `@DirtiesContext` for integration tests

## Expected Outcomes

- **80%+ code coverage** across the codebase
- **Comprehensive unit tests** for all business logic, especially MessageTransformer
- **End-to-end integration tests** verifying complete MQTT → Transform → MQTT flow
- **Reliable test suite** using TestContainers and embedded MQTT broker
- **Fast feedback** with separated unit (surefire) and integration (failsafe) test execution
