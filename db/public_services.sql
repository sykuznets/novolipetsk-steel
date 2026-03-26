-- liquibase formatted sql
-- changeset kuznets_sy:public_services.sql splitStatements:false endDelimiter:$$

-- Insert placeholder services and attach them to categories
DO $$
DECLARE
    rec RECORD;
    _service_id INT;
BEGIN
    FOR rec IN
        SELECT *
        FROM (
            VALUES
                ('Onboarding Assistant', 'employee',
                'https://example.com/onboarding/',
                'My Services/Work/Onboarding/profile.svg',
                'Work Tools', 10),

                ('Onboarding Assistant', 'specialist',
                'https://example.com/onboarding/',
                'My Services/Work/Onboarding/profile.svg',
                'Work Tools', 9),

                ('Pay Schedule', 'employee',
                'https://example.com/payroll/#schedule',
                'My Services/Finance/Pay Schedule/calendar.svg',
                'Personal Finance', 3),

                ('Pay Schedule', 'specialist',
                'https://example.com/payroll/#schedule',
                'My Services/Finance/Pay Schedule/calendar.svg',
                'Personal Finance', 3)
        ) AS t(title, role, url, image_path, category_title, sort_order)
    LOOP
        SELECT id INTO _service_id
        FROM service
        WHERE title = rec.title
          AND role = rec.role
          AND image_path = rec.image_path;

        IF _service_id IS NULL THEN
            INSERT INTO service (title, url, image_path, role, is_under_construction, is_visible)
            VALUES (
                rec.title,
                rec.url,
                rec.image_path,
                rec.role,
                false,
                true
            )
            RETURNING id INTO _service_id;
        END IF;

        INSERT INTO service_in_category (service_id, category_id, sort_order)
        SELECT
            _service_id,
            sc.id,
            rec.sort_order
        FROM service_category sc
        WHERE sc.title = rec.category_title
          AND NOT EXISTS (
              SELECT 1
              FROM service_in_category sic
              WHERE sic.service_id = _service_id
                AND sic.category_id = sc.id
          );
    END LOOP;
END $$;

