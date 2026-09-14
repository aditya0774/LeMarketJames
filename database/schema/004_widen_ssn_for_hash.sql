-- Registration stores a bcrypt hash (60 characters), not an 11-character SSN.
-- Widening preserves existing data and is safe to reapply to persistent volumes.
ALTER TABLE clients ALTER COLUMN ssn TYPE VARCHAR(60);
