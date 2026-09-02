package com.medirag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards SPA routes to the built frontend entry.
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/forgot-password",
            "/chat",
            "/chat/{id}",
            "/knowledge",
            "/dashboard",
            "/users",
            "/ai-config",
            "/profile"
    })
    public String index() {
        return "forward:/index.html";
    }
}
