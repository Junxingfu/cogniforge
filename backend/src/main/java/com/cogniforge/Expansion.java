package com.cogniforge;

import java.security.SecureRandom;
import java.util.*;

public class Expansion {

  static final Random R = new SecureRandom();
  public static final Set<String> IDS = Set.of(
    "stroop",
    "switch",
    "cpt",
    "digits",
    "reverse",
    "chunk",
    "loci"
  );
  static final List<String> COLORS = List.of("红", "蓝", "绿", "黄");

  public static Tasks.Plan create(String task, int level) {
    List<String> stimuli = new ArrayList<>(),
      expected = new ArrayList<>();
    int window = 3000 - level * 500;
    if (task.equals("stroop")) {
      for (int c = 0; c < 4; c++) for (int j = 0; j < 10; j++) stimuli.add(
        COLORS.get(j < 5 ? c : (c + 1 + R.nextInt(3)) % 4) + "|" + COLORS.get(c)
      );
      Collections.shuffle(stimuli, R);
      for (String s : stimuli) expected.add(s.split("\\|")[1]);
    } else if (task.equals("switch")) {
      List<Boolean> transitions = new ArrayList<>();
      for (int i = 0; i < 40; i++) transitions.add(i < 20);
      Collections.shuffle(transitions, R);
      boolean parity = R.nextBoolean();
      for (int i = 0; i < 41; i++) {
        if (i > 0 && transitions.get(i - 1)) parity = !parity;
        int n;
        do {
          n = 1 + R.nextInt(9);
        } while (n == 5);
        stimuli.add((parity ? "奇偶" : "大小") + "|" + n);
        expected.add(
          i == 0 ? "WARMUP" : (parity ? n % 2 == 1 : n < 5) ? "LEFT" : "RIGHT"
        );
      }
    } else if (task.equals("cpt")) {
      window = 1000;
      for (int block = 0; block < level + 1; block++) {
        List<String> part = new ArrayList<>();
        for (int i = 0; i < 60; i++) part.add(
          i < 6 ? "X" : "ABCDEFGH".substring(i % 8, (i % 8) + 1)
        );
        Collections.shuffle(part, R);
        stimuli.addAll(part);
      }
      for (String s : stimuli) expected.add(s.equals("X") ? "PRESS" : "");
    } else {
      window = task.equals("digits") || task.equals("reverse") ? 15000 : 60000;
      for (int round = 0; round < (window == 15000 ? 5 : 3); round++) {
        List<String> items = new ArrayList<>();
        if (task.equals("loci")) {
          items.addAll(
            List.of(
              "苹果",
              "雨伞",
              "钥匙",
              "面包",
              "书本",
              "茶杯",
              "帽子",
              "鲜花",
              "毛巾",
              "铅笔",
              "香蕉",
              "手套",
              "牛奶",
              "镜子",
              "背包",
              "闹钟"
            )
          );
          Collections.shuffle(items, R);
          items = new ArrayList<>(items.subList(0, 2 + level * 2));
        } else if (task.equals("chunk")) {
          for (int i = 0; i < (level + 1) * 3; i++) items.add(
            "" + R.nextInt(10)
          );
        } else {
          for (int i = 0; i < 10; i++) items.add("" + i);
          Collections.shuffle(items, R);
          items = new ArrayList<>(items.subList(0, level + 2));
        }
        stimuli.add(String.join(",", items));
        if (task.equals("reverse")) Collections.reverse(items);
        expected.add(String.join(",", items));
      }
    }
    return new Tasks.Plan(task, level, task.equals("stroop") ? "1.2" : "1.0", window, stimuli, expected, null);
  }

  public static String condition(Tasks.Plan p, int i) {
    String[] s = p.stimuli().get(i).split("\\|");
    return switch (p.task()) {
      case "stroop" -> s[0].equals(s[1]) ? "匹配" : "冲突";
      case "switch" -> i == 0
        ? "熟悉"
        : s[0].equals(
              p
                .stimuli()
                .get(i - 1)
                .split("\\|")[0]
            )
          ? "重复"
          : "切换";
      case "cpt" -> "第" + (i / 60 + 1) + "段";
      default -> "第" + (i + 1) + "轮";
    };
  }

  public static void validate(Tasks.Plan p, Tasks.Event e) {
    if (e.response().isEmpty()) return;
    boolean ok = switch (p.task()) {
      case "stroop" -> COLORS.contains(e.response());
      case "switch" -> Set.of("LEFT", "RIGHT").contains(e.response());
      case "digits", "reverse", "chunk" -> e.response().matches(
        "[0-9](,[0-9])*"
      );
      default -> true;
    };
    if (!ok) throw new IllegalArgumentException("任务响应格式无效");
  }

  public static Map<String, Map<String, Double>> breakdown(
    Tasks.Plan p,
    Tasks.Submission submission
  ) {
    if (!IDS.contains(p.task())) return null;
    if(p.cptConfig()!=null)return Cpt.breakdown(p,submission);
    Map<String, List<Tasks.Event>> groups = new LinkedHashMap<>();
    for (var e : submission.events())
      if (!p.expected().get(e.index()).equals("WARMUP")) groups
        .computeIfAbsent(condition(p, e.index()), k -> new ArrayList<>())
        .add(e);
    Map<String, Map<String, Double>> out = new LinkedHashMap<>();
    for (var entry : groups.entrySet()) {
      double correct = 0,
        targets = 0,
        misses = 0,
        fa = 0,
        positions = 0,
        matched = 0;
      List<Double> times = new ArrayList<>();
      for (var e : entry.getValue()) {
        String expected = p.expected().get(e.index());
        if (expected.equals(e.response())) {
          correct++;
          if (e.rtMs() != null) times.add(e.rtMs());
        }
        if (expected.equals("PRESS")) {
          targets++;
          if (e.response().isEmpty()) misses++;
        } else if (expected.isEmpty() && !e.response().isEmpty()) fa++;
        if (Set.of("digits", "reverse", "chunk", "loci").contains(p.task())) {
          String[] wanted = expected.split(","),
            actual = e.response().split(",");
          positions += wanted.length;
          for (int j = 0; j < wanted.length; j++) if (
            j < actual.length && wanted[j].equals(actual[j])
          ) matched++;
        }
      }
      Collections.sort(times);
      int n = times.size();
      double mean = times
        .stream()
        .mapToDouble(x -> x)
        .average()
        .orElse(0);
      Map<String, Double> stats = new LinkedHashMap<>();
      stats.put("n", (double) entry.getValue().size());
      stats.put("correct", correct);
      stats.put("accuracy", correct / entry.getValue().size());
      stats.put("correctResponses", (double) n);
      stats.put(
        "medianRt",
        n == 0
          ? null
          : n % 2 == 1
            ? times.get(n / 2)
            : (times.get(n / 2 - 1) + times.get(n / 2)) / 2
      );
      stats.put(
        "rtSd",
        n < 2
          ? null
          : Math.sqrt(
              times
                .stream()
                .mapToDouble(x -> (x - mean) * (x - mean))
                .sum() /
                (n - 1)
            )
      );
      if (p.task().equals("cpt")) {
        stats.put("targets", targets);
        stats.put("misses", misses);
        stats.put("falseAlarms", fa);
      }
      if (positions > 0) stats.put("positionAccuracy", matched / positions);
      out.put(entry.getKey(), stats);
    }
    return out;
  }
}
