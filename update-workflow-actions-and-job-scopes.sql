-- File: update_workflow_actions_and_job_scopes.sql
-- liquibase formatted SQL
-- changeset kuznets_sy:134

-- Modify the 'action_code' column in the workflow_actions table.
ALTER TABLE workflow_actions ALTER COLUMN action_code TYPE VARCHAR(64);

-- Insert or update a set of actions into the workflow_actions table.
INSERT INTO workflow_actions (action_code, action_name) VALUES
    ('actualize', 'Actualizes'),
    ('analyze', 'Analyzes'),
    ('execute', 'Executes'),
    ('consult', 'Consults'),
    ('control', 'Controls'),
    ('coordinate', 'Coordinates'),
    ('provide', 'Provides'),
    ('organize', 'Organizes'),
    ('implement', 'Implements')
ON CONFLICT (action_name)
DO UPDATE SET action_code = EXCLUDED.action_code;
