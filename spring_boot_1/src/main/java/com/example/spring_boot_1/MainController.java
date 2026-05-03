package com.example.spring_boot_1;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/")
public class MainController {

    @ResponseBody
    @GetMapping("/hello")
        public String hello() {
        return "hello";
        }
}
