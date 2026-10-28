package com.cogniforge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  }
)
@AutoConfigureMockMvc
class SessionIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  String token() throws Exception {
    return (
      "Bearer " +
      json
        .readTree(
          mvc
            .perform(post("/api/v1/identity"))
            .andReturn()
            .getResponse()
            .getContentAsString()
        )
        .get("token")
        .asText()
    );
  }

  @Test
  void sixDigitSpanIsAcceptedAndStoredOnlyForDigitTasks() throws Exception {
    String auth = token();
    for (String task : List.of("digits", "reverse", "nback", "gonogo", "corsi", "schulte", "stroop", "switch", "cpt", "chunk", "loci")) {
      var request = post("/api/v1/sessions").header("Authorization", auth)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("task", task, "level", 4, "mode", "TRAINING", "device", "test")));
      if (Set.of("digits", "reverse").contains(task)) {
        var result = mvc.perform(request).andExpect(status().isOk())
          .andExpect(jsonPath("$.plan.level").value(4)).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(6, data.get("plan").get("stimuli").get(0).asText().split(",").length);
        mvc.perform(get("/api/v1/sessions/" + data.get("id").asText()).header("Authorization", auth))
          .andExpect(jsonPath("$.plan.level").value(4));
      } else mvc.perform(request).andExpect(status().isBadRequest());
    }
  }

  @Test
  void cptFreezesConfigAndPracticeDuration() throws Exception {
    String auth=token();
    for(String mode:List.of("PRACTICE","TRAINING","RETEST")) {
      var result=mvc.perform(post("/api/v1/sessions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("task","cpt","level",2,"mode",mode,"device","test","cptConfig",Map.of("variant","CUED","durationSeconds",90)))))
       .andExpect(status().isOk()).andExpect(jsonPath("$.plan.version").value("2.0")).andExpect(jsonPath("$.plan.cptConfig.durationSeconds").value(mode.equals("PRACTICE")?30:90)).andReturn();
      String id=json.readTree(result.getResponse().getContentAsString()).get("id").asText();
      mvc.perform(get("/api/v1/sessions/"+id).header("Authorization",auth)).andExpect(jsonPath("$.plan.gapMs").value(400));
    }
    mvc.perform(get("/api/v1/sessions").header("Authorization",auth)).andExpect(jsonPath("$[0].cptConfig.variant").value("CUED"));
  }

  @Test
  void reportsCurrentTaskSupport() throws Exception {
    mvc.perform(get("/api/v1/capabilities")).andExpect(status().isOk())
      .andExpect(jsonPath("$.protocol").value("2026-10-expansion-1"))
      .andExpect(jsonPath("$.tasks.length()").value(11))
      .andExpect(jsonPath("$.tasks", org.hamcrest.Matchers.hasItem("stroop")));
  }

  @Test
  void everyExpandedTaskPersistsPlanAndBreakdown() throws Exception {
    String auth = token();
    for(String task : Expansion.IDS) {
      var start = mvc.perform(post("/api/v1/sessions").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("task",task,"level",1,"mode","TRAINING","device","test"))))
        .andExpect(status().isOk()).andReturn();
      var data=json.readTree(start.getResponse().getContentAsString());
      String id=data.get("id").asText();
      var plan=json.treeToValue(data.get("plan"),Tasks.Plan.class);
      List<Tasks.Event> events=new ArrayList<>();
      for(int i=0;i<plan.expected().size();i++) {
        String response=plan.expected().get(i); if(response.equals("WARMUP")) response="LEFT";
        events.add(new Tasks.Event(i,response,response.isEmpty()?null:200.0,i*2000));
      }
      mvc.perform(put("/api/v1/sessions/"+id+"/result").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Tasks.Submission("COMPLETED",events,"","场景复盘"))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.accuracy").value(1)).andExpect(jsonPath("$.breakdown").isMap());
      mvc.perform(get("/api/v1/sessions/"+id).header("Authorization",auth)).andExpect(jsonPath("$.plan.task").value(task)).andExpect(jsonPath("$.submission.reflection").value("场景复盘"));
    }
    mvc.perform(get("/api/v1/sessions").header("Authorization",auth)).andExpect(jsonPath("$.length()").value(7)).andExpect(jsonPath("$[0].result.breakdown").isMap());
  }

  @Test
  void searchConfigurationAndScoresRoundTrip() throws Exception {
    String auth = token();
    var request = Map.of("task", "schulte", "level", 1, "mode", "TRAINING", "device", "test", "searchConfig", new Tasks.SearchConfig(5, "LETTER", 2, "HIDDEN"));
    mvc.perform(post("/api/v1/sessions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request).replace("\"count\":2", "\"count\":2.5"))).andExpect(status().isBadRequest());
    var started = mvc.perform(post("/api/v1/sessions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.plan.searchConfig.gridSize").value(5)).andReturn();
    var id = json.readTree(started.getResponse().getContentAsString()).get("id").asText();
    var body = new Tasks.Submission("COMPLETED", List.of(new Tasks.Event(0, "A", 400.0, 1000), new Tasks.Event(1, "B", 300.0, 1400)), "", "", 1000.0);
    mvc.perform(put("/api/v1/sessions/" + id + "/result").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.totalResponseMs").value(700));
    mvc.perform(get("/api/v1/sessions").header("Authorization", auth)).andExpect(jsonPath("$[0].searchConfig.visibility").value("HIDDEN")).andExpect(jsonPath("$[0].version").value("1.1"));
    mvc.perform(post("/api/v1/sessions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request).replace("\"count\":2", "\"count\":26"))).andExpect(status().isBadRequest());
  }

  @Test
  void isolationIdempotencyValidationAndDeletion() throws Exception {
    String a = token(),
      b = token();
    mvc.perform(get("/api/v1/sessions")).andExpect(status().isUnauthorized());
    var start = mvc
      .perform(
        post("/api/v1/sessions")
          .header("Authorization", a)
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"task\":\"gonogo\",\"level\":1,\"mode\":\"TRAINING\",\"device\":\"test\"}"
          )
      )
      .andExpect(status().isOk())
      .andReturn();
    var data = json.readTree(start.getResponse().getContentAsString());
    String id = data.get("id").asText();
    mvc
      .perform(get("/api/v1/sessions/" + id).header("Authorization", b))
      .andExpect(status().isNotFound());
    List<Tasks.Event> events = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      String response = data.get("plan").get("expected").get(i).asText();
      events.add(
        new Tasks.Event(
          i,
          response,
          response.isEmpty() ? null : 350.0,
          i * 1500
        )
      );
    }
    String body = json.writeValueAsString(
      new Tasks.Submission("COMPLETED", events, "", "一次复盘")
    );
    mvc
      .perform(
        put("/api/v1/sessions/" + id + "/result")
          .header("Authorization", a)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accuracy").value(1));
    mvc
      .perform(
        put("/api/v1/sessions/" + id + "/result")
          .header("Authorization", a)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        put("/api/v1/sessions/" + id + "/result")
          .header("Authorization", a)
          .contentType(MediaType.APPLICATION_JSON)
          .content(body.replace("一次复盘", "修改"))
      )
      .andExpect(status().isConflict());
    mvc
      .perform(get("/api/v1/sessions").header("Authorization", b))
      .andExpect(jsonPath("$.length()").value(0));
    mvc
      .perform(delete("/api/v1/sessions").header("Authorization", b))
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/v1/sessions/" + id).header("Authorization", a))
      .andExpect(status().isOk());
    mvc
      .perform(delete("/api/v1/sessions").header("Authorization", a))
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/v1/sessions/" + id).header("Authorization", a))
      .andExpect(status().isNotFound());
  }
}
