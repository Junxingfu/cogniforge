package com.cogniforge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

  private final SessionMapper mapper;
  private final ObjectMapper json;

  public SessionController(SessionMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public record Start(
    @NotBlank String task,
    @Min(1) @Max(4) int level,
    @Pattern(regexp = "PRACTICE|TRAINING|RETEST") String mode,
    @NotBlank @Size(max = 500) String device,
    Tasks.SearchConfig searchConfig,
    Cpt.Config cptConfig
  ) {}

  public record Reflection(@NotNull @Size(max = 1000) String reflection) {}

  private String owner(String header) throws Exception {
    if (
      header == null || !header.matches("Bearer [A-Za-z0-9_-]{43}")
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "请创建本机个人档案"
    );
    return HexFormat.of().formatHex(
      MessageDigest.getInstance("SHA-256").digest(
        header.getBytes(StandardCharsets.UTF_8)
      )
    );
  }

  @GetMapping("/capabilities")
  public Map<String, Object> capabilities() {
    Set<String> tasks = new TreeSet<>(Expansion.IDS);
    tasks.addAll(Set.of("nback", "gonogo", "corsi", "schulte"));
    return Map.of("protocol", "2026-10-expansion-1", "tasks", tasks);
  }

  @PostMapping("/identity")
  public Map<String, String> identity() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Map.of(
      "token",
      Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    );
  }

  @PostMapping("/sessions")
  public Map<String, Object> start(
    @RequestHeader(value = "Authorization", required = false) String auth,
    @Valid @RequestBody Start body
  ) throws Exception {
    String user = owner(auth);
    if (body.mode() == null) throw new IllegalArgumentException("缺少训练模式");
    if (
      !body.task().equals("cpt") && body.cptConfig() != null
    ) throw new IllegalArgumentException("此任务不支持守望配置");
    if (
      body.task().equals("cpt") && body.searchConfig() != null
    ) throw new IllegalArgumentException("守望不支持寻踪配置");
    Tasks.Plan plan = body.task().equals("cpt")
      ? Cpt.create(
          body.level(),
          body.cptConfig(),
          body.mode().equals("PRACTICE")
        )
      : Tasks.create(body.task(), body.level(), body.searchConfig());
    TrainingSession s = new TrainingSession();
    s.setId(UUID.randomUUID().toString());
    s.setOwner(user);
    s.setTask(body.task());
    s.setLevel(body.level());
    s.setMode(body.mode());
    s.setStatus("RUNNING");
    s.setCreatedAt(Instant.now().toString());
    s.setDevice(body.device());
    s.setPlanJson(json.writeValueAsString(plan));
    mapper.insert(s);
    return Map.of("id", s.getId(), "plan", plan);
  }

  private TrainingSession owned(String id, String user) {
    TrainingSession s = mapper.selectById(id);
    if (
      s == null || !s.getOwner().equals(user)
    ) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return s;
  }

  @PutMapping("/sessions/{id}/result")
  public Tasks.Result finish(
    @PathVariable String id,
    @RequestHeader(value = "Authorization", required = false) String auth,
    @RequestBody Tasks.Submission body
  ) throws Exception {
    TrainingSession s = owned(id, owner(auth));
    if (
      (body.reflection() != null && body.reflection().length() > 1000) ||
      (body.quality() != null && body.quality().length() > 500)
    ) throw new IllegalArgumentException("文本过长");
    String payload = json.writeValueAsString(body);
    if (!s.getStatus().equals("RUNNING")) {
      if (
        !payload.equals(s.getSubmissionJson())
      ) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "此记录已提交，不可改写试次"
      );
      return json.readValue(s.getResultJson(), Tasks.Result.class);
    }
    Tasks.Result result = Tasks.score(
      json.readValue(s.getPlanJson(), Tasks.Plan.class),
      body
    );
    int updated = mapper.update(
      null,
      new UpdateWrapper<TrainingSession>()
        .eq("id", id)
        .eq("owner", s.getOwner())
        .eq("status", "RUNNING")
        .set("status", body.status())
        .set("submission_json", payload)
        .set("result_json", json.writeValueAsString(result))
        .set("reflection", body.reflection())
    );
    if (updated == 0) {
      TrainingSession fresh = owned(id, s.getOwner());
      if (
        !payload.equals(fresh.getSubmissionJson())
      ) throw new ResponseStatusException(HttpStatus.CONFLICT);
    }
    return result;
  }

  @GetMapping("/sessions")
  public List<Map<String, Object>> list(
    @RequestHeader(value = "Authorization", required = false) String auth
  ) throws Exception {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (TrainingSession s : mapper.selectList(
      new QueryWrapper<TrainingSession>()
        .eq("owner", owner(auth))
        .orderByDesc("created_at")
    )) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", s.getId());
      row.put("task", s.getTask());
      row.put("level", s.getLevel());
      Tasks.Plan plan = json.readValue(s.getPlanJson(), Tasks.Plan.class);
      row.put("version", plan.version());
      row.put("searchConfig", plan.searchConfig());
      row.put("cptConfig", plan.cptConfig());
      row.put("mode", s.getMode());
      row.put("status", s.getStatus());
      row.put("createdAt", s.getCreatedAt());
      row.put("device", s.getDevice());
      row.put("reflection", s.getReflection());
      row.put(
        "result",
        s.getResultJson() == null ? null : json.readTree(s.getResultJson())
      );
      rows.add(row);
    }
    return rows;
  }

  @GetMapping("/sessions/{id}")
  public Map<String, Object> detail(
    @PathVariable String id,
    @RequestHeader(value = "Authorization", required = false) String auth
  ) throws Exception {
    TrainingSession s = owned(id, owner(auth));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("plan", json.readTree(s.getPlanJson()));
    out.put(
      "submission",
      s.getSubmissionJson() == null
        ? null
        : json.readTree(s.getSubmissionJson())
    );
    return out;
  }

  @PatchMapping("/sessions/{id}/reflection")
  public void reflect(
    @PathVariable String id,
    @RequestHeader(value = "Authorization", required = false) String auth,
    @Valid @RequestBody Reflection body
  ) throws Exception {
    TrainingSession s = owned(id, owner(auth));
    mapper.update(
      null,
      new UpdateWrapper<TrainingSession>()
        .eq("id", id)
        .eq("owner", s.getOwner())
        .set("reflection", body.reflection())
    );
  }

  @DeleteMapping("/sessions")
  public void delete(
    @RequestHeader(value = "Authorization", required = false) String auth
  ) throws Exception {
    mapper.delete(new QueryWrapper<TrainingSession>().eq("owner", owner(auth)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(IllegalArgumentException e) {
    return Map.of("message", e.getMessage());
  }
}
