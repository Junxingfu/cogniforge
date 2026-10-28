package com.cogniforge;

import java.util.*;

public class Cpt {

  public record Config(String variant, int durationSeconds) {
    public Config {
      if (
        !Set.of("BASIC", "CUED").contains(variant == null ? "" : variant) ||
        !Set.of(30, 90, 180).contains(durationSeconds)
      ) throw new IllegalArgumentException("守望玩法或时长无效");
    }
  }

  public static Tasks.Plan create(
    int level,
    Config requested,
    boolean practice
  ) {
    if (level < 1 || level > 3) throw new IllegalArgumentException("档位无效");
    if (requested == null) requested = new Config("CUED", 90);
    if (requested.durationSeconds() == 30) throw new IllegalArgumentException(
      "正式时长仅支持90或180秒；练习时长自动设置"
    );
    Config config = new Config(
      requested.variant(),
      practice ? 30 : requested.durationSeconds()
    );
    int window = new int[] { 1000, 800, 700 }[level - 1],
      gap = new int[] { 500, 400, 300 }[level - 1];
    int n = (config.durationSeconds() * 1000) / (window + gap),
      targets = (int) Math.round(n * .15),
      lures = (int) Math.round(n * new double[] { .07, .10, .13 }[level - 1]);
    List<List<String>> tiles = new ArrayList<>();
    for (int i = 0; i < targets; i++) tiles.add(
      config.variant().equals("CUED") ? List.of("A", "X") : List.of("X")
    );
    for (int i = 0; i < lures; i++) {
      tiles.add(
        requested.variant().equals("BASIC")
          ? List.of("B", "C")
          : List.of("B", "X")
      );
      tiles.add(List.of("A", "B"));
    }
    // BASIC treats every X, including BX, as a target.
    int used = tiles.stream().mapToInt(List::size).sum();
    for (int i = used; i < n; i++) tiles.add(
      List.of("BCDEFGH".substring(i % 7, (i % 7) + 1))
    );
    for (int attempt = 0; attempt < 10000; attempt++) {
      Collections.shuffle(tiles, Expansion.R);
      List<String> stimuli = new ArrayList<>(),
        expected = new ArrayList<>();
      for (var tile : tiles) stimuli.addAll(tile);
      int gapCount = 0,
        maxGap = 0;
      for (int i = 0; i < n; i++) {
        boolean target =
          stimuli.get(i).equals("X") &&
          (config.variant().equals("BASIC") ||
            (i > 0 && stimuli.get(i - 1).equals("A")));
        expected.add(target ? "PRESS" : "");
        gapCount = target ? 0 : gapCount + 1;
        maxGap = Math.max(maxGap, gapCount);
      }
      if (maxGap <= 24) return new Tasks.Plan(
        "cpt",
        level,
        "2.0",
        window,
        stimuli,
        expected,
        null,
        config,
        gap
      );
    }
    throw new IllegalArgumentException("刺激生成失败，请重试");
  }

  public static Map<String, Map<String, Double>> breakdown(
    Tasks.Plan p,
    Tasks.Submission submission
  ) {
    Map<String, Map<String, Double>> result = new LinkedHashMap<>();
    result.put("全程", stats(p, submission.events()));
    int half = p.expected().size() / 2;
    result.put(
      "前半",
      stats(
        p,
        submission
          .events()
          .stream()
          .filter(e -> e.index() < half)
          .toList()
      )
    );
    result.put(
      "后半",
      stats(
        p,
        submission
          .events()
          .stream()
          .filter(e -> e.index() >= half)
          .toList()
      )
    );
    return result;
  }

  static Map<String, Double> stats(Tasks.Plan p, List<Tasks.Event> events) {
    double targets = 0,
      misses = 0,
      fa = 0,
      correct = 0,
      cues = 0,
      early = 0,
      lures = 0,
      lureErrors = 0,
      other = 0;
    List<Double> rts = new ArrayList<>();
    for (var e : events) {
      boolean target = p.expected().get(e.index()).equals("PRESS"),
        pressed = e.response().equals("PRESS");
      if (target) {
        targets++;
        if (!pressed) misses++;
        else if (e.rtMs() != null) rts.add(e.rtMs());
      } else if (pressed) fa++;
      if (target == pressed) correct++;
      String stimulus = p.stimuli().get(e.index());
      if (stimulus.equals("A")) {
        cues++;
        if (pressed) early++;
      } else if (stimulus.equals("X") && !target) {
        lures++;
        if (pressed) lureErrors++;
      } else if (!target && pressed) other++;
    }
    Collections.sort(rts);
    int count = rts.size();
    double mean = rts
      .stream()
      .mapToDouble(x -> x)
      .average()
      .orElse(0);
    Map<String, Double> s = new LinkedHashMap<>();
    s.put("n", (double) events.size());
    s.put("correct", correct);
    s.put("targets", targets);
    s.put("hits", targets - misses);
    s.put("misses", misses);
    s.put("nonTargets", events.size() - targets);
    s.put("falseAlarms", fa);
    s.put("cueOpportunities", cues);
    s.put("premature", early);
    s.put("lureOpportunities", lures);
    s.put("lureFalseAlarms", lureErrors);
    s.put("otherFalseAlarms", other);
    s.put("correctResponses", (double) count);
    s.put(
      "medianRt",
      count < 5
        ? null
        : count % 2 == 1
          ? rts.get(count / 2)
          : (rts.get(count / 2 - 1) + rts.get(count / 2)) / 2
    );
    s.put(
      "rtSd",
      count < 5
        ? null
        : Math.sqrt(
            rts
              .stream()
              .mapToDouble(x -> (x - mean) * (x - mean))
              .sum() /
              (count - 1)
          )
    );
    return s;
  }
}
