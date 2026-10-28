package com.cogniforge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CptTest {

  @Test
  void sequencesMatchTargetsDurationAndBoundedGaps() {
    for (int repeat = 0; repeat < 30; repeat++) for (String variant : List.of(
      "BASIC",
      "CUED"
    ))
      for (int level = 1; level <= 3; level++) for (int duration : List.of(
        90,
        180
      ))
        for (boolean practice : List.of(true, false)) {
          var p = Cpt.create(
            level,
            new Cpt.Config(variant, duration),
            practice
          );
          assertEquals(
            (practice ? 30 : duration) * 1000,
            p.expected().size() * (p.windowMs() + p.gapMs())
          );
          assertEquals("2.0", p.version());
          int gap = 0,
            targets = 0;
          for (int i = 0; i < p.stimuli().size(); i++) {
            boolean target =
              p.stimuli().get(i).equals("X") &&
              (variant.equals("BASIC") ||
                (i > 0 &&
                  p
                    .stimuli()
                    .get(i - 1)
                    .equals("A")));
            assertEquals(target, p.expected().get(i).equals("PRESS"));
            if (target) targets++;
            gap = target ? 0 : gap + 1;
            assertTrue(gap <= 24);
          }
          assertEquals(Math.round(p.expected().size() * .15), targets);
        }
  }

  @Test
  void errorTypesAndHalfReportsAreExact() {
    var p = new Tasks.Plan(
      "cpt",
      1,
      "2.0",
      1000,
      List.of("A", "X", "B", "X", "A", "B"),
      List.of("", "PRESS", "", "", "", ""),
      null,
      new Cpt.Config("CUED", 90),
      500
    );
    List<Tasks.Event> events = new ArrayList<>();
    for (int i = 0; i < 6; i++) events.add(
      new Tasks.Event(i, i == 1 ? "" : "PRESS", i == 1 ? null : 200.0, i * 1500)
    );
    var r = Tasks.score(p, new Tasks.Submission("COMPLETED", events, "", ""));
    var s = r.breakdown().get("全程");
    assertEquals(1, s.get("misses"));
    assertEquals(2, s.get("premature"));
    assertEquals(1, s.get("lureFalseAlarms"));
    assertEquals(2, s.get("otherFalseAlarms"));
    assertEquals(5, s.get("falseAlarms"));
    assertEquals(3, r.breakdown().get("前半").get("n"));
    assertEquals(3, r.breakdown().get("后半").get("n"));
    assertNull(r.medianRt());
  }

  @Test
  void rejectsInvalidConfigAndPreservesLegacyJson() throws Exception {
    assertThrows(IllegalArgumentException.class, () ->
      new Cpt.Config("bad", 90)
    );
    assertThrows(IllegalArgumentException.class, () ->
      Cpt.create(1, new Cpt.Config("CUED", 30), false)
    );
    var p = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
      "{\"task\":\"cpt\",\"level\":1,\"version\":\"1.0\",\"windowMs\":1000,\"stimuli\":[\"X\"],\"expected\":[\"PRESS\"]}",
      Tasks.Plan.class
    );
    assertNull(p.cptConfig());
    assertEquals(
      1,
      Tasks.score(
        p,
        new Tasks.Submission(
          "COMPLETED",
          List.of(new Tasks.Event(0, "PRESS", 200.0, 0)),
          "",
          ""
        )
      ).accuracy()
    );
  }
}
