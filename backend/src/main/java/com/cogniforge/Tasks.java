package com.cogniforge;

import java.security.SecureRandom;
import java.util.*;

public class Tasks {

  public record SearchConfig(
    int gridSize,
    String symbols,
    int count,
    String visibility
  ) {
    public SearchConfig {
      if (
        (gridSize != 4 && gridSize != 5) ||
        count < 2 ||
        count > gridSize * gridSize ||
        !Set.of("NUMBER", "LETTER").contains(symbols == null ? "" : symbols) ||
        !Set.of("VISIBLE", "HIDDEN").contains(
          visibility == null ? "" : visibility
        )
      ) throw new IllegalArgumentException(
        "请选择 4×4 或 5×5、2 至格子容量个字符及有效模式"
      );
    }
  }

  public record Plan(
    String task,
    int level,
    String version,
    int windowMs,
    List<String> stimuli,
    List<String> expected,
    SearchConfig searchConfig,
    Cpt.Config cptConfig,
    Integer gapMs
  ) {
    public Plan(
      String task,
      int level,
      String version,
      int windowMs,
      List<String> stimuli,
      List<String> expected,
      SearchConfig searchConfig
    ) {
      this(
        task,
        level,
        version,
        windowMs,
        stimuli,
        expected,
        searchConfig,
        null,
        null
      );
    }
  }

  public record Event(
    int index,
    String response,
    Double rtMs,
    double onsetMs
  ) {}

  public record Submission(
    String status,
    List<Event> events,
    String quality,
    String reflection,
    Double observationMs
  ) {
    public Submission(
      String status,
      List<Event> events,
      String quality,
      String reflection
    ) {
      this(status, events, quality, reflection, null);
    }
  }

  public record Result(
    int trials,
    int correct,
    int targets,
    int hits,
    int falseAlarms,
    int nonTargets,
    Double accuracy,
    Double hitRate,
    Double falseAlarmRate,
    Double medianRt,
    String advice,
    boolean valid,
    Double observationMs,
    Double totalResponseMs,
    Integer errorClicks,
    Map<String, Map<String, Double>> breakdown
  ) {}

  private static final Random random = new SecureRandom();

  public static Plan create(String task, int level) {
    return create(task, level, null);
  }

  public static Plan create(String task, int level, SearchConfig config) {
    if (level < 1 || level > (Set.of("digits", "reverse").contains(task) ? 4 : 3)) throw new IllegalArgumentException("档位无效");
    if (
      !task.equals("schulte") && config != null
    ) throw new IllegalArgumentException("此任务不支持寻踪配置");
    if (Expansion.IDS.contains(task)) return Expansion.create(task, level);
    List<String> stimuli = new ArrayList<>(),
      expected = new ArrayList<>();
    int window = 2500;
    if (task.equals("nback")) {
      Set<Integer> targets = new HashSet<>();
      while (targets.size() < 9) targets.add(level + random.nextInt(30));
      for (int i = 0; i < 30 + level; i++) {
        String previous = i >= level ? stimuli.get(i - level) : "";
        String x;
        if (targets.contains(i)) x = previous;
        else {
          do {
            x = "" + (char) ('A' + random.nextInt(8));
          } while (x.equals(previous));
        }
        stimuli.add(x);
        expected.add(i < level ? "WARMUP" : x.equals(previous) ? "PRESS" : "");
      }
    } else if (task.equals("gonogo")) {
      window = new int[] { 0, 1000, 850, 700 }[level];
      List<String> items = new ArrayList<>();
      for (int i = 0; i < 40; i++) items.add(i < 10 ? "STOP" : "GO");
      Collections.shuffle(items, random);
      stimuli.addAll(items);
      for (String x : items) expected.add(x.equals("GO") ? "PRESS" : "");
    } else if (task.equals("corsi")) {
      window = 15000;
      for (int i = 0; i < 5; i++) {
        List<String> cells = new ArrayList<>();
        for (int j = 0; j < 9; j++) cells.add("" + j);
        Collections.shuffle(cells, random);
        String seq = String.join(",", cells.subList(0, level + 2));
        stimuli.add(seq);
        expected.add(seq);
      }
    } else if (task.equals("schulte")) {
      window = 60000;
      List<String> cells = new ArrayList<>();
      if (config == null) config = new SearchConfig(4, "NUMBER", 16, "VISIBLE");
      for (int i = 1; i <= config.count(); i++) expected.add(
        config.symbols().equals("NUMBER") ? "" + i : "" + (char) ('A' + i - 1)
      );
      cells.addAll(expected);
      while (cells.size() < config.gridSize() * config.gridSize())
        cells.add("");
      Collections.shuffle(cells, random);
      for (int i = 0; i < config.count(); i++) stimuli.add(
        String.join(",", cells)
      );
    } else throw new IllegalArgumentException("未知训练任务");
    return new Plan(
      task,
      level,
      config == null ? "1.0" : "1.1",
      window,
      stimuli,
      expected,
      config
    );
  }

  public static Result score(Plan p, Submission s) {
    if (
      s == null ||
      s.status() == null ||
      !Set.of("COMPLETED", "ABORTED").contains(s.status()) ||
      s.events() == null ||
      s.events().size() > p.expected().size()
    ) throw new IllegalArgumentException("训练状态或事件数量无效");
    if (
      s.status().equals("COMPLETED") && s.events().size() != p.expected().size()
    ) throw new IllegalArgumentException("试次不完整");
    boolean search = p.searchConfig() != null;
    if (
      search &&
      (s.observationMs() == null ||
        !Double.isFinite(s.observationMs()) ||
        s.observationMs() < 0 ||
        s.observationMs() > 7200000)
    ) throw new IllegalArgumentException("观察时间无效");
    double totalResponse = 0;
    int errors = 0;
    int correct = 0,
      count = 0,
      targets = 0,
      hits = 0,
      fa = 0,
      non = 0;
    double last = -1;
    List<Double> rts = new ArrayList<>();
    for (int i = 0; i < s.events().size(); i++) {
      Event e = s.events().get(i);
      if (
        e == null ||
        e.index() != i ||
        e.response() == null ||
        e.response().length() > 100 ||
        !Double.isFinite(e.onsetMs()) ||
        e.onsetMs() < 0 ||
        e.onsetMs() < last ||
        e.onsetMs() > 7200000
      ) throw new IllegalArgumentException("试次顺序或时间无效");
      last = e.onsetMs();
      if (
        e.rtMs() != null &&
        (!Double.isFinite(e.rtMs()) || e.rtMs() < 0 || e.rtMs() > p.windowMs())
      ) throw new IllegalArgumentException("响应时间超出窗口");
      if (
        e.response().isEmpty() != (e.rtMs() == null)
      ) throw new IllegalArgumentException("响应与时间不一致");
      Expansion.validate(p, e);
      String expected = p.expected().get(i);
      if (expected.equals("WARMUP")) continue;
      boolean binary =
        p.task().equals("nback") ||
        p.task().equals("gonogo") ||
        p.task().equals("cpt");
      if (
        binary && !Set.of("", "PRESS").contains(e.response())
      ) throw new IllegalArgumentException("响应值无效");
      if (search) {
        if (e.onsetMs() < s.observationMs()) throw new IllegalArgumentException(
          "作答早于观察结束"
        );
        String[] clicks = e.response().isEmpty()
          ? new String[0]
          : e.response().split(",", -1);
        for (int c = 0; c < clicks.length; c++) {
          if (
            !p.expected().contains(clicks[c]) ||
            (c < clicks.length - 1 && clicks[c].equals(expected))
          ) throw new IllegalArgumentException("点击序列无效");
          if (!clicks[c].equals(expected)) errors++;
        }
        if (
          s.status().equals("COMPLETED") &&
          (clicks.length == 0 || !clicks[clicks.length - 1].equals(expected))
        ) throw new IllegalArgumentException("尚未找到全部目标");
        totalResponse += e.rtMs() == null ? p.windowMs() : e.rtMs();
      }
      count++;
      boolean ok = expected.equals(e.response());
      if (ok) {
        correct++;
        if (e.rtMs() != null) rts.add(e.rtMs());
      }
      if (binary) {
        if (expected.equals("PRESS")) {
          targets++;
          if (ok) hits++;
        } else {
          non++;
          if (!ok) fa++;
        }
      }
    }
    Collections.sort(rts);
    int n = rts.size();
    Double median =
      n == 0
        ? null
        : n % 2 == 1
          ? rts.get(n / 2)
          : (rts.get(n / 2 - 1) + rts.get(n / 2)) / 2;
    boolean valid =
      s.status().equals("COMPLETED") &&
      (s.quality() == null || s.quality().isBlank());
    return new Result(
      count,
      correct,
      targets,
      hits,
      fa,
      non,
      count == 0 ? null : (correct * 1.0) / count,
      targets == 0 ? null : (hits * 1.0) / targets,
      non == 0 ? null : (fa * 1.0) / non,
      p.cptConfig() != null && rts.size() < 5 ? null : median,
      valid
        ? "先保持当前难度，积累同条件记录后再调整；本次仅反映该任务表现。"
        : "本次已中断或存在质量标记，不纳入表现趋势。",
      valid,
      search ? s.observationMs() : null,
      search ? totalResponse : null,
      search ? errors : null,
      Expansion.breakdown(p, s)
    );
  }
}
