package adapters.repositories;

import adapters.repositories.records.Record;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository
        extends JpaRepository<Record, Long>, JpaSpecificationExecutor<Record> {

    @Query(nativeQuery = true, value = """
        SELECT i.*
        FROM items i
        WHERE (i.roles ILIKE '%user%' OR i.roles IS NULL)
          AND (i.codes IS NULL OR EXISTS (
              SELECT 1 FROM UNNEST(i.codes) AS code
              WHERE code IN (:codes)
          ))
        ORDER BY i.priority, i.created DESC
        LIMIT 10
        """)
    List<Record> findItemsForUser(@Param("codes") List<Integer> codes);

    @Query(nativeQuery = true, value = """
        SELECT i.*
        FROM items i
        WHERE (i.roles ILIKE '%user%' OR i.roles IS NULL)
          AND i.codes IS NULL
        ORDER BY i.priority, i.created DESC
        LIMIT 10
        """)
    List<Record> findItemsForUserWithNoCodes();

    @Query(nativeQuery = true, value = """
        SELECT i.*
        FROM items i
        WHERE (i.roles ILIKE '%admin%' OR i.roles IS NULL)
          AND (i.codes IS NULL OR EXISTS (
              SELECT 1 FROM UNNEST(i.codes) AS code
              WHERE code IN (:codes)
          ))
        ORDER BY i.priority, i.created DESC
        LIMIT 10
        """)
    List<Record> findItemsForAdmin(@Param("codes") List<Integer> codes);

    @Query(nativeQuery = true, value = """
        SELECT i.*
        FROM items i
        WHERE (i.roles ILIKE '%admin%' OR i.roles IS NULL)
          AND i.codes IS NULL
        ORDER BY i.priority, i.created DESC
        LIMIT 10
        """)
    List<Record> findItemsForAdminWithNoCodes();

    @Query(nativeQuery = true, value = """
        SELECT i.*
        FROM items i
        WHERE (i.roles ILIKE '%staff%' OR i.roles IS NULL)
          AND (i.codes IS NULL OR EXISTS (
              SELECT 1 FROM UNNEST(i.codes) AS code
              WHERE code IN (:codes)
          ))
        ORDER BY i.priority, i.created DESC
        LIMIT 10
        """)
    List<Record> findItemsForStaff(@Param("codes") List<Integer> codes);

    @Query(nativeQuery = true, value = """
        SELECT i.*
        FROM items i
        WHERE (i.roles ILIKE '%staff%' OR i.roles IS NULL)
          AND i.codes IS NULL
        ORDER BY i.priority, i.created DESC
        LIMIT 10
        """)
    List<Record> findItemsForStaffWithNoCodes();
                
}
