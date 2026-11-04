-- V10__append_only_triggers.sql
-- Enforce append-only immutability triggers on audit_log and entries tables

CREATE TRIGGER audit_log_no_update BEFORE UPDATE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();

CREATE TRIGGER audit_log_no_delete BEFORE DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();

CREATE TRIGGER entries_no_update BEFORE UPDATE ON entries
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();

CREATE TRIGGER entries_no_delete BEFORE DELETE ON entries
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();
