package com.beep.jobportal.ask;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/ask?q=fresher java jobs in Pune -> filters the AI understood + a short reply. */
@RestController
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @GetMapping("/api/ask")
    public AskService.Answer ask(@RequestParam(defaultValue = "") String q) {
        return askService.ask(q);
    }
}
