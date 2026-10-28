CREATE TABLE IF NOT EXISTS training_session (
 id VARCHAR(36) PRIMARY KEY,
 owner VARCHAR(64) NOT NULL,
 task VARCHAR(20) NOT NULL,
 level INT NOT NULL,
 mode VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL,
 created_at VARCHAR(40) NOT NULL,
 plan_json TEXT NOT NULL,
 submission_json TEXT,
 result_json TEXT,
 reflection VARCHAR(1000),
 device VARCHAR(500) NOT NULL
);
