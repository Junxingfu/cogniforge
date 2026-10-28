package com.cogniforge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class ExpansionTest {

  @Test
  void balancedStroopAndSwitch() {
    for (int level = 1; level <= 3; level++) for (String task : List.of(
      "stroop",
      "switch"
    )) {
      var p = Tasks.create(task, level);
      Map<String, Integer> counts = new HashMap<>();
      for (int i = 0; i < p.stimuli().size(); i++) {
        String[] parts = p.stimuli().get(i).split("\\|");
        if (task.equals("stroop")) assertEquals(parts[1], p.expected().get(i));
        else if (i > 0) {
          int n = Integer.parseInt(parts[1]);
          assertNotEquals(5, n);
          assertEquals(
            (parts[0].equals("奇偶") ? n % 2 == 1 : n < 5) ? "LEFT" : "RIGHT",
            p.expected().get(i)
          );
        }
        counts.merge(Expansion.condition(p, i), 1, Integer::sum);
      }
      assertEquals(20, counts.get(task.equals("stroop") ? "匹配" : "重复"));
      assertEquals(20, counts.get(task.equals("stroop") ? "冲突" : "切换"));
    }
  }

  @Test
  void cptTargetsPerBlockAndAllSilent() {
    for (int l = 1; l <= 3; l++) {
      var p = Tasks.create("cpt", l);
      assertEquals((l + 1) * 60, p.expected().size());
      List<Tasks.Event> events = new ArrayList<>();
      for (int i = 0; i < p.expected().size(); i++) events.add(
        new Tasks.Event(i, "", null, i * 1500)
      );
      var r = Tasks.score(p, new Tasks.Submission("COMPLETED", events, "", ""));
      assertEquals(0, r.hitRate());
      assertEquals(.9, r.accuracy());
      for (var stats : r.breakdown().values()) {
        assertEquals(6, stats.get("targets"));
        assertEquals(6, stats.get("misses"));
        assertNull(stats.get("rtSd"));
      }
    }
  }

  @Test
  void memoryDirectionSpanAndPerfectScoring() {
    for (String task : Expansion.IDS)
      for (int l = 1; l <= (Set.of("digits", "reverse").contains(task) ? 4 : 3); l++) {
        var p = Tasks.create(task, l);
        List<Tasks.Event> events = new ArrayList<>();
        for (int i = 0; i < p.expected().size(); i++) {
          String target = p.expected().get(i);
          String response = target.equals("WARMUP") ? "LEFT" : target;
          events.add(
            new Tasks.Event(
              i,
              response,
              response.isEmpty() ? null : 300.0,
              i * 65000
            )
          );
          if (task.equals("reverse")) {
            var values = new ArrayList<>(
              Arrays.asList(p.stimuli().get(i).split(","))
            );
            Collections.reverse(values);
            assertEquals(String.join(",", values), target);
          }
          if (task.equals("digits") || task.equals("reverse")) assertEquals(
            l + 2,
            target.split(",").length
          );
          if (task.equals("loci")) assertEquals(
            2 + l * 2,
            target.split(",").length
          );
        }
        // Event timestamps remain within the accepted two-hour session limit.
        for (int i = 0; i < events.size(); i++) {
          var e = events.get(i);
          events.set(i, new Tasks.Event(i, e.response(), e.rtMs(), i * 2000));
        }
        var result = Tasks.score(
          p,
          new Tasks.Submission("COMPLETED", events, "", "")
        );
        assertTrue(result.valid());
        assertEquals(1, result.accuracy());
        assertNotNull(result.breakdown());
      }
  }

  @Test
  void invalidResponsesAndPartialRecall() {
    var p = Tasks.create("digits", 1);
    assertThrows(IllegalArgumentException.class, () ->
      Tasks.score(
        p,
        new Tasks.Submission(
          "ABORTED",
          List.of(new Tasks.Event(0, "bad", 100.0, 0)),
          "",
          ""
        )
      )
    );
    String first = p.expected().get(0).split(",")[0];
    var r = Tasks.score(
      p,
      new Tasks.Submission(
        "ABORTED",
        List.of(new Tasks.Event(0, first, 100.0, 0)),
        "",
        ""
      )
    );
    assertFalse(r.valid());
    assertEquals(1.0 / 3, r.breakdown().get("第1轮").get("positionAccuracy"));
  }
}
