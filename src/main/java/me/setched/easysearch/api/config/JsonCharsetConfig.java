package me.setched.easysearch.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Makes JSON responses explicitly declare {@code charset=utf-8} in their Content-Type header.
 * Spring omits it by default (JSON is always UTF-8 per RFC 8259), but some HTTP clients — e.g.
 * Windows PowerShell 5.1's {@code Invoke-RestMethod} — don't assume UTF-8 without it and garble
 * non-ASCII text such as Cyrillic product names.
 */
@Configuration
public class JsonCharsetConfig implements WebMvcConfigurer {

    /**
     * Restricts the Jackson message converter to a UTF-8-charset JSON media type.
     *
     * @param converters the message converters Spring MVC will use, mutated in place
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setSupportedMediaTypes(List.of(new MediaType("application", "json", StandardCharsets.UTF_8)));
            }
        }
    }
}
