-- database/schema/003_add_investment_experience.sql
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'clients' AND column_name = 'investment_experience'
  ) THEN
    ALTER TABLE clients
        ADD COLUMN investment_experience VARCHAR(20) NOT NULL DEFAULT 'beginner';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints 
    WHERE constraint_name = 'check_investment_experience' AND table_name = 'clients'
  ) THEN
    ALTER TABLE clients
        ADD CONSTRAINT check_investment_experience
        CHECK (investment_experience IN ('beginner', 'experienced'));
  END IF;
END $$;