package com.portfolio.tracker.snapshot;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/backfill")
@RequiredArgsConstructor
public class BackfillController {

    private final PortfolioHistoryService historyService;

    /**
     * Rattrapage manuel de l'historique (prix + snapshots).
     *
     * @param full true = recalcul complet depuis la première transaction de
     *             chaque portefeuille ; false = depuis le dernier snapshot
     */
    @PostMapping
    public String trigger(@RequestParam(defaultValue = "false") boolean full) {
        if (full) {
            historyService.refreshAllFull();
            return "Historique entièrement recalculé";
        }
        historyService.catchUp();
        return "Historique rattrapé depuis le dernier snapshot";
    }
}
