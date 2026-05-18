-- liquibase formatted SQL
-- changeset kuznets_sy:add_health_services splitStatements:false endDelimiter:$

DO $$
DECLARE
    v_category_id BIGINT;
    v_service_id  BIGINT;
    v_record      RECORD;
BEGIN
    SELECT id
    INTO v_category_id
    FROM service_category
    WHERE title = 'Health Services';

    IF v_category_id IS NULL THEN
        RAISE EXCEPTION 'Category "Health Services" not found';
    END IF;

    FOR v_record IN
        WITH roles AS (
            SELECT UNNEST(ARRAY['employee', 'specialist']) AS role
        ),
        base_services AS (
            SELECT *
            FROM (
                VALUES
                    -- url, facility_codes, sort_order
                    ('https://example.com/location-1/', ARRAY[1], 17),
                    ('https://example.com/location-2/', ARRAY[11], 18),
                    ('https://example.com/location-3/', ARRAY[3, 17], 19),
                    ('https://example.com/location-4/', ARRAY[4], 20),
                    ('https://example.com/location-5/', ARRAY[6], 21),
                    ('https://example.com/location-6/', ARRAY[5], 22),
                    ('https://example.com/location-7/', ARRAY[2], 23)
            ) AS t (url, facility_codes, sort_order)
        )
        SELECT
            r.role,
            b.url,
            b.facility_codes,
            b.sort_order
        FROM base_services b
        CROSS JOIN roles r
    LOOP
        SELECT id
        INTO v_service_id
        FROM service
        WHERE title = 'Sanatorium and Resort Treatment'
          AND role = v_record.role
          AND url = v_record.url;

        IF v_service_id IS NULL THEN
            INSERT INTO service (
                title, url, image_path, role, is_under_construction, is_visible, facility_codes
            )
            VALUES (
                'Sanatorium and Resort Treatment',
                v_record.url,
                'Health Services/Sanatorium and Resort Treatment/document-recognition.svg',
                v_record.role,
                FALSE,
                TRUE,
                v_record.facility_codes
            )
            RETURNING id INTO v_service_id;
        END IF;

        INSERT INTO service_in_category (service_id, category_id, sort_order)
        SELECT v_service_id, v_category_id, v_record.sort_order
        WHERE NOT EXISTS (
            SELECT 1
            FROM service_in_category sic
            WHERE sic.service_id = v_service_id
              AND sic.category_id = v_category_id
        );
    END LOOP;
END $$;
