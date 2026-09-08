-- database/schema/003_add_investment_experience.sql
ALTER TABLE clients
    ADD COLUMN investment_experience VARCHAR(20) NOT NULL DEFAULT 'beginner'
    CHECK (investment_experience IN ('beginner', 'experienced'));