package com.project.catxi.member.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.config.security.JwtConfig;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.common.jwt.JwtTokenProvider;
import com.project.catxi.member.dto.AuthDTO;
import com.project.catxi.member.dto.AuthDTO.LoginResponse;
import com.project.catxi.member.dto.IdResponse;
import com.project.catxi.member.dto.MatchHistoryRes;
import com.project.catxi.member.dto.MemberProfileRes;
import com.project.catxi.member.dto.SignUpDTO;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.member.service.MatchHistoryService;
import com.project.catxi.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService memberService;
  private final MatchHistoryService matchHistoryService;
  private final JwtUtil jwtUtil;
  private final JwtTokenProvider jwtTokenProvider;
  private final AuthenticationManager authenticationManager;
  private final JwtConfig jwtConfig;
  private final MemberRepository memberRepository;

  //@Operation(summary = "SignUp Api")
  @PostMapping("/signUp")
  public ResponseEntity<IdResponse> signUp(@RequestBody @Valid SignUpDTO dto) {
    Long id = memberService.signUp(dto);
    return ResponseEntity
        .created(URI.create("/api/members/" + id))
        .body(new IdResponse(id));
  }

  @Operation(summary = "Login API")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody @Valid AuthDTO.LoginRequest request) {

    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.membername(),
            request.password()
        )
    );
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();

    // 사용자 정보 추출
    String username = userDetails.getUsername();
    String role = userDetails.getAuthorities()
        .stream()
        .findFirst()
        .map(GrantedAuthority::getAuthority)
        .orElse("ROLE_USER"); // 기본 권한 처리

    // JWT 생성
    String token = jwtTokenProvider.generateAccessToken(username, role);

    return ResponseEntity.ok(new LoginResponse(token));
  }

  @Operation(summary = "학생 이름 입력시 학번 반환")
  @GetMapping("/stdNo")
  public String getStudentNo(@RequestParam("nickname") String nickname){
    Member member = memberRepository.findByNickname(nickname)
        .orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "해당 닉네임의 회원을 찾을 수 없습니다."
            )
        );
    return member.getStudentNo();
  }

  @Operation(summary = "회원탈퇴 API")
  @PatchMapping("/delete")
  public ApiResponse<String> delete(@AuthenticationPrincipal UserDetails userDetails) {
    memberService.delete(userDetails.getUsername());

    return ApiResponse.success("삭제 완료");
  }

  @Operation(summary = "회원 기본 정보 조회")
  @GetMapping("/")
  public ApiResponse<MemberProfileRes> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
    //userDetails의 username -> email
    String email = userDetails.getUsername();
    MemberProfileRes dto = memberService.getProfile(email);
    return ApiResponse.success(dto);
  }

  @Operation(summary = "이용 기록 단건 조회")
  @GetMapping("/history/{historyId}")
  public ResponseEntity<MatchHistoryRes> getMatchHistoryById(
      @PathVariable("historyId") Long historyId, @AuthenticationPrincipal UserDetails userDetails
  ) {
    String email = userDetails.getUsername();
    MatchHistoryRes res = matchHistoryService.getHistoryById(historyId, email);
    return ResponseEntity.ok(res);
  }

//  @Operation(summary = "이용 기록 최신 2개 조회")
//  @GetMapping("/history/recent")
//  public ResponseEntity<List<MatchHistoryRes>> getRecentHistorySummary(
//      @AuthenticationPrincipal UserDetails userDetails
//  ) {
//    String email = userDetails.getUsername();
//    List<MatchHistoryRes> summaries = matchHistoryService.getRecentHistoryTop2(email);
//    return ResponseEntity.ok(summaries);
//  }

  @Operation(summary = "이용 기록 전부 조회")
  @GetMapping("/history/all")
  public ResponseEntity<Slice<MatchHistoryRes>> getMyMatchHistoryWithScroll(
      @AuthenticationPrincipal UserDetails userDetails,
      @PageableDefault(size = 2, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
  ) {
    String email = userDetails.getUsername();
    Slice<MatchHistoryRes> slice = matchHistoryService.getScrollHistory(email, pageable);
    return ResponseEntity.ok(slice);
  }


}
