package com.project.catxi.member.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDTO {
  private AuthDTO(){

  }

  public static record LoginRequest(
      @NotBlank String membername,
      @NotBlank String password
  ){}

  public static record LoginResponse(
      String token
  ){}

}
