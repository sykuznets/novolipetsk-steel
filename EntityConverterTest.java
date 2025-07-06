package com.example.integration.converters;

import static java.math.BigDecimal.valueOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.integration.pending.PendingService;
import com.example.integration.response.DataDto;
import com.example.services.*;
import com.example.models.Rating;
import com.example.models.Evaluation;
import com.example.models.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

class EntityConverterTest {

    @InjectMocks
    private EntityConverter entityConverter;

    @Mock
    private DepartmentService departmentService;
    @Mock
    private DisciplineService disciplineService;
    @Mock
    private EvaluationService evaluationService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PendingService<Entity, Map<String, String>> pendingService;
    @Mock
    private RatingService ratingService;
    @Mock
    private SubcategoryRateService subcategoryRateService;
    @Mock
    private CityService cityService;
    @Mock
    private EvaluationConverter evaluationConverter;
    @Mock
    private RatingConverter ratingConverter;
    @Mock
    private EntitySubcategoryRateConverter entitySubcategoryRateConverter;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        when(ratingConverter.convert(anyList())).thenAnswer(invocation -> {
            List<DataDto> dtos = invocation.getArgument(0);
            String code = dtos.isEmpty() ? null : dtos.getFirst().code();
            return ("001".equals(code) || "002".equals(code)) ? createExpectedRating() : null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRatingDeduplicationDuringConversion() {
        List<DataDto> dataDtos = List.of(
                new DataDto("001", "Actual", "RatingName1", LocalDate.now(), null, null, null),
                new DataDto("002", "Actual", "RatingName2", LocalDate.now(), null, null, null)
        );

        Rating rating = createExpectedRating();
        when(evaluationConverter.convert(anyList())).thenReturn(new Evaluation());
        when(ratingService.saveRatings(List.of(rating))).thenReturn(List.of(rating));

        // Act
        entityConverter.convert(dataDtos);

        ArgumentCaptor<List<Rating>> ratingCaptor = ArgumentCaptor.forClass(List.class);
        verify(ratingService).saveRatings(ratingCaptor.capture());

        // Assert
        List<Rating> capturedRatings = ratingCaptor.getValue();
        assertEquals(1, capturedRatings.size(), "Expected deduplication to result in 1 unique Rating object");

        Rating result = capturedRatings.getFirst();
        assertEquals(5, result.getLevel());
        assertEquals(valueOf(1000), result.getMinValue());
        assertEquals(valueOf(2000), result.getMaxValue());

        verify(pendingService, times(1)).setPending(any(Entity.class), anyMap());
        verify(entitySubcategoryRateConverter, times(2)).convert(anyList());

        verifyNoInteractions(disciplineService);
        verifyNoInteractions(departmentService);
        verifyNoInteractions(cityService);
        verifyNoInteractions(categoryService);
        verifyNoInteractions(evaluationService);
        verifyNoInteractions(subcategoryRateService);
    }

    private Rating createExpectedRating() {
        Rating rating = new Rating();
        rating.setLevel(5);
        rating.setMinValue(valueOf(1000));
        rating.setMaxValue(valueOf(2000));
        return rating;
    }

}
