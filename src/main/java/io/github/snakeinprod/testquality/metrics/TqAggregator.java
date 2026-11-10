package io.github.snakeinprod.testquality.metrics;

import org.sonar.api.ce.measure.Component;
import org.sonar.api.ce.measure.Measure;
import org.sonar.api.ce.measure.MeasureComputer;
import org.sonar.api.measures.Metric;

/**
 * Sums the integer tq_* measures from files to directories and the project, then derives assertions per test and the
 * mutation score where the parts exist. Report measures saved on the project (p95, slowest, flaky) pass through.
 */
public class TqAggregator implements MeasureComputer {

  @Override
  public MeasureComputerDefinition define(MeasureComputerDefinitionContext def) {
    String[] summed = TqMetrics.SUMMED.stream().map(Metric::getKey).toArray(String[]::new);
    String[] outputs = java.util.stream.Stream.concat(java.util.Arrays.stream(summed),
      java.util.stream.Stream.of(TqMetrics.ASSERTIONS_PER_TEST.getKey(), TqMetrics.MUTATION_SCORE.getKey())).toArray(String[]::new);
    return def.newDefinitionBuilder().setOutputMetrics(outputs).build();
  }

  @Override
  public void compute(MeasureComputerContext ctx) {
    if (ctx.getComponent().getType() != Component.Type.FILE) {
      for (Metric<Integer> m : TqMetrics.SUMMED) {
        int sum = 0;
        boolean any = false;
        for (Measure child : ctx.getChildrenMeasures(m.getKey())) {
          sum += child.getIntValue();
          any = true;
        }
        if (any && ctx.getMeasure(m.getKey()) == null) {
          ctx.addMeasure(m.getKey(), sum);
        }
      }
    }
    Integer tests = value(ctx, TqMetrics.TESTS.getKey());
    Integer assertions = value(ctx, TqMetrics.ASSERTIONS.getKey());
    if (tests != null && assertions != null && tests > 0 && ctx.getMeasure(TqMetrics.ASSERTIONS_PER_TEST.getKey()) == null) {
      ctx.addMeasure(TqMetrics.ASSERTIONS_PER_TEST.getKey(), Math.round(10.0 * assertions / tests) / 10.0);
    }
    Integer killed = value(ctx, TqMetrics.MUTANTS_KILLED.getKey());
    Integer survived = value(ctx, TqMetrics.MUTANTS_SURVIVED.getKey());
    Integer noCoverage = value(ctx, TqMetrics.MUTANTS_NO_COVERAGE.getKey());
    if (killed != null && ctx.getMeasure(TqMetrics.MUTATION_SCORE.getKey()) == null) {
      int run = killed + (survived == null ? 0 : survived) + (noCoverage == null ? 0 : noCoverage);
      if (run > 0) {
        ctx.addMeasure(TqMetrics.MUTATION_SCORE.getKey(), Math.round(1000.0 * killed / run) / 10.0);
      }
    }
  }

  private static Integer value(MeasureComputerContext ctx, String key) {
    Measure m = ctx.getMeasure(key);
    return m == null ? null : m.getIntValue();
  }
}
