package com.office.calendar.planner;

import com.office.calendar.planner.util.UploadFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/planner")
@RequiredArgsConstructor
public class PlannerController {

    final private PlannerService plannerService;
    final private UploadFileService uploadFileService;

    @GetMapping({"", "/"})
    public String home() {
        log.info("home()");

        String nextPage = "planner/home";

        return nextPage;

    }

    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> writePlan(
            PlannerDto plannerDto,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        log.info("writePlan()");

        String signInedMemberID = principal.getName();

        // SAVE FILE
        String savedFileName = uploadFileService.upload(signInedMemberID, file);
        if (savedFileName != null) {
            plannerDto.setImg_name(savedFileName);
            plannerDto.setOwner_id(signInedMemberID);

            Map<String, Object> resultMap = plannerService.writePlan(plannerDto);
            return ResponseEntity.ok(resultMap);    // 200 OK

        } else {
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("message", "File upload failed!!");
            return ResponseEntity.badRequest().body(errorMap);      // 400 BAD

        }

    }

    // 일정들 가져오기
    @GetMapping("/plans")
    public ResponseEntity<Map<String, Object>> getPlans(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            Principal principal) {
        log.info("getPlans()");

        Map<String, Object> resultMap = plannerService.getPlans(year, month, principal.getName());

        return ResponseEntity.ok(resultMap);

    }

    // 일정 가져오기
    @GetMapping("/plan")
    public ResponseEntity<Map<String, Object>> getPlan(
            @RequestParam("no") int no
    ) {
        log.info("getPlan()");

        Map<String, Object> resultMap = plannerService.getPlan(no);
        if (resultMap.get("plan") == null) {
            return ResponseEntity.notFound().build();      // 404 NOT FOUND

        }

        return ResponseEntity.ok(resultMap);

    }

    // 일정 삭제하기
    @DeleteMapping("/plan/{no}")
    public ResponseEntity<Map<String, Object>> removePlan(@PathVariable("no") int no) {
        log.info("removePlan()");

        Map<String, Object> resultMap = plannerService.removePlan(no);

        return ResponseEntity.ok(resultMap);

    }

    // 일정 수정하기
    @PutMapping("/plan/{no}")
    public ResponseEntity<Map<String, Object>> modifyPlan(
            @PathVariable("no") int no,
            PlannerDto plannerDto,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal
    ) {
        log.info("modifyPlan()");

        plannerDto.setNo(no);
        if (file != null) {
            String signInedMemberID = principal.getName();
            String savedFileName = uploadFileService.upload(signInedMemberID, file);
            if (savedFileName != null) {
                plannerDto.setImg_name(savedFileName);
                plannerDto.setOri_owner_id(signInedMemberID);
                Map<String, Object> resultMap = plannerService.modifyPlan(plannerDto);
                return ResponseEntity.ok(resultMap);

            } else {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("message", "File upload Fail");
                return ResponseEntity.badRequest().body(errorMap);

            }

        } else {
            Map<String, Object> resultMap = plannerService.modifyPlan(plannerDto);
            return ResponseEntity.ok(resultMap);

        }

    }

}
