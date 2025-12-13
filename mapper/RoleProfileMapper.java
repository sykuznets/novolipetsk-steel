package com.example.publicapi.mapper;

import com.example.publicapi.dto.RoleProfileDto;
import com.example.publicapi.models.*;
import com.example.publicapi.models.relation.RoleTaskRelation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.LocalDate;

@Mapper(config = MapperConfiguration.class)
public interface RoleProfileMapper {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "createDate", expression = "java(currentDate())")
    @Mapping(target = "certification", expression = "java(extractCertification(dto))")
    @Mapping(target = "specialization", expression = "java(extractSpecialization(dto))")
    @Mapping(target = "softwareSkills", expression = "java(extractSoftwareSkills(dto))")
    @Mapping(target = "isShiftRole", expression = "java(dto.position().isShiftRole())")
    @Mapping(target = "isProductionRole", expression = "java(dto.position().isProductionRole())")
    @Mapping(target = "rolePurpose", expression = "java(dto.position().purpose())")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "status", source = "status")
    RoleProfile toRoleProfile(
            RoleProfileDto dto,
            User author,
            Role role,
            ProfileStatus status
    );

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "taskActions", ignore = true)
    @Mapping(target = "responsibilities", ignore = true)
    @Mapping(target = "roleScopes", ignore = true)
    @Mapping(target = "approvers", ignore = true)
    @Mapping(target = "educationRequirements", ignore = true)
    @Mapping(target = "experienceRequirements", ignore = true)
    @Mapping(target = "languageRequirements", ignore = true)
    void updateProfile(RoleProfile updatedProfile, @MappingTarget RoleProfile profile);

    @Named("currentDate")
    default LocalDate currentDate() {
        return LocalDate.now();
    }

    default String extractCertification(RoleProfileDto dto) {
        return dto.requirements() != null ? dto.requirements().certification() : null;
    }

    default String extractSpecialization(RoleProfileDto dto) {
        return dto.requirements() != null ? dto.requirements().specialization() : null;
    }

    default String extractSoftwareSkills(RoleProfileDto dto) {
        return dto.requirements() != null ? dto.requirements().softwareSkills() : null;
    }
  
}
