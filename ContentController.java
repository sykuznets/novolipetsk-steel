package com.example.application.controllers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.example.api.ContentApi;
import com.example.api.dto.ContentItemDto;
import com.example.api.dto.ContentItemsDto;
import com.example.api.dto.ContentItemsResponse;
import com.example.domain.model.Role;
import com.example.domain.model.primitives.DateRange;
import com.example.domain.operations.GetContentItemsOperation;
import com.example.domain.operations.enums.SortingOrder;
import com.example.domain.operations.enums.SortingType;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ContentController extends BaseController implements ContentApi {

    private final GetContentItemsOperation contentItemsOperation;
    private final ControllersMapper mapper;

    @Value("${content.default-count}")
    private int defaultItemCount;

    @Override
    @PreAuthorize("hasAuthority('MS_CONTENT_MANAGE')")
    public ResponseEntity<ContentItemsResponse> getContentItems(
            String sortBy,
            String sortDirection,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate publishedFrom,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate publishedTo,
            List<String> roles,
            List<Integer> facilityCodes,
            Integer offset,
            Integer limit
    ) {
        log.info("Received request to retrieve content items");

        List<Role> roleFilters = (roles != null && !roles.isEmpty())
                ? roles.stream().map(Role::fromValue).toList()
                : null;

        SortingType sortingType = SortingType.fromString(sortBy);
        SortingOrder sortingOrder = SortingOrder.fromString(sortDirection);
        int resolvedLimit = (limit != null && limit > 0) ? limit : defaultItemCount;

        DateRange dateRange = DateRange.builder()
                .from(publishedFrom)
                .to(publishedTo)
                .build();

        var result = contentItemsOperation.execute(
                sortingType,
                sortingOrder,
                dateRange,
                roleFilters,
                facilityCodes,
                offset,
                resolvedLimit
        );

        log.info("Fetched {} content items", result.getKey().size());

        List<ContentItemDto> itemDtos = mapper.map(result.getKey());
        Long totalCount = result.getValue();

        ContentItemsResponse response = buildContentItemsResponse(itemDtos, offset, resolvedLimit, totalCount);

        return ResponseEntity.ok(response);
    }

    private ContentItemsResponse buildContentItemsResponse(
            List<ContentItemDto> items,
            Integer offset,
            Integer limit,
            Long totalCount
    ) {
        ContentItemsDto dto = new ContentItemsDto();
        dto.setItems(items);
        dto.setPage(offset != null ? offset / limit : 0);
        dto.setPageSize(limit);
        dto.setTotalItems(totalCount != null ? totalCount.intValue() : 0);

        ContentItemsResponse response = new ContentItemsResponse();
        response.setData(dto);

        return response;
    }
  
}
