package com.projekt.app.controllers;

import com.projekt.app.dtos.AuthResponse;
import com.projekt.app.dtos.LoginRequest;
import com.projekt.app.entities.User;
import com.projekt.app.repositories.IUserRepository;
import com.projekt.app.services.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final IUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthController(IUserRepository userRepository, PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public AuthResponse login(@RequestBody LoginRequest request) {
    // User user = userRepository.findByUsername(request.username()).orElseThrow(
    //     () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Credentials"));
    User user = new User();
    user.setId(4444L);
    user.setUsername("Viktor");
    user.setPassword("pass");

    // if (!passwordEncoder.matches(request.password(), user.getPassword())) {
    //   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Credentials");
    // }

    if (!request.password().equals(user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Credentials");
    }

    return new AuthResponse(jwtService.generateToken(request.username()));
  }



}
