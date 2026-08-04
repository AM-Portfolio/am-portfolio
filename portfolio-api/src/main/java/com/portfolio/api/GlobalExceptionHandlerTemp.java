package com.portfolio.api;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.io.PrintWriter;
import java.io.FileWriter;

@ControllerAdvice
public class GlobalExceptionHandlerTemp {
    @ExceptionHandler(Exception.class)
    public void handleException(Exception ex) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("C:/Users/drabh/Documents/am-repo/am-portfolio/error.txt", true))) {
            ex.printStackTrace(pw);
        } catch (Exception ignored) {}
        throw new RuntimeException(ex);
    }
}
