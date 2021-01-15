package com.dee.custom.mvcframework.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public class HandlerMappingUtil {
    /**
     * 判断是不是/开头
     *
     * @param path
     */
    public static String buildFistPath(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }
    /**
     * 组装mapping
     * @param mapping
     * @return
     */
    public static String filterNullMapping(String... mapping){
        StringBuilder sb = new StringBuilder();
        Arrays.stream(mapping)
                .filter(StringUtils::isNoneEmpty)
                .forEach(s->sb.append(buildFistPath(s)));
        return sb.toString();
    }
}
