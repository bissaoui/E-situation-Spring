package com.example.situation.security;

import java.text.Normalizer;
import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC).trim();
        value = value.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        value = value.replaceAll("<[^>]*>", "");
        value = value.replaceAll("\\s{2,}", " ");
        return value;
    }
}
