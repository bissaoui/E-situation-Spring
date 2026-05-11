package com.example.situation.config;

import com.example.situation.security.InputSanitizer;
import java.beans.PropertyEditorSupport;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
public class WebInputSanitizingAdvice {

    private final InputSanitizer inputSanitizer;

    public WebInputSanitizingAdvice(InputSanitizer inputSanitizer) {
        this.inputSanitizer = inputSanitizer;
    }

    @InitBinder
    public void sanitizeStringInputs(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(inputSanitizer.sanitize(text));
            }
        });
    }
}
