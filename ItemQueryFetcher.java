package com.example.util;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.example.mapper.ItemMapper;
import com.example.service.DictionaryService;
import com.example.service.ItemService;
import com.example.service.FileStorageService;
import com.example.dto.ItemDto;
import com.example.types.Location;
import com.example.types.Item;
import com.example.types.ItemFilter;
import com.example.types.ItemInput;
import com.example.types.ItemList;
import com.example.types.ItemOrder;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import static com.example.constants.GraphQLConstants.MUTATION.CREATE_ITEM_INPUT_ARGUMENT.ImageFile;
import static com.example.exception.InvalidMimeTypeException.throwInvalidContentType;
import static com.example.util.FileUtils.generateFileIdentifier;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class ItemQueryFetcher {

    private final ItemService itemService;
    private final FileStorageService fileStorageService;
    private final ItemMapper itemMapper;
    private final DictionaryService dictionaryService;

    @Value("${storage.bucket}")
    private String bucket;
    @Value("${storage.directoryPath}")
    private String directoryPath;
    @Value("${storage.url}")
    private String storageUrl;

    @DgsMutation
    public Boolean createItem(
            @InputArgument ItemInput item,
            @NotNull DgsDataFetchingEnvironment dfe
    ) {
        MultipartFile file = dfe.getArgument(ImageFile);
        var originalFileName = file.getOriginalFilename();
        var imageExtension = FilenameUtils.getExtension(originalFileName);

        if (!isValidImageMimeType(file.getContentType())) {
            throw throwInvalidContentType(file.getContentType());
        }

        String fileIdentifier = generateFileIdentifier(
                bucket, directoryPath, item.getTitle(), imageExtension
        );

        var fileData = fileStorageService.uploadFile(fileIdentifier, file);

        var itemDto = itemMapper.map(item);
        itemDto.setPublished(OffsetDateTime.now());
        itemDto.setImageFileId(fileData.getId().longValue());

        return itemService.createItem(itemDto);
    }

    @DgsQuery
    public ItemList managerItems(
            @InputArgument Integer timeZoneOffsetInMinutes,
            @InputArgument List<Integer> locationCodes
    ) {
        var adjustedZoneOffset = ZoneOffset.ofHoursMinutes(
                timeZoneOffsetInMinutes / 60,
                timeZoneOffsetInMinutes % 60
        );

        return getItemsByRoles(adjustedZoneOffset, locationCodes, List.of("manager"));
    }

    @DgsQuery
    public ItemList employeeItems(
            @InputArgument Integer timeZoneOffsetInMinutes,
            @InputArgument List<Integer> locationCodes
    ) {
        var adjustedZoneOffset = ZoneOffset.ofHoursMinutes(
                timeZoneOffsetInMinutes / 60,
                timeZoneOffsetInMinutes % 60
        );

        return getItemsByRoles(
                adjustedZoneOffset,
                locationCodes,
                List.of("specialist", "employee")
        );
    }

    @DgsQuery
    public Item item(
            @InputArgument String id,
            @InputArgument Integer timeZoneOffsetInMinutes
    ) {
        var adjustedZoneOffset = ZoneOffset.ofHoursMinutes(
                timeZoneOffsetInMinutes / 60,
                timeZoneOffsetInMinutes % 60
        );

        var itemDto = itemService.getItem(id);

        adjustPublishedDate(itemDto, adjustedZoneOffset);

        var imageFileId = itemDto.getImageFileId();
        if (nonNull(imageFileId)) {
            itemDto.setImageUrl(getFullPath(imageFileId));
        }

        return itemMapper.map(itemDto);
    }

    @DgsQuery
    public ItemList items(
            @InputArgument ItemFilter filter,
            @InputArgument ItemOrder orderBy,
            @InputArgument Integer from,
            @InputArgument Integer count
    ) {
        var adminItemsDto = itemService.getAdminItems(filter, orderBy, from, count);
        return itemMapper.map(adminItemsDto);
    }

    @DgsQuery
    public List<Location> locations() {
        return dictionaryService.getLocations();
    }

    private ItemList getItemsByRoles(
            ZoneOffset offset,
            List<Integer> locationCodes,
            List<String> roles
    ) {
        var itemsDto = itemService.getItemsByRoles(roles, locationCodes);
        if (isNull(itemsDto) || isNull(itemsDto.getItems())) {
            return null;
        }

        log.info("Retrieved items: '{}'", itemsDto);

        var items = itemsDto.getItems().stream()
                .peek(itemDto -> adjustPublishedDate(itemDto, offset))
                .toList();

        var fullPaths = items.stream()
                .map(ItemDto::getImageFileId)
                .filter(Objects::nonNull)
                .map(id -> {
                    var fullPath = getFullPath(id);
                    log.info("Mapped image file ID '{}' to full path: '{}'", id, fullPath);
                    return Map.entry(id, fullPath);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        var itemsWithFullPaths = setImageUrlForItems(items, fullPaths);
        itemsDto.setItems(itemsWithFullPaths);

        return itemMapper.map(itemsDto);
    }

    private String getFullPath(Long imageFileId) {
        try {
            log.info("Retrieving file with ID: '{}'", imageFileId);
            var file = fileStorageService.getFile(imageFileId.intValue());
            return storageUrl + "/" + file.getFullPath();
        } catch (Exception e) {
            log.error(
                    "Error occurred while processing file ID '{}': '{}'",
                    imageFileId, e.getMessage()
            );
            return Strings.EMPTY;
        }
    }

    private void adjustPublishedDate(
            ItemDto itemDto,
            ZoneOffset adjustedZoneOffset
    ) {
        var publishedDate = itemDto.getPublished();
        itemDto.setPublished(publishedDate.withOffsetSameInstant(adjustedZoneOffset));
    }

    private List<ItemDto> setImageUrlForItems(
            List<ItemDto> items,
            Map<Long, String> fullPaths
    ) {
        return items.stream()
                .peek(dto -> {
                    var fullPath = fullPaths.get(dto.getImageFileId());
                    dto.setImageUrl(fullPath);
                })
                .toList();
    }

    private boolean isValidImageMimeType(String contentType) {
        return nonNull(contentType) && contentType.startsWith("image/");
    }

}
