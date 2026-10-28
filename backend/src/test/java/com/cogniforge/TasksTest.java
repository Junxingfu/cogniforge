package com.cogniforge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class TasksTest {
  @Test
  void configurableSearchHasUniqueSymbolsAndEmptyCells() {
    for (int size : List.of(4, 5)) for (String type : List.of("NUMBER", "LETTER")) {
      var p = Tasks.create("schulte", 1, new Tasks.SearchConfig(size, type, 6, "HIDDEN"));
      var cells = Arrays.asList(p.stimuli().get(0).split(",", -1));
      assertEquals(size * size, cells.size());
      assertEquals(6, cells.stream().filter(x -> !x.isEmpty()).distinct().count());
      assertTrue(cells.containsAll(p.expected()));
      assertEquals("1.1", p.version());
    }
    assertThrows(IllegalArgumentException.class, () -> new Tasks.SearchConfig(4, "NUMBER", 17, "VISIBLE"));
    assertThrows(IllegalArgumentException.class, () -> new Tasks.SearchConfig(5, "LETTER", 1, "VISIBLE"));
    assertThrows(IllegalArgumentException.class, () -> new Tasks.SearchConfig(3, "LETTER", 4, "VISIBLE"));
    assertThrows(IllegalArgumentException.class, () -> new Tasks.SearchConfig(5, "OTHER", 4, "VISIBLE"));
  }

  @Test
  void searchScoresMistakesAndSeparatesObservation() {
    var p = Tasks.create("schulte", 1, new Tasks.SearchConfig(4, "LETTER", 2, "HIDDEN"));
    var events = List.of(new Tasks.Event(0, "B,A", 800.0, 2000), new Tasks.Event(1, "B", 500.0, 2800));
    var r = Tasks.score(p, new Tasks.Submission("COMPLETED", events, "", "", 2000.0));
    assertEquals(.5, r.accuracy());
    assertEquals(1, r.errorClicks());
    assertEquals(1300, r.totalResponseMs());
    assertEquals(2000, r.observationMs());
    assertEquals(500, r.medianRt());
    assertThrows(IllegalArgumentException.class, () -> Tasks.score(p, new Tasks.Submission("COMPLETED", events, "", "", 3000.0)));
    assertThrows(IllegalArgumentException.class, () -> Tasks.score(p, new Tasks.Submission("COMPLETED", List.of(new Tasks.Event(0, "B", 800.0, 2000), events.get(1)), "", "", 2000.0)));
  }

  @Test
  void legacyPlanStillDeserializes() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var p = mapper.readValue("{\"task\":\"schulte\",\"level\":1,\"version\":\"1.0\",\"windowMs\":60000,\"stimuli\":[\"1\"],\"expected\":[\"1\"]}", Tasks.Plan.class);
    assertNull(p.searchConfig());
    assertTrue(Tasks.score(p, new Tasks.Submission("COMPLETED", List.of(new Tasks.Event(0, "1", 100.0, 0)), "", "")).valid());
  }


  @Test
  void nbackTargetsAreRealAndWarmupExcluded() {
    for (int level = 1; level <= 3; level++) {
      var p = Tasks.create("nback", level);
      assertEquals(30 + level, p.expected().size());
      int targets = 0;
      for (int i = level; i < p.expected().size(); i++) {
        boolean target = p
          .stimuli()
          .get(i)
          .equals(p.stimuli().get(i - level));
        assertEquals(target, p.expected().get(i).equals("PRESS"));
        if (target) targets++;
      }
      assertEquals(9, targets);
    }
  }

  @Test
  void allSilentIsNotPerfect() {
    var p = Tasks.create("nback", 2);
    var events = new ArrayList<Tasks.Event>();
    for (int i = 0; i < p.expected().size(); i++) events.add(
      new Tasks.Event(i, "", null, i * 3000)
    );
    var r = Tasks.score(p, new Tasks.Submission("COMPLETED", events, "", ""));
    assertEquals(30, r.trials());
    assertEquals(0, r.hitRate());
    assertEquals(.7, r.accuracy());
    assertNull(r.medianRt());
  }

  @Test
  void noGoFalseAlarmsAreSeparate() {
    var p = Tasks.create("gonogo", 1);
    var events = new ArrayList<Tasks.Event>();
    for (int i = 0; i < 40; i++) events.add(
      new Tasks.Event(i, "PRESS", 400.0, i * 1500)
    );
    var r = Tasks.score(p, new Tasks.Submission("COMPLETED", events, "", ""));
    assertEquals(1, r.hitRate());
    assertEquals(1, r.falseAlarmRate());
    assertEquals(.75, r.accuracy());
    assertEquals(400, r.medianRt());
  }

  @Test
  void invalidTimingAndIncompleteDataRejected() {
    var p = Tasks.create("gonogo", 3);
    assertThrows(IllegalArgumentException.class, () ->
      Tasks.score(p, new Tasks.Submission("COMPLETED", List.of(), "", ""))
    );
    assertThrows(IllegalArgumentException.class, () ->
      Tasks.score(
        p,
        new Tasks.Submission(
          "ABORTED",
          List.of(new Tasks.Event(0, "PRESS", 1000.0, 0)),
          "",
          ""
        )
      )
    );
    assertThrows(IllegalArgumentException.class, () ->
      Tasks.score(
        p,
        new Tasks.Submission(
          "ABORTED",
          List.of(new Tasks.Event(1, "", null, 0)),
          "",
          ""
        )
      )
    );
    assertFalse(
      Tasks.score(
        p,
        new Tasks.Submission("ABORTED", List.of(), "BLUR", "")
      ).valid()
    );
  }

  @Test
  void corsiHasDistinctLocationsAndCorrectSpan() {
    for (int n = 1; n <= 3; n++) {
      var p = Tasks.create("corsi", n);
      for (String s : p.stimuli()) {
        var values = s.split(",");
        assertEquals(n + 2, values.length);
        assertEquals(
          values.length,
          new HashSet<>(Arrays.asList(values)).size()
        );
      }
    }
  }
}
