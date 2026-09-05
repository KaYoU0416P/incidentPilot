package com.incidentpilot.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("models")
@RequestMapping("/api/v1/demo")
public class DemoController {
    private final DemoService demo;
    public DemoController(DemoService demo) { this.demo = demo; }
    @PostMapping("/seed") public Object seed() { return demo.seed(); }
    @GetMapping("/cases") public Object cases() { return demo.corpus().cases(); }
}
