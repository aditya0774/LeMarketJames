-- Enforce unique client emails
ALTER TABLE clients ADD CONSTRAINT clients_email_key UNIQUE (email);
