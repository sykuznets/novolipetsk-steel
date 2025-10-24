package com.example.utils;

import static com.example.common.converter.PathGenerator.generatePath;
import static com.example.utils.Constants.FORM;
import static com.example.utils.Constants.FORM_SECTIONS;
import static com.example.utils.Constants.GOAL_NAMES.CATEGORY_ELEMENT;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import com.example.common.converter.ExpandConverter;
import com.example.domain.FormSection;
import com.example.domain.FormContent;
import com.example.dto.FormContentDto;
import com.example.dto.FormDto;
import com.example.dto.GoalEvent;
import com.example.mapper.GoalMapper;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GoalUtils {

    public static List<GoalEvent.LimitedFormDto> convertToLimitedFormDtos(
            List<FormSection> formSections,
            ExpandConverter<FormContent, FormContentDto> formContentConverter,
            GoalMapper goalMapper
    ) {
        if (formSections == null) {
            return emptyList();
        }

        return formSections.stream()
                .map(FormSection::getFormContent)
                .filter(Objects::nonNull)
                .map(formContent -> {
                    var formContentDto = formContentConverter.convert(
                            formContent,
                            Set.of(generatePath(FORM_SECTIONS, CATEGORY_ELEMENT), FORM)
                    );

                    return goalMapper.toLimitedFormDto(
                            ofNullable(formContentDto.getForm()).map(FormDto::getId).orElse(null),
                            formContentDto
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }
  
}
