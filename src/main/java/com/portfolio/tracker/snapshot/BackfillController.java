package com.portfolio.tracker.snapshot;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/backfill")
@RequiredArgsConstructor
public class BackfillController {

    private final BackfillService backfillService;

    @PostMapping
    public String trigger(@RequestParam(defaultValue = "90") int daysBack) {
        backfillService.backfillAll(daysBack);
        return "Backfill lancé en arrière-plan pour " + daysBack + " jours";
    }
}