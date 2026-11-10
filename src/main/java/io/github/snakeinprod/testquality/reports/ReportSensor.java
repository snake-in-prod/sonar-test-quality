package io.github.snakeinprod.testquality.reports;

import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

/**
 * Imports mutation (mutation-testing-elements JSON), JUnit XML and Dart test JSON reports into the tq_* report
 * measures. Not implemented yet.
 */
public class ReportSensor implements Sensor {

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("Test Quality reports");
  }

  @Override
  public void execute(SensorContext context) {
    // not implemented yet
  }
}
