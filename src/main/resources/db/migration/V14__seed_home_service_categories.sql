-- =============================================================================
-- V14: Add focused home-service categories.
--
-- WHY: V11 already seeded broad home/local categories, but the public catalog should
-- look more clearly like a home-services marketplace on a fresh database. These rows
-- are additive and idempotent so existing databases keep their applied V11 history.
-- =============================================================================
INSERT INTO categories (name, slug, description) VALUES
    ('Deep Cleaning',     'deep-cleaning',     'Detailed one-time cleaning for kitchens, bathrooms, and hard-to-reach areas'),
    ('Move-out Cleaning', 'move-out-cleaning', 'End-of-lease or post-move cleaning for apartments and houses'),
    ('Carpet Cleaning',   'carpet-cleaning',   'Carpet shampooing, stain treatment, and room refresh services'),
    ('Handyman',          'handyman',          'Small repairs, mounting, assembly, and general home maintenance'),
    ('Gardening',         'gardening',         'Lawn care, planting, pruning, and outdoor cleanup help')
ON CONFLICT (slug) DO NOTHING;
