package com.example.datafetcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mapper.GenericMapper;
import com.example.mapper.GenericMapperImpl;
import com.example.service.GenericService;
import com.example.service.FileService;
import com.example.dto.GenericDto;
import com.example.types.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Slf4j
class DataFetcherTest {

    @Mock
    private GenericService genericService;
    @Mock
    private FileService fileService;

    private final GenericMapper genericMapper = new GenericMapperImpl();
    private DataFetcher dataFetcher;

    @BeforeEach
    void setUp() {
        dataFetcher = new DataFetcher(genericService, fileService, genericMapper);
    }

    @ParameterizedTest
    @MethodSource("rolesProvider")
    void testFetchDataWhenFileIsEmpty(List<String> roles) {
        List<Integer> facilityCodes = List.of(1, 2);
        var genericDto = createGenericDto();

        var dtoList = new GenericDto();
        dtoList.setItems(List.of(genericDto));

        when(genericService.getDataByRoles(roles, facilityCodes)).thenReturn(dtoList);
        when(fileService.getFile(anyInt())).thenReturn(null);

        Function<Integer, ?> fetchMethod = roles.contains("manager")
                ? (offset) -> dataFetcher.managerData(offset, facilityCodes)
                : (offset) -> dataFetcher.userData(offset, facilityCodes);

        assertData(roles, dtoList, fetchMethod);
        verify(fileService).getFile(anyInt());
    }

    @Test
    void shouldFetchAdminDataWithFilterAndOrder() {
        var filter = DataFilter.newBuilder()
                .facilityCodes(List.of(14, 20))
                .build();
        var order = DataOrder.newBuilder()
                .priority(SortingOrder.ASC)
                .build();
        var adminDto = createAdminDto();

        when(genericService.getAdminData(filter, order, 1, 4)).thenReturn(adminDto);

        DataList actual = dataFetcher.fetchData(filter, order, 1, 4);

        // Assert
        verify(genericService).getAdminData(filter, order, 1, 4);
        assertEquals(genericMapper.map(adminDto), actual);
    }

    // Provide parameters for test
    static List<List<String>> rolesProvider() {
        return List.of(
                List.of("specialist", "employee"),
                List.of("manager")
        );
    }

    private void assertData(
            List<String> roles,
            GenericDto dtoList,
            Function<Integer, ?> fetchMethod
    ) {
        var result = fetchMethod.apply(360);
        verify(genericService).getDataByRoles(roles, List.of(1, 2));
        assertEquals(genericMapper.map(dtoList), result);
    }

    private GenericDto createGenericDto() {
        var genericDto = new GenericDto();
        genericDto.setPictureFileId(0L);
        genericDto.setPictureUrl("");
        genericDto.setPublished(OffsetDateTime.now());

        return genericDto;
    }

    private AdminDto createAdminDto() {
        var adminDto = new AdminDto();
        adminDto.setItems(List.of(createGenericDto()));
        return adminDto;
    }

}
