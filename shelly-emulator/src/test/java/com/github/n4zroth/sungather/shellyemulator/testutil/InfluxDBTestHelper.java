package com.github.n4zroth.sungather.shellyemulator.testutil;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

/**
 * Helper class for managing InfluxDB test data.
 */
public class InfluxDBTestHelper {

    private final InfluxDBClient influxDBClient;
    private final String bucket;
    private final String organization;

    public InfluxDBTestHelper(InfluxDBClient influxDBClient, String bucket, String organization) {
        this.influxDBClient = influxDBClient;
        this.bucket = bucket;
        this.organization = organization;
    }

    /**
     * Writes a test measurement to InfluxDB.
     */
    public void writeMeasurement(Instant time, double totalPower) {
        Point point = Point
                .measurement("energy")
                .addTag("device", "total")
                .addField("total_act", totalPower)
                .time(time, WritePrecision.NS);

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writePoint(bucket, organization, point);
    }

    /**
     * Clears all data from the test bucket (use with caution).
     */
    public void clearBucket() {
        try {
            OffsetDateTime start = OffsetDateTime.ofInstant(Instant.parse("1970-01-01T00:00:00Z"), ZoneOffset.UTC);
            OffsetDateTime stop = OffsetDateTime.now().plusHours(1);

            influxDBClient.getDeleteApi().delete(start, stop, "", bucket, organization);
            System.out.println("Cleared test bucket: " + bucket);
        } catch (Exception e) {
            System.err.println("Error clearing bucket: " + e.getMessage());
        }
    }

    /**
     * Queries the last measurement from InfluxDB.
     */
    public Double queryLastMeasurement() {
        String flux = String.format("""
                from(bucket: "%s")
                  |> range(start: -1y)
                  |> filter(fn: (r) => r["_field"] == "total_act" and r["device"] == "total")
                  |> last()
                """, bucket);

        return influxDBClient.getQueryApi()
                .query(flux, organization)
                .stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> (Double) record.getValue())
                .findFirst()
                .orElse(null);
    }
}
