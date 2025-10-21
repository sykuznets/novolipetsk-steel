--liquibase formatted sql
--changeset kuznets_sy:v1-create-initial-tables

-- Create table for goals
CREATE TABLE project_goals (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    year NUMERIC(4, 0) NOT NULL,
    category_code BIGINT NOT NULL,
    title TEXT NOT NULL,
    parent_goal_id BIGINT NULL,
    last_modified TIMESTAMP NOT NULL
);

-- Create join table between goals and form sections
CREATE TABLE project_goals_form_sections (
    goal_id BIGINT NOT NULL,
    form_section_id BIGINT NOT NULL,
    PRIMARY KEY (goal_id, form_section_id),
    CONSTRAINT fk_goal FOREIGN KEY (goal_id) REFERENCES project_goals (id)
);

-- Create table for outgoing Kafka messages
CREATE TABLE event_jobs (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    message_type VARCHAR(255) NOT NULL,
    message_status VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL
);
