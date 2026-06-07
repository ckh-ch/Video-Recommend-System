CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY,
  username VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS videos (
  id BIGINT PRIMARY KEY,
  category VARCHAR(50),
  tags VARCHAR(500),
  duration INT DEFAULT 0,
  view_count INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_behavior (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_id BIGINT NOT NULL,
  video_category VARCHAR(50),
  like_type INT DEFAULT 0,
  relay_type INT DEFAULT 0,
  viewing_time DOUBLE DEFAULT 0,
  behavior_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_behavior_time (behavior_time)
);

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY,
  interest_tags VARCHAR(500),
  avg_viewing_time DOUBLE DEFAULT 0,
  total_watch_count INT DEFAULT 0,
  like_rate DOUBLE DEFAULT 0,
  active_level INT DEFAULT 1,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recommend_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  video_ids TEXT NOT NULL,
  strategy VARCHAR(20) DEFAULT 'ALS',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_id (user_id)
);
