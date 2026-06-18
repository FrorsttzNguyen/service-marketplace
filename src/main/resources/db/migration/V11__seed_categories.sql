-- =============================================================================
-- V11: Seed top-level service categories (reference data).
--
-- WHY: a service requires a category_id (FK, NOT NULL on services). Nothing seeded
-- the categories table and there is no API to create one, so a fresh database could
-- never have a usable catalog. These are reference rows the whole catalog depends on.
--
-- Idempotent: ON CONFLICT (slug) DO NOTHING — slug is UNIQUE, so re-running (or running
-- against a DB that already has some of these) is a safe no-op, never a duplicate.
-- =============================================================================
INSERT INTO categories (name, slug, description) VALUES
    ('Home Cleaning',  'home-cleaning',  'House and apartment cleaning services'),
    ('Plumbing',       'plumbing',       'Repairs, installation and maintenance for plumbing'),
    ('Electrical',     'electrical',     'Wiring, fixtures and electrical repairs'),
    ('Photography',    'photography',    'Event, portrait and product photography'),
    ('Tutoring',       'tutoring',       'Academic and skills tutoring'),
    ('Moving',         'moving',         'Local moving and relocation help')
ON CONFLICT (slug) DO NOTHING;
