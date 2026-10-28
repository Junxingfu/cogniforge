package com.cogniforge;

import com.baomidou.mybatisplus.annotation.*;

@TableName("training_session")
public class TrainingSession {

  @TableId(type = IdType.INPUT)
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value;
  }

  private String owner;

  public String getOwner() {
    return owner;
  }

  public void setOwner(String value) {
    owner = value;
  }

  private String task;

  public String getTask() {
    return task;
  }

  public void setTask(String value) {
    task = value;
  }

  private Integer level;

  public Integer getLevel() {
    return level;
  }

  public void setLevel(Integer value) {
    level = value;
  }

  private String mode;

  public String getMode() {
    return mode;
  }

  public void setMode(String value) {
    mode = value;
  }

  private String status;

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  private String createdAt;

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String value) {
    createdAt = value;
  }

  private String planJson;

  public String getPlanJson() {
    return planJson;
  }

  public void setPlanJson(String value) {
    planJson = value;
  }

  private String submissionJson;

  public String getSubmissionJson() {
    return submissionJson;
  }

  public void setSubmissionJson(String value) {
    submissionJson = value;
  }

  private String resultJson;

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String value) {
    resultJson = value;
  }

  private String reflection;

  public String getReflection() {
    return reflection;
  }

  public void setReflection(String value) {
    reflection = value;
  }

  private String device;

  public String getDevice() {
    return device;
  }

  public void setDevice(String value) {
    device = value;
  }
}
