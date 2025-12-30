package com.github.n4zroth.sungather.shellyemulator.fixtures;

import java.time.Instant;
import java.util.List;

import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builder class for creating test data objects.
 */
public class TestDataBuilder {

    /**
     * Creates a SungatherMessage with the specified parameters.
     */
    public static SungatherMessage createSungatherMessage(
            double batteryDischarge,
            double directConsumption,
            double importedEnergy,
            double currentLoad) {
        return new SungatherMessage(batteryDischarge, directConsumption, importedEnergy, currentLoad);
    }

    /**
     * Creates a SungatherMessage with default values for testing.
     */
    public static SungatherMessage createDefaultSungatherMessage() {
        return createSungatherMessage(5.0, 3.0, 2.0, 500.0);
    }

    /**
     * Creates a SungatherMessage with zero values.
     */
    public static SungatherMessage createZeroSungatherMessage() {
        return createSungatherMessage(0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Creates a SungatherMessage with large values.
     */
    public static SungatherMessage createLargeSungatherMessage() {
        return createSungatherMessage(100.0, 75.0, 50.0, 10000.0);
    }

    /**
     * Creates sample JSON for a valid Sungather message.
     */
    public static String createValidSungatherJson() {
        return """
                {
                    "daily_battery_discharge_energy": 5.0,
                    "daily_direct_energy_consumption": 3.0,
                    "daily_import_energy": 2.0,
                    "load_power_hybrid": 500.0
                }
                """;
    }

    /**
     * Creates sample JSON for an invalid Sungather message (missing required field).
     */
    public static String createInvalidSungatherJson() {
        return """
                {
                    "daily_battery_discharge_energy": 5.0,
                    "daily_import_energy": 2.0
                }
                """;
    }

    /**
     * Creates sample JSON for a malformed message.
     */
    public static String createMalformedJson() {
        return "{ invalid json content }}";
    }

    /**
     * Creates a mocked FluxTable with a single FluxRecord.
     */
    public static List<FluxTable> createFluxTableWithSingleRecord(Instant time, Double value) {
        FluxRecord record = mock(FluxRecord.class);
        when(record.getTime()).thenReturn(time);
        when(record.getValue()).thenReturn(value);

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(record));

        return List.of(table);
    }

    /**
     * Creates an empty FluxTable list (no data in InfluxDB).
     */
    public static List<FluxTable> createEmptyFluxTable() {
        return List.of();
    }

    /**
     * Creates multiple FluxTables (error scenario).
     */
    public static List<FluxTable> createMultipleFluxTables() {
        FluxTable table1 = mock(FluxTable.class);
        FluxTable table2 = mock(FluxTable.class);
        return List.of(table1, table2);
    }

    /**
     * Creates a FluxTable with multiple records (error scenario).
     */
    public static List<FluxTable> createFluxTableWithMultipleRecords() {
        FluxRecord record1 = mock(FluxRecord.class);
        FluxRecord record2 = mock(FluxRecord.class);

        FluxTable table = mock(FluxTable.class);
        when(table.getRecords()).thenReturn(List.of(record1, record2));

        return List.of(table);
    }
}
