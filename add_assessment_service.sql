-- liquibase formatted sql
-- changeset add_assessment_service splitStatements:false

-- Insert new service "Assessment Portal" for role = 'employee'
DO $$
DECLARE
    v_service_id INT;
BEGIN
    SELECT id
    INTO v_service_id
    FROM service
    WHERE title = 'Assessment Portal' AND role = 'employee';

    IF v_service_id IS NULL THEN
        INSERT INTO service (
            title,
            url,
            image_path,
            role,
            is_under_construction,
            is_visible
        )
        VALUES (
            'Assessment Portal',
            'https://training.example.com/',
            'Services/Career Development/Assessment Portal/AssessmentPortal.svg',
            'employee',
            false,
            true
        )
        RETURNING id INTO v_service_id;

        INSERT INTO service_in_category (service_id, category_id, sort_order)
        SELECT v_service_id, sc.id, 2
        FROM service_category sc
        WHERE sc.title = 'Career Development';
    END IF;
END $$;

-- Insert new service "Assessment Portal" for role = 'specialist'
DO $$
DECLARE
    v_service_id INT;
BEGIN
    SELECT id
    INTO v_service_id
    FROM service
    WHERE title = 'Assessment Portal' AND role = 'specialist';

    IF v_service_id IS NULL THEN
        INSERT INTO service (
            title,
            url,
            image_path,
            role,
            is_under_construction,
            is_visible
        )
        VALUES (
            'Assessment Portal',
            'https://training.example.com/',
            'Services/Career Development/Assessment Portal/AssessmentPortal.svg',
            'specialist',
            false,
            true
        )
        RETURNING id INTO v_service_id;

        INSERT INTO service_in_category (service_id, category_id, sort_order)
        SELECT v_service_id, sc.id, 4
        FROM service_category sc
        WHERE sc.title = 'Career Development';
    END IF;
END $$;

-- Update sort order for services in category "Career Development"
UPDATE service_in_category sic
SET sort_order = CASE
    -- Employee services
    WHEN s.title = 'Training' AND s.role = 'employee' THEN 3
    WHEN s.title = 'Internal Vacancies' AND s.role = 'employee' THEN 4
    WHEN s.title = 'Competitions' AND s.role = 'employee' THEN 5
    WHEN s.title = 'Library' AND s.role = 'employee' THEN 6
    WHEN s.title = 'Parent–Child Relations School' AND s.role = 'employee' THEN 7
    WHEN s.title = 'Knowledge Area Booking' AND s.role = 'employee' THEN 8

    -- Specialist services
    WHEN s.title = 'Training' AND s.role = 'specialist' THEN 5
    WHEN s.title = 'Career and Development' AND s.role = 'specialist' THEN 6
    WHEN s.title = 'Internal Vacancies' AND s.role = 'specialist' THEN 7
    WHEN s.title = 'Competitions' AND s.role = 'specialist' THEN 8
    WHEN s.title = 'Library' AND s.role = 'specialist' THEN 9
    WHEN s.title = 'Parent–Child Relations School' AND s.role = 'specialist' THEN 10
    WHEN s.title = 'Knowledge Area Booking' AND s.role = 'specialist' THEN 11
    WHEN s.title = 'Career Consulting' AND s.role = 'specialist' THEN 12

    ELSE sic.sort_order
END
FROM service s
JOIN service_category sc ON sc.id = sic.category_id
WHERE sic.service_id = s.id
  AND sc.title = 'Career Development'
  AND (
      (s.role = 'employee' AND s.title IN (
          'Training',
          'Internal Vacancies',
          'Competitions',
          'Library',
          'Parent–Child Relations School',
          'Knowledge Area Booking'
      ))
      OR
      (s.role = 'specialist' AND s.title IN (
          'Training',
          'Career and Development',
          'Internal Vacancies',
          'Competitions',
          'Library',
          'Parent–Child Relations School',
          'Knowledge Area Booking',
          'Career Consulting'
      ))
  );
