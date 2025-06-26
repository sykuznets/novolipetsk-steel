package com.example.util;

import static java.util.Objects.isNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JobDescriptionUtils {

    public static <T> void resetRelation(
            Supplier<List<T>> getter,
            Consumer<List<T>> setter
    ) {
        List<T> collection = getter.get();
        setter.accept(isNull(collection) ? new ArrayList<>() : clearAndReturn(collection));
    }

    private static <T> List<T> clearAndReturn(List<T> list) {
        list.clear();
        return list;
    }

}
