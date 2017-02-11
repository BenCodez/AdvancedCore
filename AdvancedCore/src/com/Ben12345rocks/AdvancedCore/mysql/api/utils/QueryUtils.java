package com.Ben12345rocks.AdvancedCore.mysql.api.utils;

import java.util.Collection;

public class QueryUtils {

    /**
     * Turn a collection into a string separated by a separator.
     *
     * @param collection the collection to be separated
     * @param separator  the separator
     * @return String
     */
    public static String separate(Collection<String> collection, String separator) {
        StringBuilder builder = new StringBuilder();
        String sep = "";
        for (String item : collection) {
            builder.append(sep)
                    .append(item);
            sep = separator;
        }
        return builder.toString();
    }

}
