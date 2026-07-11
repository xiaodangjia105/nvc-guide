package nvc.guide.modules.nvcprofile.controller;

import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcprofile.service.NvcDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nvc/dashboard")
@RequiredArgsConstructor
public class NvcDashboardController {

    private final NvcDashboardService dashboardService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getUserStats(@RequestParam Long userId) {
        return Result.success(dashboardService.getUserStats(userId));
    }
}
