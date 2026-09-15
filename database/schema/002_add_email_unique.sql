-- Enforce unique client emails
-- Skip if constraint already exists (it may be in 001_core_schema.sql)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints 
    WHERE constraint_name = 'clients_email_key' AND table_name = 'clients'
  ) THEN
    ALTER TABLE clients ADD CONSTRAINT clients_email_key UNIQUE (email);
  END IF;
END $$;
